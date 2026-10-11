package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.search.PictureSearchTaskService;
import com.spt.sptpicturebackend.service.InboxService;
import com.spt.sptpicturebackend.service.OutboxEventService;
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
public class PictureIndexMaintenanceConsumer {
    private static final String GROUP = "picture-index-maintenance";
    @Resource private ObjectMapper objectMapper;
    @Resource private PictureSearchTaskService taskService;
    @Resource private InboxService inboxService;
    @Resource private OutboxEventService outboxEventService;
    @Resource private ReliableRabbitPublisher publisher;

    @RabbitListener(queues = RabbitMqTopology.INDEX_MAINTENANCE_QUEUE,
            concurrency = "${picture.async.index-maintenance-concurrency:1}")
    public void consume(Message message, Channel channel) throws Exception {
        PictureMessageEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), PictureMessageEnvelope.class);
            validate(envelope);
        } catch (Exception invalid) {
            try {
                publisher.park(message.getBody(), RabbitMqTopology.INDEX_MAINTENANCE_QUEUE, invalid.getMessage());
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
            taskService.handleMaintenance(envelope);
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

    private boolean retryOrDlq(PictureMessageEnvelope envelope, Exception error) {
        try {
            if (envelope.getRetryCount() < 3) {
                int next = envelope.getRetryCount() + 1;
                envelope.setRetryCount(next);
                publisher.publish(RabbitMqTopology.RETRY_EXCHANGE,
                        "picture.index-rebuild.retry." + next,
                        objectMapper.writeValueAsString(envelope));
            } else {
                outboxEventService.createDeadLetter(envelope, GROUP, "INDEX_MAINTENANCE");
                log.error("索引维护事件进入 DLQ，taskId={}", envelope.getTaskId(), error);
            }
            return true;
        } catch (Exception publishError) {
            log.error("索引维护重试或死信投递失败，taskId={}", envelope.getTaskId(), publishError);
            return false;
        }
    }

    private void validate(PictureMessageEnvelope envelope) {
        if (envelope == null || envelope.getSchemaVersion() != 1 || envelope.getEventId() == null
                || envelope.getEventType() == null || envelope.getTaskId() == null) {
            throw new IllegalArgumentException("索引维护消息无效");
        }
        if (!PictureEventType.INDEX_REBUILD_REQUESTED.equals(envelope.getEventType())
                && !PictureEventType.INDEX_RECONCILE_REQUESTED.equals(envelope.getEventType())) {
            throw new IllegalArgumentException("未知索引维护事件");
        }
    }
}
