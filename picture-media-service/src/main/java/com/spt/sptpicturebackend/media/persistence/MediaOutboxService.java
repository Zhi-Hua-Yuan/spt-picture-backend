package com.spt.sptpicturebackend.media.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.spt.sptpicturebackend.media.persistence.entity.MediaOutboxEvent;
import com.spt.sptpicturebackend.media.persistence.mapper.MediaOutboxEventMapper;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Media-local transactional outbox. Result events survive worker restarts and broker outages. */
@Service
public class MediaOutboxService {
    private static final String PENDING = "PENDING";
    private static final String SENDING = "SENDING";
    private static final String SENT = "SENT";
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    @Resource private MediaOutboxEventMapper mapper;
    @Resource private ObjectMapper objectMapper;
    @Resource private RabbitTemplate rabbitTemplate;
    @Value("${picture.async.outbox-enabled:true}") private boolean enabled;

    @Transactional
    public String enqueue(String eventType, String routingKey, EventEnvelopeV1<?> cause,
                          Long aggregateId, Long version, Object payload) {
        EventEnvelopeV1<Object> envelope = new EventEnvelopeV1<>(UUID.randomUUID().toString(), eventType,
                EventEnvelopeV1.VERSION, Instant.now(), cause.traceId(), cause.correlationId(), cause.eventId(),
                aggregateId, version, payload);
        MediaOutboxEvent event = new MediaOutboxEvent();
        event.setEventId(envelope.eventId()); event.setAggregateId(aggregateId); event.setEventType(eventType);
        event.setRoutingKey(routingKey); event.setExchangeName(PictureRoutingV1.EVENTS_EXCHANGE);
        event.setVersion(version == null ? 1L : version); event.setPayload(write(envelope)); event.setStatus(PENDING);
        event.setRetryCount(0); event.setNextRetryTime(new Date()); event.setCreateTime(new Date()); event.setUpdateTime(new Date());
        mapper.insert(event);
        return event.getEventId();
    }

    @Scheduled(fixedDelayString = "${picture.async.outbox-interval-ms:500}")
    public void publishPending() {
        if (!enabled) return;
        resetStaleSending();
        Date now = new Date();
        List<MediaOutboxEvent> events = mapper.selectList(new QueryWrapper<MediaOutboxEvent>().eq("status", PENDING)
                .and(w -> w.isNull("next_retry_time").or().le("next_retry_time", now)).orderByAsc("id").last("limit 100"));
        for (MediaOutboxEvent event : events) if (claim(event.getId())) publishOne(event);
    }

    private boolean claim(Long id) {
        return mapper.update(null, new UpdateWrapper<MediaOutboxEvent>().eq("id", id).eq("status", PENDING)
                .set("status", SENDING).set("locked_at", new Date()).set("lock_owner", INSTANCE_ID)) > 0;
    }

    private void publishOne(MediaOutboxEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(event.getEventId());
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), event.getPayload(), correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
            if (correlationData.getReturned() != null || confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "broker confirm timeout" : confirm.getReason());
            }
            mapper.update(null, new UpdateWrapper<MediaOutboxEvent>().eq("id", event.getId()).set("status", SENT)
                    .set("sent_time", new Date()).set("locked_at", null).set("lock_owner", null));
        } catch (Exception error) {
            int retries = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            Calendar next = Calendar.getInstance();
            next.setTimeInMillis(System.currentTimeMillis() + Math.min(Duration.ofMinutes(5).toMillis(), 1000L * (1L << Math.min(retries, 8))));
            mapper.update(null, new UpdateWrapper<MediaOutboxEvent>().eq("id", event.getId()).set("status", PENDING)
                    .set("retry_count", retries).set("next_retry_time", next.getTime()).set("last_error", shorten(error.getMessage()))
                    .set("locked_at", null).set("lock_owner", null));
        }
    }

    private void resetStaleSending() {
        Calendar cut = Calendar.getInstance(); cut.add(Calendar.MINUTE, -2);
        mapper.update(null, new UpdateWrapper<MediaOutboxEvent>().eq("status", SENDING).lt("locked_at", cut.getTime())
                .set("status", PENDING).set("next_retry_time", new Date()).set("locked_at", null).set("lock_owner", null));
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("无法记录媒体事件", error); }
    }

    private String shorten(String value) { return value == null ? "publish failed" : value.substring(0, Math.min(value.length(), 1900)); }
}
