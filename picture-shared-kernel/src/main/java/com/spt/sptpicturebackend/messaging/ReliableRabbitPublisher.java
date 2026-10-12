package com.spt.sptpicturebackend.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/** One publisher path that treats both confirm and returned-message state as mandatory. */
@Component
public class ReliableRabbitPublisher {
    @Resource private RabbitTemplate rabbitTemplate;

    public void publish(String exchange, String routingKey, String payload) throws Exception {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
        if (correlationData.getReturned() != null) throw new IllegalStateException("消息路由不到队列");
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException(confirm == null ? "broker confirm timeout" : confirm.getReason());
        }
    }

    public void publishToQueue(String queue, String payload) throws Exception { publish("", queue, payload); }

    public void publishToQueue(String queue, byte[] payload) throws Exception {
        Message message = MessageBuilder.withBody(payload)
                .setContentType("application/json")
                .setHeader("x-picture-retry-count", 0)
                .build();
        publishMessage("", queue, message);
    }

    public void parkV1(byte[] raw, String sourceQueue, String reason) throws Exception {
        park(PictureRoutingV1.PARKING_EXCHANGE, "picture.parking.v1", raw, sourceQueue, reason);
    }

    public void retryV1(Message original, String routingKey, int retryCount, String sourceQueue) throws Exception {
        Message message = MessageBuilder.fromMessage(original)
                .setHeader("x-picture-retry-count", retryCount)
                .setHeader("x-original-queue", sourceQueue)
                .build();
        publishMessage(PictureRoutingV1.RETRY_EXCHANGE, routingKey, message);
    }

    public void deadLetterV1(Message original, String routingKey, String sourceQueue, String reason) throws Exception {
        Message message = MessageBuilder.fromMessage(original)
                .setHeader("x-original-queue", sourceQueue)
                .setHeader("x-dead-letter-reason", reason == null ? "consumer-failed" : reason)
                .build();
        publishMessage(PictureRoutingV1.DLX_EXCHANGE, routingKey, message);
    }

    private void park(String exchange, String routingKey, byte[] raw, String sourceQueue, String reason) throws Exception {
        Message message = MessageBuilder.withBody(raw)
                .setContentType("application/json")
                .setHeader("x-original-queue", sourceQueue)
                .setHeader("x-park-reason", reason == null ? "invalid-message" : reason)
                .build();
        publishMessage(exchange, routingKey, message);
    }

    private void publishMessage(String exchange, String routingKey, Message message) throws Exception {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.send(exchange, routingKey, message, correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
        if (correlationData.getReturned() != null || confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("parking queue publish failed");
        }
    }
}
