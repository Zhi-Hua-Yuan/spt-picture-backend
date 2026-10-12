package com.spt.sptpicturebackend.media;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.model.ciModel.auditing.ImageAuditingResponse;
import com.rabbitmq.client.Channel;
import com.spt.sptpicturebackend.manager.CosManager;
import com.spt.sptpicturebackend.manager.upload.UrlPictureUpload;
import com.spt.sptpicturebackend.media.persistence.MediaInboxService;
import com.spt.sptpicturebackend.media.persistence.MediaJobService;
import com.spt.sptpicturebackend.media.persistence.MediaOutboxService;
import com.spt.sptpicturebackend.media.persistence.entity.MediaJob;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.messaging.ReliableRabbitPublisher;
import com.spt.sptpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** V1 media worker. All execution, inbox and result delivery state is media-local. */
@Slf4j
@Component
public class MediaV1MessageConsumer {
    @Resource private ObjectMapper objectMapper;
    @Resource private CosManager cosManager;
    @Resource private UrlPictureUpload urlPictureUpload;
    @Resource private MediaInboxService inboxService;
    @Resource private MediaJobService mediaJobService;
    @Resource private MediaOutboxService mediaOutboxService;
    @Resource private ReliableRabbitPublisher publisher;

    @RabbitListener(queues = PictureRoutingV1.MEDIA_PROCESS_QUEUE,
            concurrency = "${picture.async.process-concurrency:2}",
            autoStartup = "${picture.async.process-enabled:true}")
    public void process(Message message, Channel channel) throws Exception {
        EventEnvelopeV1<JsonNode> raw;
        try { raw = parse(message); } catch (Exception invalid) { parkInvalid(message, channel, invalid); return; }
        if (skipDuplicate(message, raw, "picture-media-process", channel)) return;
        try {
            PicturePayloadsV1.PictureProcessingRequested request = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.PictureProcessingRequested.class);
            MediaJob job = mediaJobService.startProcess(raw, request);
            ImageAuditingResponse review = cosManager.reviewObject(request.originalObjectKey());
            if (review == null || review.getResult() == null) throw new IllegalStateException("内容审核无响应");
            String status = switch (review.getResult()) {
                case "0" -> "PASS";
                case "1" -> "REJECT";
                default -> "REVIEWING";
            };
            PicturePayloadsV1.PictureProcessingSucceeded result = new PicturePayloadsV1.PictureProcessingSucceeded(
                    request.pictureId(), request.taskId(), request.expectedVersion(), status,
                    "0".equals(review.getResult()) ? "经 AI 审核通过" : "1".equals(review.getResult()) ? "判定为违规敏感文件" : "疑似敏感，建议人工复核",
                    null, null, null, null, null, Instant.now());
            mediaJobService.markSucceeded(job);
            publish(PictureEventTypesV1.PICTURE_PROCESSING_SUCCEEDED, raw, request.pictureId(), request.expectedVersion(), result);
            inboxService.markSucceeded("picture-media-process", raw.eventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            try {
                PicturePayloadsV1.PictureProcessingRequested request = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.PictureProcessingRequested.class);
                PicturePayloadsV1.PictureProcessingFailed failed = new PicturePayloadsV1.PictureProcessingFailed(
                        request.pictureId(), request.taskId(), request.expectedVersion(), "MEDIA_PROCESSING_FAILED",
                        error.getMessage(), List.of());
                mediaJobService.markFailed(mediaJobService.startProcess(raw, request), failed.errorCode(), failed.errorMessage());
                publish(PictureEventTypesV1.PICTURE_PROCESSING_FAILED, raw, request.pictureId(), request.expectedVersion(), failed);
                inboxService.markSucceeded("picture-media-process", raw.eventId());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (Exception publishFailure) {
                inboxService.markFailed("picture-media-process", raw.eventId(), publishFailure.getMessage());
                retryOrDeadLetter(message, channel, PictureRoutingV1.MEDIA_PROCESS_RETRY,
                        PictureRoutingV1.MEDIA_PROCESS_DLQ, PictureRoutingV1.MEDIA_PROCESS_QUEUE,
                        publishFailure.getMessage());
            }
        }
    }

