package com.spt.sptpicturebackend.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.mapper.PictureTaskMapper;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.model.entity.PictureTask;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskVO;
import com.spt.sptpicturebackend.service.OutboxEventService;
import com.spt.sptpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

/** Core-side command/query facade for search maintenance tasks. */
@Service
public class PictureSearchTaskService {
    private static final String TASK_TYPE = "INDEX_REBUILD";
    private static final String RECONCILE_TYPE = "INDEX_RECONCILE";
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    @Resource private PictureTaskMapper pictureTaskMapper;
    @Resource private UserService userService;
    @Resource private OutboxEventService outboxEventService;

    public Long submitRebuild(User user) {
        ensureNoActive(TASK_TYPE);
        PictureTask task = newTask(TASK_TYPE, user.getId()); pictureTaskMapper.insert(task);
        outboxEventService.createV1(PictureEventTypesV1.SEARCH_MAINTENANCE_REQUESTED,
                PictureRoutingV1.SEARCH_MAINTENANCE_REQUESTED, task.getId(), 1L, null,
                String.valueOf(task.getId()), null,
                new PicturePayloadsV1.SearchMaintenanceRequested(task.getId(), "REBUILD"));
        return task.getId();
    }

    public Long submitReconcile(User user) {
        ensureNoActive(RECONCILE_TYPE);
        PictureTask task = newTask(RECONCILE_TYPE, user.getId()); pictureTaskMapper.insert(task);
        outboxEventService.createV1(PictureEventTypesV1.SEARCH_MAINTENANCE_REQUESTED,
                PictureRoutingV1.SEARCH_MAINTENANCE_REQUESTED, task.getId(), 1L, null,
                String.valueOf(task.getId()), null,
                new PicturePayloadsV1.SearchMaintenanceRequested(task.getId(), "RECONCILE"));
        return task.getId();
    }

    public PictureTaskVO getTask(Long taskId, User user) {
        PictureTask task = pictureTaskMapper.selectById(taskId);
        if (task == null || (!TASK_TYPE.equals(task.getTaskType()) && !RECONCILE_TYPE.equals(task.getTaskType()))) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        if (!userService.isAdmin(user) && !task.getUserId().equals(user.getId())) throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        PictureTaskVO vo = new PictureTaskVO(); BeanUtils.copyProperties(task, vo); return vo;
    }

    private void ensureNoActive(String type) {
        if (pictureTaskMapper.selectCount(new QueryWrapper<PictureTask>().eq("taskType", type).in("status", PENDING, RUNNING)) > 0) throw new BusinessException(ErrorCode.OPERATION_ERROR, "已有索引任务正在执行");
    }
    private PictureTask newTask(String type, Long userId) {
        PictureTask task = new PictureTask(); task.setTaskType(type); task.setUserId(userId); task.setStatus(PENDING); task.setProgress(0); task.setSuccessCount(0); task.setFailedCount(0); task.setTotalCount(0); task.setCreateTime(new Date()); task.setUpdateTime(new Date()); return task;
    }
}
