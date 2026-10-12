package com.spt.sptpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.mapper.OutboxEventMapper;
import com.spt.sptpicturebackend.mapper.MqConsumeLogMapper;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.model.entity.OutboxEvent;
import com.spt.sptpicturebackend.model.entity.Picture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OutboxEventService {
    private static final String PENDING = "PENDING";
    private static final String SENDING = "SENDING";
    private static final String SENT = "SENT";

    @Resource
    private OutboxEventMapper outboxEventMapper;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private MqConsumeLogMapper mqConsumeLogMapper;
    @Resource
    private PictureMapper pictureMapper;
    @Resource
    private MeterRegistry meterRegistry;

    @Value("${picture.async.outbox-enabled:true}")
    private boolean enabled;
    @Value("${picture.async.outbox-retention-days:7}")
    private int outboxRetentionDays;
    @Value("${picture.async.consume-log-retention-days:30}")
    private int consumeLogRetentionDays;
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    public String createPictureChanged(Long pictureId, Long version) {
        Picture picture = pictureMapper.selectByIdIncludingDeleted(pictureId);
        if (picture == null || Integer.valueOf(1).equals(picture.getIsDelete())
                || !Integer.valueOf(2).equals(picture.getProcessStatus())) {
            return createPictureDeleted(pictureId, version, Collections.emptyMap());
        }
        List<String> tags = parseTags(picture.getTags());
        PicturePayloadsV1.PictureSearchSnapshot snapshot = new PicturePayloadsV1.PictureSearchSnapshot(
                picture.getId(), versionOf(picture.getVersion(), version), picture.getUrl(), picture.getName(),
                picture.getIntroduction(), picture.getCategory(), tags, picture.getPicSize(), picture.getPicWidth(),
                picture.getPicHeight(), picture.getPicScale(), picture.getPicFormat(), picture.getUserId(),
                picture.getSpaceId(), picture.getPicColor(), picture.getReviewStatus(), picture.getProcessStatus(),
                picture.getIsDelete(), instant(picture.getCreateTime()), instant(picture.getEditTime()), instant(picture.getUpdateTime()));
        return createV1(PictureEventTypesV1.PICTURE_SEARCH_UPSERTED, PictureRoutingV1.SEARCH_INDEX_UPSERTED,
                pictureId, snapshot.version(), null, null, null, snapshot);
    }

    public String createPictureDeleted(Long pictureId, Long version, Map<String, Object> data) {
        return createV1(PictureEventTypesV1.PICTURE_SEARCH_DELETED, PictureRoutingV1.SEARCH_INDEX_DELETED,
                pictureId, versionOf(null, version), null, null, null,
                new PicturePayloadsV1.PictureSearchDeleted(pictureId, versionOf(null, version)));
    }

    private List<String> parseTags(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Instant instant(Date value) { return value == null ? null : value.toInstant(); }

    private long versionOf(Long preferred, Long fallback) {
        Long value = preferred == null ? fallback : preferred;
        return value == null || value < 1 ? 1L : value;
    }

    /** Persist a typed V1 event in the same Core transaction as its aggregate change. */
    @Transactional
    public String createV1(String eventType, String routingKey, Long aggregateId, Long version,
                           String traceId, String correlationId, String causationId, Object payload) {
        EventEnvelopeV1<Object> envelope = new EventEnvelopeV1<>(UUID.randomUUID().toString(), eventType,
                EventEnvelopeV1.VERSION, java.time.Instant.now(), traceId, correlationId, causationId,
                aggregateId, version, payload);
        OutboxEvent event = new OutboxEvent();
        event.setEventId(envelope.eventId());
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setRoutingKey(routingKey);
        event.setExchangeName(PictureRoutingV1.EVENTS_EXCHANGE);
        event.setVersion(version == null ? 1L : version);
        try {
            event.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化 V1 图片事件", error);
        }
        event.setStatus(PENDING);
        event.setRetryCount(0);
        event.setNextRetryTime(new Date());
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        outboxEventMapper.insert(event);
        return event.getEventId();
    }

    public String createPictureProcessingRequestedV1(Long pictureId, Long taskId, Long version, String originalObjectKey) {
        return createV1(PictureEventTypesV1.PICTURE_PROCESSING_REQUESTED, PictureRoutingV1.MEDIA_PROCESS_REQUESTED,
                pictureId, version, null, String.valueOf(taskId), null,
                new PicturePayloadsV1.PictureProcessingRequested(pictureId, taskId, version, originalObjectKey));
    }

    public String createFilesDeleteRequestedV1(Long aggregateId, Long version, List<String> objectKeys) {
        return createV1(PictureEventTypesV1.FILES_DELETE_REQUESTED, PictureRoutingV1.MEDIA_FILE_DELETE_REQUESTED,
                aggregateId, version, null, String.valueOf(aggregateId), null,
                new PicturePayloadsV1.FilesDeleteRequested(objectKeys));
    }

    @Scheduled(fixedDelayString = "${picture.async.outbox-interval-ms:500}")
    public void publishPending() {
        if (!enabled) return;
        resetStaleSending();
        Date now = new Date();
        List<OutboxEvent> events = outboxEventMapper.selectList(new QueryWrapper<OutboxEvent>()
                .in("status", PENDING)
                .and(w -> w.isNull("nextRetryTime").or().le("nextRetryTime", now))
                .orderByAsc("id")
                .last("limit 100"));
        for (OutboxEvent event : events) {
            if (!claim(event.getId())) continue;
            publishOne(event);
        }
        meterRegistry.gauge("picture.outbox.pending", outboxEventMapper,
                mapper -> mapper.selectCount(new QueryWrapper<OutboxEvent>().eq("status", PENDING)));
    }

    private boolean claim(Long id) {
        return outboxEventMapper.update(null, new UpdateWrapper<OutboxEvent>()
                .eq("id", id).eq("status", PENDING)
                .set("status", SENDING)
                .set("lockedAt", new Date())
                .set("lockOwner", instanceId())) > 0;
    }

    private void publishOne(OutboxEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(event.getEventId());
            rabbitTemplate.convertAndSend(event.getExchangeName() == null ? PictureRoutingV1.EVENTS_EXCHANGE : event.getExchangeName(), event.getRoutingKey(),
                    event.getPayload(), correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("消息路由不到队列");
            }
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "broker confirm timeout" : confirm.getReason());
            }
            outboxEventMapper.update(null, new UpdateWrapper<OutboxEvent>().eq("id", event.getId())
                    .set("status", SENT).set("sentTime", new Date()).set("lockedAt", null).set("lockOwner", null));
            meterRegistry.counter("picture.outbox.published.total").increment();
        } catch (Exception e) {
            int retries = event.getRetryCount() == null ? 0 : event.getRetryCount() + 1;
            long delay = Math.min(Duration.ofMinutes(5).toMillis(), 1000L * (1L << Math.min(retries, 8)));
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis() + delay);
            outboxEventMapper.update(null, new UpdateWrapper<OutboxEvent>().eq("id", event.getId())
                    .set("status", PENDING).set("retryCount", retries)
                    .set("nextRetryTime", calendar.getTime()).set("lastError", shorten(e.getMessage()))
                    .set("lockedAt", null).set("lockOwner", null));
            log.warn("outbox publish failed, eventId={}, retryCount={}", event.getEventId(), retries, e);
            meterRegistry.counter("picture.outbox.publish.failures.total").increment();
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void cleanupHistory() {
        Calendar outboxCutoff = Calendar.getInstance();
        outboxCutoff.add(Calendar.DAY_OF_YEAR, -outboxRetentionDays);
        outboxEventMapper.delete(new QueryWrapper<OutboxEvent>().eq("status", SENT)
                .lt("sentTime", outboxCutoff.getTime()).last("limit 1000"));
        Calendar consumeCutoff = Calendar.getInstance();
        consumeCutoff.add(Calendar.DAY_OF_YEAR, -consumeLogRetentionDays);
        mqConsumeLogMapper.delete(new QueryWrapper<com.spt.sptpicturebackend.model.entity.MqConsumeLog>()
                .lt("consumedTime", consumeCutoff.getTime()).last("limit 5000"));
    }

    private void resetStaleSending() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -2);
        outboxEventMapper.update(null, new UpdateWrapper<OutboxEvent>().eq("status", SENDING)
                .lt("lockedAt", calendar.getTime()).set("status", PENDING).set("nextRetryTime", new Date())
                .set("lockedAt", null).set("lockOwner", null));
    }

    private String instanceId() {
        return INSTANCE_ID;
    }

    private String shorten(String value) {
        if (value == null) return "publish failed";
        return value.substring(0, Math.min(value.length(), 1900));
    }

}