    @RabbitListener(queues = PictureRoutingV1.MEDIA_FILE_DELETE_QUEUE,
            concurrency = "${picture.async.file-delete-concurrency:2}",
            autoStartup = "${picture.async.file-delete-enabled:true}")
    public void deleteFiles(Message message, Channel channel) throws Exception {
        EventEnvelopeV1<JsonNode> raw;
        try { raw = parse(message); } catch (Exception invalid) { parkInvalid(message, channel, invalid); return; }
        if (skipDuplicate(message, raw, "picture-media-file-delete", channel)) return;
        try {
            PicturePayloadsV1.FilesDeleteRequested request = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.FilesDeleteRequested.class);
            MediaJob job = mediaJobService.startFileDelete(raw, request);
            if (request.objectKeys() != null) for (String key : request.objectKeys()) if (key != null && !key.isBlank()) cosManager.deleteObject(key);
            mediaJobService.markSucceeded(job);
            inboxService.markSucceeded("picture-media-file-delete", raw.eventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed("picture-media-file-delete", raw.eventId(), error.getMessage());
            retryOrDeadLetter(message, channel, PictureRoutingV1.MEDIA_FILE_DELETE_RETRY,
                    PictureRoutingV1.MEDIA_FILE_DELETE_DLQ, PictureRoutingV1.MEDIA_FILE_DELETE_QUEUE,
                    error.getMessage());
        }
    }

    @RabbitListener(queues = PictureRoutingV1.MEDIA_BATCH_QUEUE,
            concurrency = "${picture.async.batch-concurrency:2}",
            autoStartup = "${picture.async.batch-enabled:true}")
    public void batch(Message message, Channel channel) throws Exception {
        EventEnvelopeV1<JsonNode> raw;
        try { raw = parse(message); } catch (Exception invalid) { parkInvalid(message, channel, invalid); return; }
        if (skipDuplicate(message, raw, "picture-media-batch", channel)) return;
        try {
            PicturePayloadsV1.BatchImportRequested request = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.BatchImportRequested.class);
            List<PicturePayloadsV1.RetryItem> retryItems = request.retryItems() == null ? List.of() : request.retryItems();
            if (retryItems.isEmpty()) retryItems = discover(request);
            MediaJob job = mediaJobService.startBatch(raw, request, retryItems);
            int success = 0;
            for (PicturePayloadsV1.RetryItem item : retryItems) {
                try {
                    UploadPictureResult upload = urlPictureUpload.uploadPicture(item.sourceUrl(),
                            request.spaceId() == null ? "public/" + request.userId() : "space/" + request.spaceId(),
                            "async/" + request.taskId() + "/" + item.sequenceNo() + "/" + raw.eventId());
                    String name = request.namePrefix() == null || request.namePrefix().isBlank()
                            ? upload.getPicName() : request.namePrefix() + item.sequenceNo();
                    PicturePayloadsV1.BatchItemPrepared prepared = new PicturePayloadsV1.BatchItemPrepared(request.taskId(), item.itemId(), item.sequenceNo(), request.userId(), request.spaceId(), item.sourceUrl(),
                            name, upload.getOriginalObjectKey(), upload.getDisplayObjectKey(), upload.getThumbnailObjectKey(), upload.getUrl(), upload.getThumbnailUrl(),
                            upload.getPicColor(), upload.getPicSize(), upload.getPicWidth(), upload.getPicHeight(), upload.getPicScale(), upload.getPicFormat(), true, null);
                    mediaJobService.markItem(job, item, prepared);
                    publish(PictureEventTypesV1.BATCH_ITEM_PREPARED, raw, request.taskId(), 1L, prepared);
                    success++;
                } catch (Exception error) {
                    PicturePayloadsV1.BatchItemPrepared prepared = new PicturePayloadsV1.BatchItemPrepared(request.taskId(), item.itemId(), item.sequenceNo(), request.userId(), request.spaceId(), item.sourceUrl(),
                            null, null, null, null, null, null, null, null, null, null, null, null, false, error.getMessage());
                    mediaJobService.markItem(job, item, prepared);
                    publish(PictureEventTypesV1.BATCH_ITEM_PREPARED, raw, request.taskId(), 1L, prepared);
                }
            }
            PicturePayloadsV1.BatchCompleted completed = new PicturePayloadsV1.BatchCompleted(request.taskId(), retryItems.size(), success, retryItems.size() - success);
            if (success == retryItems.size()) mediaJobService.markSucceeded(job); else mediaJobService.markBatchPartial(job);
            publish(PictureEventTypesV1.BATCH_COMPLETED, raw, request.taskId(), 1L, completed);
            inboxService.markSucceeded("picture-media-batch", raw.eventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed("picture-media-batch", raw.eventId(), error.getMessage());
            retryOrDeadLetter(message, channel, PictureRoutingV1.MEDIA_BATCH_RETRY,
                    PictureRoutingV1.MEDIA_BATCH_DLQ, PictureRoutingV1.MEDIA_BATCH_QUEUE,
                    error.getMessage());
        }
    }

