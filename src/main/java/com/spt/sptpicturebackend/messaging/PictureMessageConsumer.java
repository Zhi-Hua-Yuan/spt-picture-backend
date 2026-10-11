package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.service.InboxService;
import com.spt.sptpicturebackend.service.PictureAsyncProcessingService;
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
public class PictureMessageConsumer {
    @Resource private ObjectMapper objectMapper;
    @Resource private PictureAsyncProcessingService processingService;
    @Resource private InboxService inboxService;
    @Resource private ReliableRabbitPublisher publisher;

    @RabbitListener(queues = RabbitMqTopology.PROCESS_QUEUE, concurrency = "${picture.async.process-concurrency:2}")
    public void process(Message message, Channel channel) throws Exception { consume(message, channel, RabbitMqTopology.PROCESS_QUEUE, "process"); }

    @RabbitListener(queues = RabbitMqTopology.BATCH_QUEUE, concurrency = "${picture.async.batch-concurrency:2}")
    public void batch(Message message, Channel channel) throws Exception { consume(message, channel, RabbitMqTopology.BATCH_QUEUE, "batch"); }

    @RabbitListener(queues = RabbitMqTopology.FILE_DELETE_QUEUE, concurrency = "${picture.async.file-delete-concurrency:2}")
    public void fileDelete(Message message, Channel channel) throws Exception { consume(message, channel, RabbitMqTopology.FILE_DELETE_QUEUE, "file-delete"); }

    private void consume(Message message, Channel channel, String sourceQueue, String queueType) throws Exception {
        PictureMessageEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), PictureMessageEnvelope.class);
            validate(envelope);
        } catch (Exception invalid) {
            try {
                publisher.park(message.getBody(), sourceQueue, invalid.getMessage());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } catch (Exception parkingFailure) {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
            return;
        }
        String group = consumerGroup(envelope);
        try {
            switch (envelope.getEventType()) {
                case PictureEventType.PROCESS_REQUESTED -> processingService.handleProcess(envelope);
                case PictureEventType.BATCH_IMPORT_REQUESTED -> processingService.handleBatchImport(envelope);
                case PictureEventType.BATCH_IMPORT_ITEM_REQUESTED -> processingService.handleBatchItem(envelope);
                case PictureEventType.PICTURE_DELETED, PictureEventType.FILES_DELETE_REQUESTED -> processingService.handleFileDelete(envelope);
                default -> throw new IllegalStateException("未知图片事件: " + envelope.getEventType());
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
            inboxService.markFailed(group, envelope.getEventId(), error.getMessage());
            if (sendRetryOrDlq(envelope, queueType, group, error)) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

    private boolean sendRetryOrDlq(PictureMessageEnvelope envelope, String queueType,
                                   String group, Exception error) {
        try {
            if (envelope.getRetryCount() < 3) {
                int next = envelope.getRetryCount() + 1;
                envelope.setRetryCount(next);
                String key = switch (queueType) {
                    case "process" -> "picture.process.retry." + next;
                    case "batch" -> "picture.batch.retry." + next;
                    default -> "picture.file-delete.retry." + next;
                };
                publisher.publish(RabbitMqTopology.RETRY_EXCHANGE, key, objectMapper.writeValueAsString(envelope));
            } else {
                processingService.markFinalFailure(envelope, error.getMessage(), group);
                log.error("消息进入 DLQ，eventId={}, type={}", envelope.getEventId(), envelope.getEventType(), error);
            }
            return true;
        } catch (Exception publishError) {
            log.error("消息重试或死信投递失败，eventId={}", envelope.getEventId(), publishError);
            return false;
        }
    }

    private String consumerGroup(PictureMessageEnvelope envelope) {
        return switch (envelope.getEventType()) {
            case PictureEventType.PROCESS_REQUESTED -> "picture-process";
            case PictureEventType.BATCH_IMPORT_REQUESTED -> "picture-batch";
            case PictureEventType.BATCH_IMPORT_ITEM_REQUESTED -> "picture-batch-item";
            default -> "picture-file-delete";
        };
    }

    private void validate(PictureMessageEnvelope envelope) {
        if (envelope == null || envelope.getSchemaVersion() != 1 || envelope.getEventType() == null
                || envelope.getEventId() == null || envelope.getEventId().isBlank()) {
            throw new IllegalArgumentException("消息信封版本或事件标识无效");
        }
        if (!PictureEventType.PROCESS_REQUESTED.equals(envelope.getEventType())
                && !PictureEventType.BATCH_IMPORT_REQUESTED.equals(envelope.getEventType())
                && !PictureEventType.BATCH_IMPORT_ITEM_REQUESTED.equals(envelope.getEventType())
                && !PictureEventType.PICTURE_DELETED.equals(envelope.getEventType())
                && !PictureEventType.FILES_DELETE_REQUESTED.equals(envelope.getEventType())) {
            throw new IllegalArgumentException("未知图片事件");
        }
    }
}
