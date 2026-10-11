package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.search.PictureSearchTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "picture.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PictureIndexMaintenanceConsumer {
    @Resource private ObjectMapper objectMapper;
    @Resource private RabbitTemplate rabbitTemplate;
    @Resource private PictureSearchTaskService taskService;

    @RabbitListener(queues = RabbitMqTopology.INDEX_MAINTENANCE_QUEUE,
            concurrency = "${picture.async.index-maintenance-concurrency:1}")
    public void consume(Message message, Channel channel) throws Exception {
        PictureMessageEnvelope envelope = objectMapper.readValue(message.getBody(), PictureMessageEnvelope.class);
        try {
            taskService.handleMaintenance(envelope);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
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
                envelope.setRetryCount(envelope.getRetryCount() + 1);
                sendConfirmed(RabbitMqTopology.RETRY_EXCHANGE,
                        "picture.index-rebuild.retry." + envelope.getRetryCount(),
                        objectMapper.writeValueAsString(envelope));
            } else {
                sendConfirmed(RabbitMqTopology.DLX_EXCHANGE, "INDEX_MAINTENANCE",
                        objectMapper.writeValueAsString(envelope));
                log.error("索引维护事件进入 DLQ，taskId={}", envelope.getTaskId(), error);
            }
            return true;
        } catch (Exception publishError) {
            log.error("索引维护重试或死信投递失败，taskId={}", envelope.getTaskId(), publishError);
            return false;
        }
    }

    private void sendConfirmed(String exchange, String routingKey, String payload) throws Exception {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
        if (confirm == null || !confirm.isAck() || correlationData.getReturned() != null) {
            throw new IllegalStateException("消息未获得有效 Broker confirm");
        }
    }
}
