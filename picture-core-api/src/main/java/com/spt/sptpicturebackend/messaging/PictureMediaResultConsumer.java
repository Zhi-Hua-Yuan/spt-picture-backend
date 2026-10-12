package com.spt.sptpicturebackend.messaging;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskMapper;
import com.spt.sptpicturebackend.mapper.PictureTaskItemMapper;
import com.spt.sptpicturebackend.mapper.SpaceMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.model.entity.PictureTask;
import com.spt.sptpicturebackend.model.entity.PictureTaskItem;
import com.spt.sptpicturebackend.model.entity.Space;
import com.spt.sptpicturebackend.model.enums.PictureReviewStatusEnum;
import com.spt.sptpicturebackend.service.InboxService;
import com.spt.sptpicturebackend.service.OutboxEventService;
import com.spt.sptpicturebackend.messaging.ReliableRabbitPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.List;

/** Applies Media results to Core-owned projections and emits the next search snapshot. */
@Slf4j
@Component
public class PictureMediaResultConsumer {
    private static final String GROUP = "picture-core-media-result";
    @Resource private ObjectMapper objectMapper;
    @Resource private PictureMapper pictureMapper;
    @Resource private PictureTaskMapper pictureTaskMapper;
    @Resource private PictureTaskItemMapper pictureTaskItemMapper;
    @Resource private SpaceMapper spaceMapper;
    @Resource private InboxService inboxService;
    @Resource private OutboxEventService outboxEventService;
    @Resource private ReliableRabbitPublisher publisher;
    @Resource private TransactionTemplate transactionTemplate;