    private EventEnvelopeV1<JsonNode> parse(Message message) throws Exception {
        EventEnvelopeV1<JsonNode> envelope = objectMapper.readValue(message.getBody(), new TypeReference<>() { });
        if (envelope.schemaVersion() != EventEnvelopeV1.VERSION || envelope.eventId() == null) throw new IllegalArgumentException("invalid media event");
        return envelope;
    }

    private void parkInvalid(Message message, Channel channel, Exception invalid) throws Exception {
        try {
            String sourceQueue = message.getMessageProperties().getConsumerQueue();
            publisher.parkV1(message.getBody(), sourceQueue == null ? "unknown" : sourceQueue, invalid.getMessage());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception failed) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
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

    private void publish(String type, EventEnvelopeV1<?> cause, Long aggregateId, Long version, Object payload) {
        mediaOutboxService.enqueue(type, PictureRoutingV1.MEDIA_RESULT, cause, aggregateId, version, payload);
    }

    private List<PicturePayloadsV1.RetryItem> discover(PicturePayloadsV1.BatchImportRequested request) {
        int limit = request.count() == null ? 10 : Math.max(1, Math.min(request.count(), 30));
        try {
            Document document = Jsoup.connect("https://cn.bing.com/images/async?q="
                            + URLEncoder.encode(request.searchText(), StandardCharsets.UTF_8) + "&mmasync=1")
                    .userAgent("Mozilla/5.0 picture-media-service").timeout(10000).get();
            List<String> urls = document.getElementsByClass("dgControl").stream().flatMap(div -> div.select("img.mimg").stream())
                    .map(element -> element.attr("src")).filter(value -> value != null && !value.isBlank())
                    .map(value -> value.contains("?") ? value.substring(0, value.indexOf('?')) : value).limit(limit).collect(Collectors.toList());
            List<PicturePayloadsV1.RetryItem> items = new ArrayList<>();
            for (int index = 0; index < urls.size(); index++) items.add(new PicturePayloadsV1.RetryItem(stableItemId(request.taskId(), index + 1), index + 1, urls.get(index)));
            return items;
        } catch (IOException error) { throw new IllegalStateException("获取候选图片失败", error); }
    }

    private long stableItemId(Long taskId, int sequence) {
        long value = UUID.nameUUIDFromBytes((String.valueOf(taskId) + ":" + sequence).getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        return value == Long.MIN_VALUE ? 1L : Math.abs(value);
    }

    private boolean skipDuplicate(Message message, EventEnvelopeV1<JsonNode> raw, String group, Channel channel) throws Exception {
        if (inboxService.isSucceeded(group, raw.eventId()) || !inboxService.claim(group, raw.eventId())) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return true;
        }
        return false;
    }
}
