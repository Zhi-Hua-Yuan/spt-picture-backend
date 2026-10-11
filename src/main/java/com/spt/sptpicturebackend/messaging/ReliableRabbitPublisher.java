package com.spt.sptpicturebackend.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

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

    public void park(byte[] raw, String sourceQueue, String reason) throws Exception {
        Message message = MessageBuilder.withBody(raw)
                .setContentType("application/json")
                .setHeader("x-original-queue", sourceQueue)
                .setHeader("x-park-reason", reason == null ? "invalid-message" : reason)
                .build();
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.send(RabbitMqTopology.PARKING_EXCHANGE, "picture.parking", message, correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(8, TimeUnit.SECONDS);
        if (correlationData.getReturned() != null || confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("parking queue publish failed");
        }
    }
}
