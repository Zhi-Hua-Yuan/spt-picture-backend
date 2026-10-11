package com.spt.sptpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.sptpicturebackend.config.PictureInboxProperties;
import com.spt.sptpicturebackend.mapper.MqConsumeLogMapper;
import com.spt.sptpicturebackend.model.entity.MqConsumeLog;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/** Atomic inbox claim/lease state shared by all Rabbit consumers. */
@Service
public class InboxService {
    public static final String NEW = "NEW";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    @Resource private MqConsumeLogMapper mapper;
    @Resource private PictureInboxProperties properties;
    private final String instanceId = UUID.randomUUID().toString();

    public boolean claim(String group, String eventId) {
        if (eventId == null || eventId.isBlank()) return true;
        MqConsumeLog current = find(group, eventId);
        if (current != null && SUCCEEDED.equals(current.getStatus())) return false;
        if (current == null) {
            MqConsumeLog created = new MqConsumeLog();
            created.setConsumerGroup(group);
            created.setEventId(eventId);
            created.setConsumedTime(new Date());
            created.setStatus(NEW);
            created.setRetryCount(0);
            try { mapper.insert(created); } catch (DuplicateKeyException ignored) { }
        }
        Date now = new Date();
        Calendar until = Calendar.getInstance();
        until.setTimeInMillis(now.getTime() + properties.getLeaseSeconds() * 1000L);
        int changed = mapper.update(null, new UpdateWrapper<MqConsumeLog>()
                .eq("consumerGroup", group)
                .eq("eventId", eventId)
                .ne("status", SUCCEEDED)
                .and(w -> w.isNull("leaseUntil").or().lt("leaseUntil", now))
                .set("status", PROCESSING)
                .set("leaseOwner", instanceId)
                .set("leaseUntil", until.getTime())
                .setSql("retryCount = COALESCE(retryCount, 0) + 1"));
        return changed > 0;
    }

    public boolean isSucceeded(String group, String eventId) {
        MqConsumeLog value = find(group, eventId);
        return value != null && SUCCEEDED.equals(value.getStatus());
    }

    public void markSucceeded(String group, String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        mapper.update(null, new UpdateWrapper<MqConsumeLog>()
                .eq("consumerGroup", group).eq("eventId", eventId)
                .set("status", SUCCEEDED).set("leaseOwner", null).set("leaseUntil", null)
                .set("lastError", null).set("processedTime", new Date()));
    }

    public void markFailed(String group, String eventId, String error) {
        if (eventId == null || eventId.isBlank()) return;
        String message = error == null ? "消费失败" : error.substring(0, Math.min(error.length(), 1900));
        mapper.update(null, new UpdateWrapper<MqConsumeLog>()
                .eq("consumerGroup", group).eq("eventId", eventId)
                .ne("status", SUCCEEDED)
                .set("status", FAILED).set("leaseOwner", null).set("leaseUntil", null)
                .set("lastError", message));
    }

    private MqConsumeLog find(String group, String eventId) {
        if (eventId == null || eventId.isBlank()) return null;
        return mapper.selectOne(new QueryWrapper<MqConsumeLog>()
                .eq("consumerGroup", group).eq("eventId", eventId).last("LIMIT 1"));
    }
}
