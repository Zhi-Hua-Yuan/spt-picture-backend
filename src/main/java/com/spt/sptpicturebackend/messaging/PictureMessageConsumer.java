package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.service.PictureAsyncProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.rabbitmq.client.Channel;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "picture.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PictureMessageConsumer {
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private PictureAsyncProcessingService processingService;

    @RabbitListener(queues = RabbitMqTopology.PROCESS_QUEUE, concurrency = "${picture.async.process-concurrency:2}")
    public void process(Message message, Channel channel) throws Exception {
        consume(message, channel, "process");
    }

    @RabbitListener(queues = RabbitMqTopology.BATCH_QUEUE, concurrency = "${picture.async.batch-concurrency:2}")
    public void batch(Message message, Channel channel) throws Exception {
        consume(message, channel, "batch");
    }

    @RabbitListener(queues = RabbitMqTopology.FILE_DELETE_QUEUE, concurrency = "${picture.async.file-delete-concurrency:2}")
    public void fileDelete(Message message, Channel channel) throws Exception {
        consume(message, channel, "file-delete");
    }

    private void consume(Message message, Channel channel, String queue) throws Exception {
        PictureMessageEnvelope envelope = objectMapper.readValue(message.getBody(), PictureMessageEnvelope.class);
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
            if (!sendRetryOrDlq(envelope, queue, error)) {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            } else {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }
        }
    }

    private boolean sendRetryOrDlq(PictureMessageEnvelope envelope, String queue, Exception error) {
        try {
            if (envelope.getRetryCount() < 3) {
                int next = envelope.getRetryCount() + 1;
                envelope.setRetryCount(next);
                String key = switch (queue) {
                    case "process" -> "picture.process.retry." + next;
                    case "batch" -> "picture.batch.retry." + next;
                    default -> "picture.file-delete.retry." + next;
                };
                sendConfirmed(RabbitMqTopology.RETRY_EXCHANGE, key, objectMapper.writeValueAsString(envelope));
            } else {
                processingService.markFinalFailure(envelope, error.getMessage());
                sendConfirmed(RabbitMqTopology.DLX_EXCHANGE, envelope.getEventType(), objectMapper.writeValueAsString(envelope));
            }
            return true;
        } catch (Exception publishError) {
            log.error("消息重试或死信投递失败，eventId={}", envelope.getEventId(), publishError);
            return false;
        }
    }

    private void sendConfirmed(String exchange, String routingKey, String payload) throws Exception {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("消息未获得 Broker confirm");
        }
    }
}
