package com.spt.sptpicturebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;
import com.spt.sptpicturebackend.messaging.PictureEventType;
import com.spt.sptpicturebackend.messaging.PictureMessageEnvelope;
import com.spt.sptpicturebackend.messaging.RabbitMqTopology;
import com.spt.sptpicturebackend.messaging.ReliableRabbitPublisher;
import com.spt.sptpicturebackend.model.dto.picture.PictureDlqReplayRequest;
import com.spt.sptpicturebackend.model.vo.picture.PictureDlqReplayResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    @Resource private ReliableRabbitPublisher publisher;

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
                    // 保留原 eventId，使 Inbox 和 COS 对象键继续提供幂等语义。
                    envelope.setTraceId(UUID.randomUUID().toString());
                    envelope.setRetryCount(0);
                    envelope.setData(data);
                    String targetQueue = targetQueue(request.getQueue());
                    publisher.publishToQueue(targetQueue, objectMapper.writeValueAsString(envelope));
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

    private String targetQueue(String dlq) {
        if (RabbitMqTopology.PROCESS_DLQ.equals(dlq)) return RabbitMqTopology.PROCESS_QUEUE;
        if (RabbitMqTopology.BATCH_DLQ.equals(dlq)) return RabbitMqTopology.BATCH_QUEUE;
        if (RabbitMqTopology.FILE_DELETE_DLQ.equals(dlq)) return RabbitMqTopology.FILE_DELETE_QUEUE;
        if (RabbitMqTopology.SEARCH_INDEX_DLQ.equals(dlq)) return RabbitMqTopology.SEARCH_INDEX_QUEUE;
        return RabbitMqTopology.INDEX_MAINTENANCE_QUEUE;
    }
}
