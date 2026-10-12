package com.spt.sptpicturebackend.service;

import com.rabbitmq.client.GetResponse;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import com.spt.sptpicturebackend.messaging.ReliableRabbitPublisher;
import com.spt.sptpicturebackend.model.dto.picture.PictureDlqReplayRequest;
import com.spt.sptpicturebackend.model.vo.picture.PictureDlqReplayResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Set;

@Slf4j
@Service
public class PictureDlqReplayService {
    private static final Set<String> ALLOWED = Set.of(
            PictureRoutingV1.MEDIA_PROCESS_DLQ,
            PictureRoutingV1.MEDIA_BATCH_DLQ,
            PictureRoutingV1.MEDIA_FILE_DELETE_DLQ,
            PictureRoutingV1.SEARCH_INDEX_DLQ,
            PictureRoutingV1.SEARCH_MAINTENANCE_DLQ,
            PictureRoutingV1.CORE_MEDIA_RESULT_DLQ);

    @Resource private RabbitTemplate rabbitTemplate;
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
                    String targetQueue = targetQueue(request.getQueue());
                    // 保留原 V1 eventId 和 payload，使 Inbox 与对象键继续提供幂等语义。
                    publisher.publishToQueue(targetQueue, get.getBody());
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
        if (PictureRoutingV1.MEDIA_PROCESS_DLQ.equals(dlq)) return PictureRoutingV1.MEDIA_PROCESS_QUEUE;
        if (PictureRoutingV1.MEDIA_BATCH_DLQ.equals(dlq)) return PictureRoutingV1.MEDIA_BATCH_QUEUE;
        if (PictureRoutingV1.MEDIA_FILE_DELETE_DLQ.equals(dlq)) return PictureRoutingV1.MEDIA_FILE_DELETE_QUEUE;
        if (PictureRoutingV1.SEARCH_INDEX_DLQ.equals(dlq)) return PictureRoutingV1.SEARCH_INDEX_QUEUE;
        if (PictureRoutingV1.SEARCH_MAINTENANCE_DLQ.equals(dlq)) return PictureRoutingV1.SEARCH_MAINTENANCE_QUEUE;
        return PictureRoutingV1.CORE_MEDIA_RESULT_QUEUE;
    }
}