    @RabbitListener(queues = PictureRoutingV1.CORE_MEDIA_RESULT_QUEUE,
            concurrency = "${picture.async.media-result-concurrency:2}",
            autoStartup = "${picture.async.media-result-enabled:true}")
    public void consume(Message message, Channel channel) throws Exception {
        EventEnvelopeV1<JsonNode> raw;
        try {
            raw = objectMapper.readValue(message.getBody(), new TypeReference<>() { });
            if (raw.schemaVersion() != EventEnvelopeV1.VERSION || raw.eventId() == null) throw new IllegalArgumentException("invalid media result");
        } catch (Exception invalid) {
            try {
                publisher.parkV1(message.getBody(), PictureRoutingV1.CORE_MEDIA_RESULT_QUEUE, invalid.getMessage());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (Exception failed) {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
            return;
        }
        if (inboxService.isSucceeded(GROUP, raw.eventId()) || !inboxService.claim(GROUP, raw.eventId())) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        try {
            if (PictureEventTypesV1.PICTURE_PROCESSING_SUCCEEDED.equals(raw.eventType())) {
                applySuccess(objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.PictureProcessingSucceeded.class));
            } else if (PictureEventTypesV1.PICTURE_PROCESSING_FAILED.equals(raw.eventType())) {
                applyFailure(objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.PictureProcessingFailed.class));
            } else if (PictureEventTypesV1.BATCH_ITEM_PREPARED.equals(raw.eventType())) {
                applyBatchItem(objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.BatchItemPrepared.class));
            } else if (PictureEventTypesV1.BATCH_COMPLETED.equals(raw.eventType())) {
                applyBatchCompleted(objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.BatchCompleted.class));
            } else {
                throw new IllegalArgumentException("unknown media result event: " + raw.eventType());
            }
            inboxService.markSucceeded(GROUP, raw.eventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed(GROUP, raw.eventId(), error.getMessage());
            retryOrDeadLetter(message, channel, PictureRoutingV1.CORE_MEDIA_RESULT_RETRY,
                    PictureRoutingV1.CORE_MEDIA_RESULT_DLQ, PictureRoutingV1.CORE_MEDIA_RESULT_QUEUE,
                    error.getMessage());
        }
    }

    private void retryOrDeadLetter(Message message, Channel channel, String retryKey, String dlqKey,
                                   String sourceQueue, String reason) throws Exception {
        try {
            int current = retryCount(message);
            if (current >= 3) {
                publisher.deadLetterV1(message, dlqKey, sourceQueue, reason);
            } else {
                publisher.retryV1(message, retryKeyFor(retryKey, current + 1), current + 1, sourceQueue);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception failed) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get("x-picture-retry-count");
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String retryKeyFor(String base, int attempt) {
        return attempt == 1 ? base : base + (attempt == 2 ? ".60" : ".300");
    }

    private void applySuccess(PicturePayloadsV1.PictureProcessingSucceeded result) {
        transactionTemplate.execute(status -> {
            Picture current = pictureMapper.selectByIdIncludingDeleted(result.pictureId());
            if (!accepts(current, result.taskId(), result.expectedVersion())) return null;
            Integer reviewStatus = switch (result.reviewStatus() == null ? "REVIEWING" : result.reviewStatus()) {
                case "PASS" -> PictureReviewStatusEnum.PASS.getValue();
                case "REJECT" -> PictureReviewStatusEnum.REJECT.getValue();
                default -> PictureReviewStatusEnum.REVIEWING.getValue();
            };
            Picture update = new Picture();
            update.setReviewStatus(reviewStatus);
            update.setReviewMessage(result.reviewMessage());
            update.setProcessStatus(2);
            update.setProcessMessage(null);
            update.setVersion(versionOf(result.expectedVersion()) + 1);
            if (result.displayObjectKey() != null) update.setDisplayObjectKey(result.displayObjectKey());
            if (result.thumbnailObjectKey() != null) update.setThumbnailObjectKey(result.thumbnailObjectKey());
            if (result.displayUrl() != null) update.setUrl(result.displayUrl());
            if (result.thumbnailUrl() != null) update.setThumbnailUrl(result.thumbnailUrl());
            if (result.processedSize() != null) update.setPicSize(result.processedSize());
            int changed = pictureMapper.update(update, new UpdateWrapper<Picture>().eq("id", result.pictureId())
                    .eq("version", versionOf(result.expectedVersion())).eq("processTaskId", result.taskId()));
            if (changed == 1) {
                Picture latest = pictureMapper.selectByIdIncludingDeleted(result.pictureId());
                markTaskSucceeded(result.taskId());
                publishSearch(latest);
            }
            return null;
        });
    }

    private void applyFailure(PicturePayloadsV1.PictureProcessingFailed result) {
        transactionTemplate.execute(status -> {
            Picture current = pictureMapper.selectByIdIncludingDeleted(result.pictureId());
            if (!accepts(current, result.taskId(), result.expectedVersion())) return null;
            Picture update = new Picture();
            update.setProcessStatus(3);
            update.setProcessMessage(result.errorMessage());
            update.setVersion(versionOf(result.expectedVersion()) + 1);
            int changed = pictureMapper.update(update, new UpdateWrapper<Picture>().eq("id", result.pictureId())
                    .eq("version", versionOf(result.expectedVersion())).eq("processTaskId", result.taskId()));
            if (changed == 1) {
                Picture latest = pictureMapper.selectByIdIncludingDeleted(result.pictureId());
                PictureTask task = new PictureTask(); task.setId(result.taskId()); task.setStatus("FAILED"); task.setProgress(100); task.setFailedCount(1); task.setErrorMessage(result.errorMessage()); task.setEndTime(new java.util.Date()); pictureTaskMapper.updateById(task);
                publishSearch(latest);
            }
            return null;
        });
    }

    private void markTaskSucceeded(Long taskId) {
        PictureTask task = new PictureTask(); task.setId(taskId); task.setStatus("SUCCEEDED"); task.setProgress(100); task.setSuccessCount(1); task.setFailedCount(0); task.setEndTime(new Date()); pictureTaskMapper.updateById(task);
    }

    private void applyBatchItem(PicturePayloadsV1.BatchItemPrepared result) {
        transactionTemplate.execute(status -> {
            PictureTask task = pictureTaskMapper.selectById(result.taskId());
            if (task == null) throw new IllegalStateException("批量任务不存在: " + result.taskId());
            PictureTaskItem item = pictureTaskItemMapper.selectOne(new QueryWrapper<PictureTaskItem>()
                    .eq("taskId", result.taskId()).eq("sequenceNo", result.sequenceNo()));
            if (item != null && "SUCCEEDED".equals(item.getStatus())) return null;
            if (item == null) {
                item = new PictureTaskItem();
                item.setTaskId(result.taskId());
                item.setSequenceNo(result.sequenceNo());
                item.setCreateTime(new Date());
                item.setAttemptCount(0);
                pictureTaskItemMapper.insert(item);
            }
            if (!result.success()) {
                PictureTaskItem failed = new PictureTaskItem();
                failed.setId(item.getId());
                failed.setSourceUrl(result.sourceUrl());
                failed.setStatus("FAILED");
                failed.setErrorMessage(result.errorMessage());
                failed.setAttemptCount((item.getAttemptCount() == null ? 0 : item.getAttemptCount()) + 1);
                pictureTaskItemMapper.updateById(failed);
                recalculateBatchTask(result.taskId());
                return null;
            }
            long size = result.picSize() == null ? 0L : result.picSize();
            if (result.spaceId() != null) {
                boolean quotaUpdated = spaceMapper.update(null, new UpdateWrapper<Space>()
                        .eq("id", result.spaceId()).eq("isDelete", 0)
                        .setSql("totalSize = totalSize + " + size)
                        .setSql("totalCount = totalCount + 1")
                        .apply("totalSize + " + size + " <= maxSize")
                        .apply("totalCount + 1 <= maxCount")) > 0;
                if (!quotaUpdated) {
                    PictureTaskItem failed = new PictureTaskItem();
                    failed.setId(item.getId()); failed.setSourceUrl(result.sourceUrl()); failed.setStatus("FAILED");
                    failed.setErrorMessage("空间额度不足");
                    failed.setAttemptCount((item.getAttemptCount() == null ? 0 : item.getAttemptCount()) + 1);
                    pictureTaskItemMapper.updateById(failed);
                    publishFileDelete(result, result.taskId());
                    recalculateBatchTask(result.taskId());
                    return null;
                }
            }
            Picture picture = new Picture();
            picture.setSpaceId(result.spaceId());
            picture.setUrl(result.url());
            picture.setThumbnailUrl(result.thumbnailUrl());
            picture.setOriginalObjectKey(result.originalObjectKey());
            picture.setDisplayObjectKey(result.displayObjectKey());
            picture.setThumbnailObjectKey(result.thumbnailObjectKey());
            picture.setName(result.name() == null || result.name().isBlank() ? "未命名图片" : result.name());
            picture.setPicSize(result.picSize()); picture.setPicWidth(result.picWidth()); picture.setPicHeight(result.picHeight());
            picture.setPicScale(result.picScale()); picture.setPicFormat(result.picFormat()); picture.setPicColor(result.picColor());
            picture.setUserId(result.userId()); picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            picture.setProcessStatus(0); picture.setVersion(1L); picture.setTaskItemId(item.getId()); picture.setIsDelete(0);
            pictureMapper.insert(picture);
            PictureTask process = new PictureTask();
            process.setTaskType("PICTURE_PROCESS"); process.setUserId(result.userId()); process.setSpaceId(result.spaceId());
            process.setPictureId(picture.getId()); process.setStatus("PENDING"); process.setProgress(0); process.setTotalCount(1);
            process.setSuccessCount(0); process.setFailedCount(0); process.setRetryCount(0); process.setCreateTime(new Date()); process.setUpdateTime(new Date());
            pictureTaskMapper.insert(process);
            Picture link = new Picture(); link.setId(picture.getId()); link.setProcessTaskId(process.getId()); pictureMapper.updateById(link);
            PictureTaskItem done = new PictureTaskItem(); done.setId(item.getId()); done.setSourceUrl(result.sourceUrl()); done.setPictureId(picture.getId());
            done.setStatus("SUCCEEDED"); done.setAttemptCount((item.getAttemptCount() == null ? 0 : item.getAttemptCount()) + 1); pictureTaskItemMapper.updateById(done);
            outboxEventService.createPictureProcessingRequestedV1(picture.getId(), process.getId(), picture.getVersion(), picture.getOriginalObjectKey());
            recalculateBatchTask(result.taskId());
            return null;
        });
    }

    private void applyBatchCompleted(PicturePayloadsV1.BatchCompleted result) {
        transactionTemplate.execute(status -> {
            PictureTask task = pictureTaskMapper.selectById(result.taskId());
            if (task == null) throw new IllegalStateException("批量任务不存在: " + result.taskId());
            PictureTask update = new PictureTask(); update.setId(result.taskId()); update.setTotalCount(result.totalCount());
            update.setSuccessCount(result.successCount()); update.setFailedCount(result.failedCount()); update.setStatus("RUNNING");
            update.setProgress(0); pictureTaskMapper.updateById(update);
            recalculateBatchTask(result.taskId());
            return null;
        });
    }

    private void recalculateBatchTask(Long taskId) {
        PictureTask task = pictureTaskMapper.selectById(taskId);
        if (task == null) return;
        List<PictureTaskItem> items = pictureTaskItemMapper.selectList(new QueryWrapper<PictureTaskItem>().eq("taskId", taskId));
        int success = (int) items.stream().filter(item -> "SUCCEEDED".equals(item.getStatus())).count();
        int failed = (int) items.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        int total = task.getTotalCount() == null ? items.size() : task.getTotalCount();
        PictureTask update = new PictureTask(); update.setId(taskId); update.setSuccessCount(success); update.setFailedCount(failed);
        update.setProgress(total <= 0 ? 100 : Math.min(100, (success + failed) * 100 / total));
        if (total <= 0) {
            update.setStatus("FAILED"); update.setErrorMessage("没有抓取到可用图片"); update.setEndTime(new Date());
        } else if (success + failed >= total) {
            update.setStatus(failed == 0 ? "SUCCEEDED" : (success == 0 ? "FAILED" : "PARTIAL_FAILED"));
            update.setProgress(100); update.setEndTime(new Date());
        } else {
            update.setStatus("RUNNING");
        }
        pictureTaskMapper.updateById(update);
    }

    private void publishFileDelete(PicturePayloadsV1.BatchItemPrepared result, Long taskId) {
        List<String> keys = new ArrayList<>();
        if (result.originalObjectKey() != null) keys.add(result.originalObjectKey());
        if (result.displayObjectKey() != null) keys.add(result.displayObjectKey());
        if (result.thumbnailObjectKey() != null) keys.add(result.thumbnailObjectKey());
        if (!keys.isEmpty()) outboxEventService.createFilesDeleteRequestedV1(taskId, 1L, keys);
    }

    private void publishSearch(Picture picture) {
        if (picture == null || picture.getId() == null) return;
        Long version = versionOf(picture.getVersion());
        if (Integer.valueOf(1).equals(picture.getIsDelete()) || !Integer.valueOf(2).equals(picture.getProcessStatus())) {
            outboxEventService.createV1(PictureEventTypesV1.PICTURE_SEARCH_DELETED, PictureRoutingV1.SEARCH_INDEX_DELETED,
                    picture.getId(), version, null, null, null,
                    new PicturePayloadsV1.PictureSearchDeleted(picture.getId(), version));
            return;
        }
        List<String> tags = new ArrayList<>();
        if (picture.getTags() != null && !picture.getTags().isBlank()) {
            try { tags = objectMapper.readValue(picture.getTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)); } catch (Exception ignored) { }
        }
        PicturePayloadsV1.PictureSearchSnapshot snapshot = new PicturePayloadsV1.PictureSearchSnapshot(
                picture.getId(), version, picture.getUrl(), picture.getName(), picture.getIntroduction(), picture.getCategory(), tags,
                picture.getPicSize(), picture.getPicWidth(), picture.getPicHeight(), picture.getPicScale(), picture.getPicFormat(), picture.getUserId(),
                picture.getSpaceId(), picture.getPicColor(), picture.getReviewStatus(), picture.getProcessStatus(), picture.getIsDelete(), instant(picture.getCreateTime()),
                instant(picture.getEditTime()), instant(picture.getUpdateTime()));
        outboxEventService.createV1(PictureEventTypesV1.PICTURE_SEARCH_UPSERTED, PictureRoutingV1.SEARCH_INDEX_UPSERTED,
                picture.getId(), version, null, null, null, snapshot);
    }

    private long versionOf(Long version) { return version == null || version < 1 ? 1L : version; }
    private Instant instant(java.util.Date value) { return value == null ? null : value.toInstant(); }

    /** Version/task fence used by both success and failure paths; stale or reordered results are no-ops. */
    static boolean accepts(Picture current, Long taskId, Long expectedVersion) {
        return current != null
                && Objects.equals(current.getProcessTaskId(), taskId)
                && Objects.equals(current.getVersion() == null || current.getVersion() < 1 ? 1L : current.getVersion(),
                expectedVersion == null || expectedVersion < 1 ? 1L : expectedVersion);
    }
}
