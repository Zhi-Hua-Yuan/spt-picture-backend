package com.spt.sptpicturebackend.service;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingResponse;
import com.spt.sptpicturebackend.manager.CosManager;
import com.spt.sptpicturebackend.mapper.MqConsumeLogMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskItemMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskMapper;
import com.spt.sptpicturebackend.messaging.PictureEventType;
import com.spt.sptpicturebackend.messaging.PictureMessageEnvelope;
import com.spt.sptpicturebackend.messaging.RabbitMqTopology;
import com.spt.sptpicturebackend.model.dto.picture.PictureBatchTaskRequest;
import com.spt.sptpicturebackend.model.dto.picture.PictureUploadRequest;
import com.spt.sptpicturebackend.model.entity.MqConsumeLog;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.PictureTask;
import com.spt.sptpicturebackend.model.entity.PictureTaskItem;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.model.enums.PictureReviewStatusEnum;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskItemVO;
import com.spt.sptpicturebackend.model.vo.picture.PictureTaskVO;
import com.spt.sptpicturebackend.exception.BusinessException;
import com.spt.sptpicturebackend.exception.ErrorCode;
import com.spt.sptpicturebackend.exception.ThrowUtils;
import com.spt.sptpicturebackend.config.PictureAsyncProperties;
import org.springframework.dao.DuplicateKeyException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PictureAsyncProcessingService {
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String PARTIAL_FAILED = "PARTIAL_FAILED";
    private static final String FAILED = "FAILED";
    private static final String CANCELLED = "CANCELLED";
    private static final String ITEM_PENDING = "PENDING";
    private static final String ITEM_RUNNING = "RUNNING";
    private static final String ITEM_SUCCEEDED = "SUCCEEDED";
    private static final String ITEM_FAILED = "FAILED";
    private static final String CONSUMER_PROCESS = "picture-process";
    private static final String CONSUMER_BATCH = "picture-batch";
    private static final String CONSUMER_BATCH_ITEM = "picture-batch-item";
    private static final String CONSUMER_FILE_DELETE = "picture-file-delete";

    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private PictureAsyncProperties asyncProperties;
    @Resource
    private PictureTaskMapper pictureTaskMapper;
    @Resource
    private PictureTaskItemMapper pictureTaskItemMapper;
    @Resource
    private MqConsumeLogMapper mqConsumeLogMapper;
    @Resource
    private OutboxEventService outboxEventService;
    @Resource
    private CosManager cosManager;
    @Resource
    private TransactionTemplate transactionTemplate;

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
            outboxEventService.create(PictureEventType.BATCH_IMPORT_REQUESTED, RabbitMqTopology.BATCH_KEY,
                    value.getId(), 1L, null, value.getId(), Collections.emptyMap());
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
                    .eq("taskId", task.getId()).eq("status", ITEM_FAILED));
            ThrowUtils.throwIf(failed.isEmpty(), ErrorCode.OPERATION_ERROR, "没有可重试的失败项");
            failed.forEach(item -> {
                item.setStatus(ITEM_PENDING);
                item.setErrorMessage(null);
                pictureTaskItemMapper.updateById(item);
                outboxEventService.create(PictureEventType.BATCH_IMPORT_ITEM_REQUESTED, RabbitMqTopology.BATCH_ITEM_KEY,
                        item.getId(), 1L, item.getPictureId(), task.getId(), Map.of("taskItemId", item.getId()));
            });
            PictureTask update = new PictureTask();
            update.setId(task.getId());
            update.setStatus(RUNNING);
            update.setEndTime(null);
            update.setErrorMessage(null);
            pictureTaskMapper.updateById(update);
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
            outboxEventService.create(PictureEventType.PROCESS_REQUESTED, RabbitMqTopology.PROCESS_KEY,
                    picture.getId(), updatePicture.getVersion(), picture.getId(), task.getId(), Collections.emptyMap());
            return null;
        });
    }

    public void handleProcess(PictureMessageEnvelope envelope) {
        if (alreadyConsumed(CONSUMER_PROCESS, envelope.getEventId())) return;
        Picture picture = pictureService.getById(envelope.getPictureId());
        PictureTask task = pictureTaskMapper.selectById(envelope.getTaskId());
        if (picture == null || task == null) {
            throw new IllegalStateException("图片处理任务不存在");
        }
        if (!Objects.equals(picture.getProcessTaskId(), task.getId())) {
            markCancelled(task.getId(), "图片已被新的处理任务替换");
            markConsumed(CONSUMER_PROCESS, envelope.getEventId());
            return;
        }
        if (SUCCEEDED.equals(task.getStatus())) return;
        if (envelope.getPictureVersion() != null && picture.getVersion() != null
                && !Objects.equals(envelope.getPictureVersion(), picture.getVersion())) {
            markCancelled(task.getId(), "图片版本已变化");
            markConsumed(CONSUMER_PROCESS, envelope.getEventId());
            return;
        }
        markProcessing(task.getId(), picture.getId());
        ImageAuditingResponse response = cosManager.reviewObject(
                StrUtil.isBlank(picture.getOriginalObjectKey()) ? picture.getUrl() : picture.getOriginalObjectKey());
        ThrowUtils.throwIf(response == null || response.getResult() == null, ErrorCode.SYSTEM_ERROR, "内容审核无响应");
        PictureReviewStatusEnum reviewStatus;
        String reviewMessage;
        switch (response.getResult()) {
            case "0" -> { reviewStatus = PictureReviewStatusEnum.PASS; reviewMessage = "经 AI 审核通过"; }
            case "1" -> { reviewStatus = PictureReviewStatusEnum.REJECT; reviewMessage = "判定为违规敏感文件"; }
            case "2" -> { reviewStatus = PictureReviewStatusEnum.REVIEWING; reviewMessage = "疑似敏感，建议人工复核"; }
            default -> throw new IllegalStateException("未知审核结果");
        }
        transactionTemplate.execute(status -> {
            Picture latest = pictureService.getById(picture.getId());
            if (latest == null || !Objects.equals(latest.getProcessTaskId(), task.getId())) return null;
            Picture updatePicture = new Picture();
            updatePicture.setId(latest.getId());
            updatePicture.setReviewStatus(reviewStatus.getValue());
            updatePicture.setReviewMessage(reviewMessage);
            updatePicture.setProcessStatus(2);
            updatePicture.setProcessMessage(null);
            updatePicture.setVersion((latest.getVersion() == null ? 1L : latest.getVersion()) + 1);
            pictureService.updateById(updatePicture);
            outboxEventService.createPictureChanged(latest.getId(), updatePicture.getVersion());
            PictureTask updateTask = new PictureTask();
            updateTask.setId(task.getId());
            updateTask.setStatus(SUCCEEDED);
            updateTask.setProgress(100);
            updateTask.setTotalCount(1);
            updateTask.setSuccessCount(1);
            updateTask.setFailedCount(0);
            updateTask.setEndTime(new Date());
            pictureTaskMapper.updateById(updateTask);
            markConsumed(CONSUMER_PROCESS, envelope.getEventId());
            return null;
        });
    }

    private void markProcessing(Long taskId, Long pictureId) {
        transactionTemplate.execute(status -> {
            PictureTask task = new PictureTask();
            task.setId(taskId);
            task.setStatus(RUNNING);
            task.setStartTime(new Date());
            pictureTaskMapper.updateById(task);
            Picture current = pictureService.getById(pictureId);
            if (current != null) {
                Picture picture = new Picture();
                picture.setId(pictureId);
                picture.setProcessStatus(1);
                picture.setProcessMessage(null);
                picture.setVersion((current.getVersion() == null ? 1L : current.getVersion()) + 1);
                pictureService.updateById(picture);
                outboxEventService.createPictureChanged(pictureId, picture.getVersion());
            }
            return null;
        });
    }

    public void handleBatchImport(PictureMessageEnvelope envelope) {
        if (alreadyConsumed(CONSUMER_BATCH, envelope.getEventId())) return;
        PictureTask task = pictureTaskMapper.selectById(envelope.getTaskId());
        if (task == null) throw new IllegalStateException("批量任务不存在");
        long existing = pictureTaskItemMapper.selectCount(new QueryWrapper<PictureTaskItem>().eq("taskId", task.getId()));
        if (existing > 0) return;
        PictureBatchTaskRequest request = JSONUtil.toBean(task.getRequestParams(), PictureBatchTaskRequest.class);
        Document document;
        try {
            document = Jsoup.connect("https://cn.bing.com/images/async?q=" +
                    java.net.URLEncoder.encode(request.getSearchText(), java.nio.charset.StandardCharsets.UTF_8) + "&mmasync=1")
                    .timeout(10000).get();
        } catch (IOException e) {
            throw new IllegalStateException("获取候选图片失败", e);
        }
        List<String> urls = document.getElementsByClass("dgControl").stream()
                .flatMap(div -> div.select("img.mimg").stream())
                .map(element -> element.attr("src"))
                .filter(StrUtil::isNotBlank)
                .map(url -> url.contains("?") ? url.substring(0, url.indexOf('?')) : url)
                .limit(request.getCount() == null ? 10 : request.getCount())
                .collect(Collectors.toList());
        transactionTemplate.execute(status -> {
            if (urls.isEmpty()) {
                markTaskFailed(task.getId(), "没有抓取到可用图片");
                markConsumed(CONSUMER_BATCH, envelope.getEventId());
                return null;
            }
            int sequence = 1;
            for (String url : urls) {
                PictureTaskItem item = new PictureTaskItem();
                item.setTaskId(task.getId());
                item.setSequenceNo(sequence++);
                item.setSourceUrl(url);
                item.setStatus(ITEM_PENDING);
                item.setAttemptCount(0);
                pictureTaskItemMapper.insert(item);
                outboxEventService.create(PictureEventType.BATCH_IMPORT_ITEM_REQUESTED, RabbitMqTopology.BATCH_ITEM_KEY,
                        item.getId(), 1L, null, task.getId(), Map.of("taskItemId", item.getId()));
            }
            PictureTask update = new PictureTask();
            update.setId(task.getId());
            update.setStatus(RUNNING);
            update.setTotalCount(urls.size());
            update.setStartTime(new Date());
            pictureTaskMapper.updateById(update);
            markConsumed(CONSUMER_BATCH, envelope.getEventId());
            return null;
        });
    }

    public void handleBatchItem(PictureMessageEnvelope envelope) {
        if (alreadyConsumed(CONSUMER_BATCH_ITEM, envelope.getEventId())) return;
        Long itemId = envelope.getData() == null ? envelope.getPictureId() : number(envelope.getData().get("taskItemId"));
        PictureTaskItem item = pictureTaskItemMapper.selectById(itemId);
        if (item == null) throw new IllegalStateException("批量任务项不存在");
        if (ITEM_SUCCEEDED.equals(item.getStatus())) return;
        Picture existing = pictureService.lambdaQuery().eq(Picture::getTaskItemId, item.getId()).one();
        if (existing != null) {
            completeItem(item, existing.getId(), null, envelope.getEventId());
            return;
        }
        transactionTemplate.execute(status -> {
            PictureTaskItem update = new PictureTaskItem();
            update.setId(item.getId());
            update.setStatus(ITEM_RUNNING);
            update.setAttemptCount((item.getAttemptCount() == null ? 0 : item.getAttemptCount()) + 1);
            pictureTaskItemMapper.updateById(update);
            return null;
        });
        PictureTask task = pictureTaskMapper.selectById(item.getTaskId());
        User user = userService.getById(task.getUserId());
        PictureBatchTaskRequest batch = JSONUtil.toBean(task.getRequestParams(), PictureBatchTaskRequest.class);
        PictureUploadRequest upload = new PictureUploadRequest();
        upload.setFileUrl(item.getSourceUrl());
        upload.setSpaceId(batch.getSpaceId());
        upload.setTaskItemId(item.getId());
        if (StrUtil.isNotBlank(batch.getNamePrefix())) {
            upload.setPicName(batch.getNamePrefix() + item.getSequenceNo());
        }
        PictureVOHolder result = new PictureVOHolder(pictureService.uploadPicture(item.getSourceUrl(), upload, user).getId());
        completeItem(item, result.pictureId, null, envelope.getEventId());
    }

    private void completeItem(PictureTaskItem item, Long pictureId, String error, String eventId) {
        transactionTemplate.execute(status -> {
            PictureTaskItem update = new PictureTaskItem();
            update.setId(item.getId());
            update.setPictureId(pictureId);
            update.setStatus(error == null ? ITEM_SUCCEEDED : ITEM_FAILED);
            update.setErrorMessage(error);
            pictureTaskItemMapper.updateById(update);
            recalculateTask(item.getTaskId());
            markConsumed(CONSUMER_BATCH_ITEM, eventId);
            return null;
        });
    }

    public void handleFileDelete(PictureMessageEnvelope envelope) {
        if (alreadyConsumed(CONSUMER_FILE_DELETE, envelope.getEventId())) return;
        Object value = envelope.getData() == null ? null : envelope.getData().get("keys");
        if (value instanceof Collection<?> collection) {
            for (Object key : collection) {
                if (key != null && StrUtil.isNotBlank(key.toString())) cosManager.deleteObject(key.toString());
            }
        }
        transactionTemplate.execute(status -> { markConsumed(CONSUMER_FILE_DELETE, envelope.getEventId()); return null; });
    }

    public void markFinalFailure(PictureMessageEnvelope envelope, String message) {
        String error = message == null ? "异步处理失败" : message.substring(0, Math.min(1800, message.length()));
        transactionTemplate.execute(status -> {
            switch (envelope.getEventType()) {
                case PictureEventType.PROCESS_REQUESTED -> {
                    Picture picture = pictureService.getById(envelope.getPictureId());
                    if (picture != null && Objects.equals(picture.getProcessTaskId(), envelope.getTaskId())) {
                        Picture update = new Picture();
                        update.setId(picture.getId()); update.setProcessStatus(3); update.setProcessMessage(error);
                        update.setVersion((picture.getVersion() == null ? 1L : picture.getVersion()) + 1);
                        pictureService.updateById(update);
                        outboxEventService.createPictureChanged(picture.getId(), update.getVersion());
                    }
                    markTaskFailed(envelope.getTaskId(), error);
                    markConsumed(CONSUMER_PROCESS, envelope.getEventId());
                }
                case PictureEventType.BATCH_IMPORT_REQUESTED -> { markTaskFailed(envelope.getTaskId(), error); markConsumed(CONSUMER_BATCH, envelope.getEventId()); }
                case PictureEventType.BATCH_IMPORT_ITEM_REQUESTED -> {
                    Long itemId = number(envelope.getData() == null ? null : envelope.getData().get("taskItemId"));
                    PictureTaskItem item = pictureTaskItemMapper.selectById(itemId);
                    if (item != null) { PictureTaskItem update = new PictureTaskItem(); update.setId(itemId); update.setStatus(ITEM_FAILED); update.setErrorMessage(error); pictureTaskItemMapper.updateById(update); recalculateTask(item.getTaskId()); }
                    markConsumed(CONSUMER_BATCH_ITEM, envelope.getEventId());
                }
                default -> markConsumed(CONSUMER_FILE_DELETE, envelope.getEventId());
            }
            return null;
        });
    }

    private void markTaskFailed(Long taskId, String message) {
        PictureTask update = new PictureTask();
        update.setId(taskId); update.setStatus(FAILED); update.setProgress(100); update.setFailedCount(1);
        update.setErrorMessage(message); update.setEndTime(new Date()); pictureTaskMapper.updateById(update);
    }

    private void recalculateTask(Long taskId) {
        List<PictureTaskItem> items = pictureTaskItemMapper.selectList(new QueryWrapper<PictureTaskItem>().eq("taskId", taskId));
        int success = (int) items.stream().filter(i -> ITEM_SUCCEEDED.equals(i.getStatus())).count();
        int failed = (int) items.stream().filter(i -> ITEM_FAILED.equals(i.getStatus())).count();
        int finished = success + failed;
        PictureTask update = new PictureTask(); update.setId(taskId); update.setSuccessCount(success); update.setFailedCount(failed);
        update.setTotalCount(items.size()); update.setProgress(items.isEmpty() ? 0 : finished * 100 / items.size());
        if (finished == items.size() && !items.isEmpty()) { update.setStatus(failed == 0 ? SUCCEEDED : (success == 0 ? FAILED : PARTIAL_FAILED)); update.setEndTime(new Date()); }
        pictureTaskMapper.updateById(update);
    }

    private void markProcessing(Long taskId, Long pictureId, boolean unused) { markProcessing(taskId, pictureId); }

    private void markCancelled(Long taskId, String message) {
        PictureTask update = new PictureTask(); update.setId(taskId); update.setStatus(CANCELLED); update.setErrorMessage(message); update.setEndTime(new Date()); pictureTaskMapper.updateById(update);
    }

    private boolean alreadyConsumed(String group, String eventId) {
        return eventId != null && mqConsumeLogMapper.selectCount(new QueryWrapper<MqConsumeLog>().eq("consumerGroup", group).eq("eventId", eventId)) > 0;
    }

    private void markConsumed(String group, String eventId) {
        if (eventId == null) return;
        MqConsumeLog log = new MqConsumeLog(); log.setConsumerGroup(group); log.setEventId(eventId); log.setConsumedTime(new Date());
        try { mqConsumeLogMapper.insert(log); } catch (DuplicateKeyException ignored) { } catch (RuntimeException error) { throw error; }
    }

    private Long number(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }

    private PictureTaskVO toVO(PictureTask task, List<PictureTaskItem> items) {
        PictureTaskVO vo = new PictureTaskVO();
        BeanUtils.copyProperties(task, vo);
        vo.setItems(items.stream().map(item -> { PictureTaskItemVO value = new PictureTaskItemVO(); BeanUtils.copyProperties(item, value); return value; }).collect(Collectors.toList()));
        return vo;
    }

    private static final class PictureVOHolder {
        private final Long pictureId;
        private PictureVOHolder(Long pictureId) { this.pictureId = pictureId; }
    }
}
