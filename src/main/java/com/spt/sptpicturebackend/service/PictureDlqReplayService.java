package com.spt.sptpicturebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;
import com.spt.sptpicturebackend.messaging.PictureEventType;
import com.spt.sptpicturebackend.messaging.PictureMessageEnvelope;
import com.spt.sptpicturebackend.messaging.RabbitMqTopology;
import com.spt.sptpicturebackend.model.dto.picture.PictureDlqReplayRequest;
import com.spt.sptpicturebackend.model.vo.picture.PictureDlqReplayResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PictureDlqReplayService {
    private static final Set<String> ALLOWED = Set.of(
            RabbitMqTopology.PROCESS_DLQ,
            RabbitMqTopology.BATCH_DLQ,
            RabbitMqTopology.FILE_DELETE_DLQ,
            RabbitMqTopology.SEARCH_INDEX_DLQ,
            RabbitMqTopology.INDEX_MAINTENANCE_DLQ);

    @Resource private RabbitTemplate rabbitTemplate;
    @Resource private ObjectMapper objectMapper;

    public PictureDlqReplayResultVO replay(PictureDlqReplayRequest request) {
        if (request == null || !ALLOWED.contains(request.getQueue())) {
            throw new IllegalArgumentException("不允许重放该死信队列");
        }
        int limit = request.getMaxMessages() == null ? 100 : request.getMaxMessages();
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("maxMessages 必须在 1 到 100 之间");
        PictureDlqReplayResultVO result = new PictureDlqReplayResultVO();
        result.setQueue(request.getQueue());
        result.setRequested(limit);
        int replayed = 0;
        int failed = 0;
        for (int i = 0; i < limit; i++) {
            try {
                Boolean didReplay = rabbitTemplate.execute(channel -> {
                    GetResponse get = channel.basicGet(request.getQueue(), false);
                    if (get == null) return false;
                    PictureMessageEnvelope envelope = objectMapper.readValue(get.getBody(), PictureMessageEnvelope.class);
                    String originalId = envelope.getEventId();
                    Map<String, Object> data = envelope.getData() == null ? new HashMap<>() : new HashMap<>(envelope.getData());
                    data.put("replayedFromEventId", originalId);
                    envelope.setEventId(UUID.randomUUID().toString());
                    envelope.setTraceId(UUID.randomUUID().toString());
                    envelope.setRetryCount(0);
                    envelope.setData(data);
                    String routingKey = routingKey(request.getQueue(), envelope);
                    sendConfirmed(RabbitMqTopology.EVENTS_EXCHANGE, routingKey,
                            objectMapper.writeValueAsString(envelope));
                    channel.basicAck(get.getEnvelope().getDeliveryTag(), false);
                    return true;
                });
                if (!Boolean.TRUE.equals(didReplay)) break;
                replayed++;
            } catch (Exception e) {
                failed++;
                log.error("DLQ 重放失败，queue={}", request.getQueue(), e);
            }
        }
        result.setReplayed(replayed);
        result.setFailed(failed);
        Long remaining = rabbitTemplate.execute(channel -> channel.messageCount(request.getQueue()));
        result.setRemaining(remaining == null ? 0 : Math.toIntExact(Math.min(Integer.MAX_VALUE, remaining)));
        return result;
    }

    private String routingKey(String queue, PictureMessageEnvelope envelope) {
        if (RabbitMqTopology.PROCESS_DLQ.equals(queue)) return RabbitMqTopology.PROCESS_KEY;
        if (RabbitMqTopology.BATCH_DLQ.equals(queue)) {
            return PictureEventType.BATCH_IMPORT_ITEM_REQUESTED.equals(envelope.getEventType())
                    ? RabbitMqTopology.BATCH_ITEM_KEY : RabbitMqTopology.BATCH_KEY;
        }
        if (RabbitMqTopology.FILE_DELETE_DLQ.equals(queue)) {
            return PictureEventType.FILES_DELETE_REQUESTED.equals(envelope.getEventType())
                    ? RabbitMqTopology.FILES_DELETE_KEY : RabbitMqTopology.DELETE_KEY;
        }
        if (RabbitMqTopology.SEARCH_INDEX_DLQ.equals(queue)) {
            return PictureEventType.PICTURE_DELETED.equals(envelope.getEventType())
                    ? RabbitMqTopology.DELETE_KEY : RabbitMqTopology.CHANGED_KEY;
        }
        return PictureEventType.INDEX_RECONCILE_REQUESTED.equals(envelope.getEventType())
                ? RabbitMqTopology.INDEX_RECONCILE_KEY : RabbitMqTopology.INDEX_REBUILD_KEY;
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
