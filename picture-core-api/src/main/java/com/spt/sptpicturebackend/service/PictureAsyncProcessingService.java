package com.spt.sptpicturebackend.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.spt.sptpicturebackend.config.PictureAsyncProperties;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.mapper.PictureTaskItemMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskMapper;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.model.dto.picture.PictureBatchTaskRequest;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.PictureTask;
import com.spt.sptpicturebackend.model.entity.PictureTaskItem;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskItemVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Core-owned task command facade. Media workers execute the messages; Core
 * only creates task rows and publishes commands, preserving the public API.
 */
@Service
public class PictureAsyncProcessingService {
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String FAILED = "FAILED";
    private static final String ITEM_PENDING = "PENDING";

    @Resource private PictureService pictureService;
    @Resource private UserService userService;
    @Resource private SpaceService spaceService;
    @Resource private PictureAsyncProperties asyncProperties;
    @Resource private PictureTaskMapper pictureTaskMapper;
    @Resource private PictureTaskItemMapper pictureTaskItemMapper;
    @Resource private OutboxEventService outboxEventService;
    @Resource private TransactionTemplate transactionTemplate;

    public PictureTaskVO submitBatch(PictureBatchTaskRequest request, User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getSearchText()), ErrorCode.PARAMS_ERROR, "请输入搜索关键词");
        int count = request.getCount() == null ? 10 : request.getCount();
        ThrowUtils.throwIf(count < 1 || count > 30, ErrorCode.PARAMS_ERROR, "批量导入数量必须在1到30之间");
        ThrowUtils.throwIf(!asyncProperties.isEnabled() || !asyncProperties.isBatchEnabled(), ErrorCode.OPERATION_ERROR, "异步批量导入暂未开启");
        if (request.getSpaceId() != null) {
            ThrowUtils.throwIf(spaceService.getById(request.getSpaceId()) == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        PictureTask task = transactionTemplate.execute(status -> {
            PictureTask value = new PictureTask();
            value.setTaskType("BATCH_IMPORT");
            value.setUserId(user.getId());
            value.setSpaceId(request.getSpaceId());
            value.setStatus(PENDING);
            value.setProgress(0);
            value.setTotalCount(count);
            value.setSuccessCount(0);
            value.setFailedCount(0);
            value.setRetryCount(0);
            value.setRequestParams(JSONUtil.toJsonStr(request));
            value.setCreateTime(new Date());
            value.setUpdateTime(new Date());
            pictureTaskMapper.insert(value);
            outboxEventService.createV1(PictureEventTypesV1.BATCH_IMPORT_REQUESTED, PictureRoutingV1.MEDIA_BATCH_REQUESTED,
                    value.getId(), 1L, null, String.valueOf(value.getId()), null,
                    new PicturePayloadsV1.BatchImportRequested(value.getId(), user.getId(), request.getSpaceId(),
                            request.getSearchText(), count, request.getNamePrefix(), List.of()));
            return value;
        });
        return toVO(task, Collections.emptyList());
    }

    public PictureTaskVO getTask(Long taskId, User user) {
        PictureTask task = pictureTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        ThrowUtils.throwIf(!userService.isAdmin(user) && !Objects.equals(task.getUserId(), user.getId()), ErrorCode.NO_AUTH_ERROR);
        List<PictureTaskItem> items = pictureTaskItemMapper.selectList(new QueryWrapper<PictureTaskItem>()
                .eq("taskId", taskId).orderByAsc("sequenceNo"));
        return toVO(task, items);
    }

    public void retry(Long taskId, User user) {
        PictureTask task = pictureTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        ThrowUtils.throwIf(!userService.isAdmin(user) && !Objects.equals(task.getUserId(), user.getId()), ErrorCode.NO_AUTH_ERROR);
        if ("BATCH_IMPORT".equals(task.getTaskType())) {
            retryBatch(task);
            return;
        }
        if ("PICTURE_PROCESS".equals(task.getTaskType())) {
            retryProcess(task);
            return;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "该任务类型不支持重试");
    }

    private void retryBatch(PictureTask task) {
        transactionTemplate.execute(status -> {
            List<PictureTaskItem> failed = pictureTaskItemMapper.selectList(new QueryWrapper<PictureTaskItem>()
                    .eq("taskId", task.getId()).eq("status", "FAILED"));
            ThrowUtils.throwIf(failed.isEmpty(), ErrorCode.OPERATION_ERROR, "没有可重试的失败项");
            PictureBatchTaskRequest batch = JSONUtil.toBean(task.getRequestParams(), PictureBatchTaskRequest.class);
            List<PicturePayloadsV1.RetryItem> retryItems = failed.stream()
                    .map(item -> new PicturePayloadsV1.RetryItem(item.getId(), item.getSequenceNo(), item.getSourceUrl()))
                    .collect(Collectors.toList());
            failed.forEach(item -> {
                item.setStatus(ITEM_PENDING);
                item.setErrorMessage(null);
                pictureTaskItemMapper.updateById(item);
            });
            PictureTask update = new PictureTask();
            update.setId(task.getId());
            update.setStatus(RUNNING);
            update.setEndTime(null);
            update.setErrorMessage(null);
            pictureTaskMapper.updateById(update);
            outboxEventService.createV1(PictureEventTypesV1.BATCH_IMPORT_REQUESTED, PictureRoutingV1.MEDIA_BATCH_REQUESTED,
                    task.getId(), 1L, null, String.valueOf(task.getId()), null,
                    new PicturePayloadsV1.BatchImportRequested(task.getId(), task.getUserId(), task.getSpaceId(),
                            batch.getSearchText(), retryItems.size(), batch.getNamePrefix(), retryItems));
            recalculateTask(task.getId());
            return null;
        });
    }

    private void retryProcess(PictureTask task) {
        ThrowUtils.throwIf(!FAILED.equals(task.getStatus()), ErrorCode.OPERATION_ERROR, "仅失败任务可重试");
        transactionTemplate.execute(status -> {
            Picture picture = pictureService.getById(task.getPictureId());
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            PictureTask update = new PictureTask();
            update.setId(task.getId());
            update.setStatus(PENDING);
            update.setProgress(0);
            update.setErrorMessage(null);
            update.setEndTime(null);
            update.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
            pictureTaskMapper.updateById(update);
            Picture updatePicture = new Picture();
            updatePicture.setId(picture.getId());
            updatePicture.setProcessStatus(0);
            updatePicture.setProcessMessage(null);
            updatePicture.setVersion((picture.getVersion() == null ? 1L : picture.getVersion()) + 1);
            pictureService.updateById(updatePicture);
            outboxEventService.createPictureChanged(picture.getId(), updatePicture.getVersion());
            outboxEventService.createPictureProcessingRequestedV1(picture.getId(), task.getId(), updatePicture.getVersion(), picture.getOriginalObjectKey());
            return null;
        });
    }

    private void recalculateTask(Long taskId) {
        List<PictureTaskItem> items = pictureTaskItemMapper.selectList(new QueryWrapper<PictureTaskItem>().eq("taskId", taskId));
        int success = (int) items.stream().filter(i -> "SUCCEEDED".equals(i.getStatus())).count();
        int failed = (int) items.stream().filter(i -> "FAILED".equals(i.getStatus())).count();
        PictureTask update = new PictureTask();
        update.setId(taskId);
        update.setSuccessCount(success);
        update.setFailedCount(failed);
        update.setTotalCount(items.size());
        update.setProgress(items.isEmpty() ? 0 : (success + failed) * 100 / items.size());
        pictureTaskMapper.updateById(update);
    }

    private PictureTaskVO toVO(PictureTask task, List<PictureTaskItem> items) {
        PictureTaskVO vo = new PictureTaskVO();
        BeanUtils.copyProperties(task, vo);
        vo.setItems(items.stream().map(item -> {
            PictureTaskItemVO value = new PictureTaskItemVO();
            BeanUtils.copyProperties(item, value);
            return value;
        }).collect(Collectors.toList()));
        return vo;
    }
}
