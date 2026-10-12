package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PictureEventTypesV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.search.PictureSearchIndexService;
import com.spt.sptpicturebackend.search.SearchTaskExecutor;
import com.spt.sptpicturebackend.search.persistence.SearchInboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/** Search consumers are projection-only: all authoritative data arrives in the event or Core snapshot API. */
@Slf4j
@Component
public class SearchMessageConsumers {
    private static final String INDEX_GROUP = "picture-search-index";
    private static final String MAINTENANCE_GROUP = "picture-index-maintenance";

    @Resource private ObjectMapper objectMapper;
    @Resource private PictureSearchIndexService indexService;
    @Resource private SearchTaskExecutor taskExecutor;
    @Resource private SearchInboxService inboxService;
    @Resource private ReliableRabbitPublisher publisher;

    @RabbitListener(queues = PictureRoutingV1.SEARCH_INDEX_QUEUE,
            concurrency = "${picture.async.search-index-concurrency:2}",
            autoStartup = "${picture.async.search-index-enabled:true}")
    public void searchIndexV1(Message message, Channel channel) throws Exception {
        EventEnvelopeV1<JsonNode> raw;
        try {
            raw = objectMapper.readValue(message.getBody(), new TypeReference<>() { });
            if (raw.schemaVersion() != EventEnvelopeV1.VERSION || raw.eventId() == null) throw new IllegalArgumentException("search envelope is invalid");
        } catch (Exception invalid) {
            park(message, channel, invalid);
            return;
        }
        if (inboxService.isSucceeded(INDEX_GROUP, raw.eventId()) || !inboxService.claim(INDEX_GROUP, raw.eventId())) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        try {
            if (PictureEventTypesV1.PICTURE_SEARCH_DELETED.equals(raw.eventType())) {
                PicturePayloadsV1.PictureSearchDeleted deleted = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.PictureSearchDeleted.class);
                indexService.deletePictureById(deleted.pictureId(), versionOf(deleted.version()));
            } else if (PictureEventTypesV1.PICTURE_SEARCH_UPSERTED.equals(raw.eventType())) {
                PicturePayloadsV1.PictureSearchSnapshot snapshot = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.PictureSearchSnapshot.class);
                indexService.indexSnapshot(new com.spt.sptpicturebackend.search.v1.SearchContractsV1.Snapshot(
                        snapshot.pictureId(), snapshot.version(), snapshot.url(), snapshot.name(), snapshot.introduction(),
                        snapshot.category(), snapshot.tags(), snapshot.picSize(), snapshot.picWidth(), snapshot.picHeight(),
                        snapshot.picScale(), snapshot.picFormat(), snapshot.userId(), snapshot.spaceId(), snapshot.picColor(),
                        snapshot.reviewStatus(), snapshot.processStatus(), snapshot.isDelete(), snapshot.createTime(), snapshot.editTime(), snapshot.updateTime()));
            } else {
                throw new IllegalArgumentException("unknown search event: " + raw.eventType());
            }
            inboxService.markSucceeded(INDEX_GROUP, raw.eventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed(INDEX_GROUP, raw.eventId(), error.getMessage());
            retryOrDeadLetter(message, channel, PictureRoutingV1.SEARCH_INDEX_RETRY,
                    PictureRoutingV1.SEARCH_INDEX_DLQ, PictureRoutingV1.SEARCH_INDEX_QUEUE,
                    error.getMessage());
        }
    }

    @RabbitListener(queues = PictureRoutingV1.SEARCH_MAINTENANCE_QUEUE,
            concurrency = "${picture.async.index-maintenance-concurrency:1}",
            autoStartup = "${picture.async.index-maintenance-enabled:true}")
    public void maintenanceV1(Message message, Channel channel) throws Exception {
        EventEnvelopeV1<JsonNode> raw;
        try {
            raw = objectMapper.readValue(message.getBody(), new TypeReference<>() { });
            if (raw.schemaVersion() != EventEnvelopeV1.VERSION) throw new IllegalArgumentException("maintenance envelope is invalid");
        } catch (Exception invalid) {
            park(message, channel, invalid);
            return;
        }
        if (inboxService.isSucceeded(MAINTENANCE_GROUP, raw.eventId()) || !inboxService.claim(MAINTENANCE_GROUP, raw.eventId())) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        try {
            PicturePayloadsV1.SearchMaintenanceRequested payload = objectMapper.treeToValue(raw.payload(), PicturePayloadsV1.SearchMaintenanceRequested.class);
            taskExecutor.handleMaintenance(new EventEnvelopeV1<>(raw.eventId(), raw.eventType(), raw.schemaVersion(), raw.occurredAt(),
                    raw.traceId(), raw.correlationId(), raw.causationId(), raw.aggregateId(), raw.aggregateVersion(), payload));
            inboxService.markSucceeded(MAINTENANCE_GROUP, raw.eventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed(MAINTENANCE_GROUP, raw.eventId(), error.getMessage());
            retryOrDeadLetter(message, channel, PictureRoutingV1.SEARCH_MAINTENANCE_RETRY,
                    PictureRoutingV1.SEARCH_MAINTENANCE_DLQ, PictureRoutingV1.SEARCH_MAINTENANCE_QUEUE,
                    error.getMessage());
        }
    }

    private void park(Message message, Channel channel, Exception invalid) throws Exception {
        try {
            publisher.parkV1(message.getBody(), PictureRoutingV1.PARKING_QUEUE, invalid.getMessage());
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

    private long versionOf(Long version) { return version == null || version < 1 ? 1L : version; }
}
