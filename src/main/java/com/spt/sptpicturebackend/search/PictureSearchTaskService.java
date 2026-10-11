package com.spt.sptpicturebackend.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.mapper.PictureTaskMapper;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.messaging.PictureEventType;
import com.spt.sptpicturebackend.messaging.PictureMessageEnvelope;
import com.spt.sptpicturebackend.messaging.RabbitMqTopology;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.PictureTask;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskVO;
import com.spt.sptpicturebackend.service.PictureService;
import com.spt.sptpicturebackend.service.UserService;
import com.spt.sptpicturebackend.service.OutboxEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class PictureSearchTaskService {
    private static final String TASK_TYPE = "INDEX_REBUILD";
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";
    private static final String RECONCILE_TYPE = "INDEX_RECONCILE";

    @Resource
    private PictureTaskMapper pictureTaskMapper;
    @Resource
    private PictureMapper pictureMapper;
    @Resource
    private PictureService pictureService;
    @Resource
    private PictureSearchIndexService indexService;
    @Resource
    private UserService userService;
    @Resource
    private OutboxEventService outboxEventService;

    public Long submitRebuild(User user) {
        Long active = pictureTaskMapper.selectCount(new QueryWrapper<PictureTask>()
                .eq("taskType", TASK_TYPE).in("status", PENDING, RUNNING)) > 0 ? 1L : null;
        if (active != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已有索引重建任务正在执行");
        }
        PictureTask task = new PictureTask();
        task.setTaskType(TASK_TYPE);
        task.setUserId(user.getId());
        task.setStatus(PENDING);
        task.setProgress(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setTotalCount(0);
        task.setCreateTime(new Date());
        pictureTaskMapper.insert(task);
        outboxEventService.create(PictureEventType.INDEX_REBUILD_REQUESTED, RabbitMqTopology.INDEX_REBUILD_KEY,
                task.getId(), 1L, null, task.getId(), java.util.Collections.emptyMap());
        return task.getId();
    }

    public Long submitReconcile(User user) {
        return submitReconcile(user.getId());
    }

    public Long submitSystemReconcile() {
        return submitReconcile(0L);
    }

    private Long submitReconcile(Long userId) {
        Long active = pictureTaskMapper.selectCount(new QueryWrapper<PictureTask>()
                .eq("taskType", RECONCILE_TYPE).in("status", PENDING, RUNNING)) > 0 ? 1L : null;
        if (active != null) throw new BusinessException(ErrorCode.OPERATION_ERROR, "已有对账任务正在执行");
        PictureTask task = new PictureTask();
        task.setTaskType(RECONCILE_TYPE);
        task.setUserId(userId);
        task.setStatus(PENDING);
        task.setProgress(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setCreateTime(new Date());
        pictureTaskMapper.insert(task);
        outboxEventService.create(PictureEventType.INDEX_RECONCILE_REQUESTED, RabbitMqTopology.INDEX_RECONCILE_KEY,
                task.getId(), 1L, null, task.getId(), java.util.Collections.emptyMap());
        return task.getId();
    }

    public PictureTaskVO getTask(Long taskId, User user) {
        PictureTask task = pictureTaskMapper.selectById(taskId);
        if (task == null || (!TASK_TYPE.equals(task.getTaskType()) && !RECONCILE_TYPE.equals(task.getTaskType()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }
        if (!userService.isAdmin(user) && !task.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return toVO(task);
    }

    public void handleMaintenance(PictureMessageEnvelope envelope) {
        Long taskId = envelope.getTaskId();
        if (taskId == null) throw new IllegalArgumentException("索引维护事件缺少 taskId");
        PictureTask existing = pictureTaskMapper.selectById(taskId);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "索引任务不存在");
        if (SUCCEEDED.equals(existing.getStatus())) return;
        if (PictureEventType.INDEX_RECONCILE_REQUESTED.equals(envelope.getEventType())) {
            runReconcile(taskId);
        } else {
            runRebuild(taskId);
        }
    }

    private void runRebuild(Long taskId) {
        String index = null;
        try {
            updateStatus(taskId, RUNNING, new Date(), null);
            index = indexService.createRebuildIndex();
            long total = pictureService.count(new QueryWrapper<Picture>());
            long lastId = 0L;
            int success = 0;
            while (true) {
                List<Picture> batch = pictureService.lambdaQuery()
                        .gt(Picture::getId, lastId)
                        .orderByAsc(Picture::getId)
                        .last("limit 500")
                        .list();
                if (batch.isEmpty()) break;
                indexService.bulkIndex(index, batch);
                success += batch.size();
                lastId = batch.get(batch.size() - 1).getId();
                int progress = total == 0 ? 100 : (int) Math.min(99, success * 100L / total);
                updateProgress(taskId, progress, success);
            }
            indexService.finishRebuild(index);
            PictureTask task = new PictureTask();
            task.setId(taskId);
            task.setStatus(SUCCEEDED);
            task.setProgress(100);
            task.setSuccessCount(success);
            task.setEndTime(new Date());
            pictureTaskMapper.updateById(task);
        } catch (Exception e) {
            log.error("picture search index rebuild failed, taskId={}", taskId, e);
            if (index != null) indexService.cancelRebuild(index);
            markFailed(taskId, e.getMessage());
        }
    }

    private void runReconcile(Long taskId) {
        try {
            updateStatus(taskId, RUNNING, new Date(), null);
            Date since = new Date(System.currentTimeMillis() - 26L * 60L * 60L * 1000L);
            List<Picture> changed = pictureMapper.selectChangedSince(since);
            int total = changed.size();
            int success = 0;
            for (Picture picture : changed) {
                if (Integer.valueOf(1).equals(picture.getIsDelete())) {
                    indexService.deletePicture(picture);
                } else {
                    indexService.indexPicture(picture);
                }
                success++;
                updateProgress(taskId, total == 0 ? 100 : success * 100 / total, success);
            }
            PictureTask done = new PictureTask();
            done.setId(taskId);
            done.setStatus(SUCCEEDED);
            done.setProgress(100);
            done.setTotalCount(total);
            done.setSuccessCount(success);
            done.setEndTime(new Date());
            pictureTaskMapper.updateById(done);
        } catch (Exception e) {
            markFailed(taskId, e.getMessage());
        }
    }

    private void updateStatus(Long taskId, String status, Date startTime, String error) {
        PictureTask task = new PictureTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setStartTime(startTime);
        task.setErrorMessage(error);
        pictureTaskMapper.updateById(task);
    }

    private void updateProgress(Long taskId, int progress, int success) {
        PictureTask task = new PictureTask();
        task.setId(taskId);
        task.setProgress(progress);
        task.setSuccessCount(success);
        pictureTaskMapper.updateById(task);
    }

    private void markFailed(Long taskId, String message) {
        PictureTask task = new PictureTask();
        task.setId(taskId);
        task.setStatus(FAILED);
        task.setErrorMessage(message == null ? "索引重建失败" : message.substring(0, Math.min(message.length(), 1000)));
        task.setEndTime(new Date());
        pictureTaskMapper.updateById(task);
    }

    private PictureTaskVO toVO(PictureTask task) {
        PictureTaskVO vo = new PictureTaskVO();
        vo.setId(task.getId());
        vo.setTaskType(task.getTaskType());
        vo.setStatus(task.getStatus());
        vo.setProgress(task.getProgress());
        vo.setSuccessCount(task.getSuccessCount());
        vo.setFailedCount(task.getFailedCount());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        return vo;
    }
}
