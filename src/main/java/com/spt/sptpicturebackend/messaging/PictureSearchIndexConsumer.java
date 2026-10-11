package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.service.InboxService;
import com.spt.sptpicturebackend.service.OutboxEventService;
import com.spt.sptpicturebackend.search.PictureSearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

import jakarta.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "picture.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PictureSearchIndexConsumer {
    private static final String GROUP = "picture-search-index";
    @Resource private ObjectMapper objectMapper;
    @Resource private PictureMapper pictureMapper;
    @Resource private PictureSearchIndexService indexService;
    @Resource private InboxService inboxService;
    @Resource private OutboxEventService outboxEventService;
    @Resource private ReliableRabbitPublisher publisher;

    @RabbitListener(queues = RabbitMqTopology.SEARCH_INDEX_QUEUE,
            concurrency = "${picture.async.search-index-concurrency:2}")
    public void consume(Message message, Channel channel) throws Exception {
        PictureMessageEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), PictureMessageEnvelope.class);
            validate(envelope);
        } catch (Exception invalid) {
            try {
                publisher.park(message.getBody(), RabbitMqTopology.SEARCH_INDEX_QUEUE, invalid.getMessage());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (Exception ignored) {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
            return;
        }
        if (inboxService.isSucceeded(GROUP, envelope.getEventId()) || !inboxService.claim(GROUP, envelope.getEventId())) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }
        try {
            synchronize(envelope);
            inboxService.markSucceeded(GROUP, envelope.getEventId());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed(GROUP, envelope.getEventId(), error.getMessage());
            if (retryOrDlq(envelope, error)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

    private void synchronize(PictureMessageEnvelope envelope) throws Exception {
        Long pictureId = envelope.getPictureId();
        if (pictureId == null) throw new IllegalArgumentException("搜索事件缺少 pictureId");
        Picture current = pictureMapper.selectByIdIncludingDeleted(pictureId);
        if (PictureEventType.PICTURE_DELETED.equals(envelope.getEventType())) {
            long eventVersion = versionOf(envelope.getPictureVersion());
            if (current == null || Integer.valueOf(1).equals(current.getIsDelete())) {
                indexService.deletePictureById(pictureId, Math.max(eventVersion, versionOf(current == null ? null : current.getVersion())));
            } else if (versionOf(current.getVersion()) <= eventVersion) {
                indexService.deletePictureById(pictureId, eventVersion);
            } else {
                indexService.indexPicture(current);
            }
            return;
        }
        if (current == null || Integer.valueOf(1).equals(current.getIsDelete())) {
            indexService.deletePictureById(pictureId, versionOf(envelope.getPictureVersion()));
            return;
        }
        if (envelope.getPictureVersion() != null && versionOf(current.getVersion()) < envelope.getPictureVersion()) {
            throw new IllegalStateException("数据库版本尚未提交: pictureId=" + pictureId);
        }
        indexService.indexPicture(current);
    }

    private boolean retryOrDlq(PictureMessageEnvelope envelope, Exception error) {
        try {
            if (envelope.getRetryCount() < 3) {
                int next = envelope.getRetryCount() + 1;
                envelope.setRetryCount(next);
                publisher.publish(RabbitMqTopology.RETRY_EXCHANGE,
                        "picture.search-index.retry." + next,
                        objectMapper.writeValueAsString(envelope));
            } else {
                outboxEventService.createDeadLetter(envelope, GROUP,
                        PictureEventType.PICTURE_DELETED.equals(envelope.getEventType())
                                ? PictureEventType.PICTURE_DELETED + ".search" : envelope.getEventType());
                log.error("搜索索引事件进入 DLQ，eventId={}, pictureId={}", envelope.getEventId(), envelope.getPictureId(), error);
            }
            return true;
        } catch (Exception publishError) {
            log.error("搜索索引重试或死信投递失败，eventId={}", envelope.getEventId(), publishError);
            return false;
        }
    }

    private void validate(PictureMessageEnvelope envelope) {
        if (envelope == null || envelope.getSchemaVersion() != 1 || envelope.getEventId() == null
                || envelope.getEventType() == null) throw new IllegalArgumentException("消息信封无效");
        if (!PictureEventType.PICTURE_CHANGED.equals(envelope.getEventType())
                && !PictureEventType.PICTURE_DELETED.equals(envelope.getEventType())) {
            throw new IllegalArgumentException("未知搜索事件");
        }
    }

    private long versionOf(Long value) { return value == null || value < 1 ? 1L : value; }
}
