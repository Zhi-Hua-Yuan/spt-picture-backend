package com.spt.sptpicturebackend.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.mapper.MqConsumeLogMapper;
import com.spt.sptpicturebackend.mapper.PictureMapper;
import com.spt.sptpicturebackend.model.entity.MqConsumeLog;
import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.search.PictureSearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.rabbitmq.client.Channel;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "picture.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PictureSearchIndexConsumer {
    private static final String GROUP = "picture-search-index";

    @Resource private ObjectMapper objectMapper;
    @Resource private RabbitTemplate rabbitTemplate;
    @Resource private PictureMapper pictureMapper;
    @Resource private MqConsumeLogMapper consumeLogMapper;
    @Resource private PictureSearchIndexService indexService;
    @Resource private TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMqTopology.SEARCH_INDEX_QUEUE,
            concurrency = "${picture.async.search-index-concurrency:2}")
    public void consume(Message message, Channel channel) throws Exception {
        PictureMessageEnvelope envelope = objectMapper.readValue(message.getBody(), PictureMessageEnvelope.class);
        try {
            if (!alreadyConsumed(envelope.getEventId())) {
                synchronize(envelope);
                markConsumed(envelope.getEventId());
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception error) {
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
                sendConfirmed(RabbitMqTopology.RETRY_EXCHANGE,
                        "picture.search-index.retry." + next,
                        objectMapper.writeValueAsString(envelope));
            } else {
                String key = PictureEventType.PICTURE_DELETED.equals(envelope.getEventType())
                        ? PictureEventType.PICTURE_DELETED + ".search" : envelope.getEventType();
                sendConfirmed(RabbitMqTopology.DLX_EXCHANGE, key,
                        objectMapper.writeValueAsString(envelope));
                log.error("搜索索引事件进入 DLQ，eventId={}, pictureId={}", envelope.getEventId(), envelope.getPictureId(), error);
            }
            return true;
        } catch (Exception publishError) {
            log.error("搜索索引重试或死信投递失败，eventId={}", envelope.getEventId(), publishError);
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

    private boolean alreadyConsumed(String eventId) {
        return eventId != null && consumeLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MqConsumeLog>()
                .eq("consumerGroup", GROUP).eq("eventId", eventId)) > 0;
    }

    private void markConsumed(String eventId) {
        if (eventId == null) return;
        transactionTemplate.execute(status -> {
            MqConsumeLog value = new MqConsumeLog();
            value.setConsumerGroup(GROUP);
            value.setEventId(eventId);
            value.setConsumedTime(new Date());
            try { consumeLogMapper.insert(value); } catch (DuplicateKeyException ignored) { }
            return null;
        });
    }

    private long versionOf(Long value) { return value == null || value < 1 ? 1L : value; }
}
