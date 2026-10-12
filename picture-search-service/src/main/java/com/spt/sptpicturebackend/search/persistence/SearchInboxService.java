package com.spt.sptpicturebackend.search.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.spt.sptpicturebackend.config.PictureInboxProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

@Service
public class SearchInboxService {
    private static final String SUCCEEDED = "SUCCEEDED";
    @Resource private SearchInboxEventMapper mapper;
    @Resource private PictureInboxProperties properties;
    private final String instanceId = UUID.randomUUID().toString();

    public boolean claim(String group, String eventId) {
        if (eventId == null || eventId.isBlank()) return true;
        SearchInboxEvent current = find(group, eventId);
        if (current != null && SUCCEEDED.equals(current.getStatus())) return false;
        if (current == null) {
            SearchInboxEvent row = new SearchInboxEvent(); row.setConsumerGroup(group); row.setEventId(eventId);
            row.setStatus("NEW"); row.setRetryCount(0); row.setCreateTime(new Date()); row.setUpdateTime(new Date());
            try { mapper.insert(row); } catch (DuplicateKeyException ignored) { }
        }
        Date now = new Date(); Calendar until = Calendar.getInstance();
        until.setTimeInMillis(now.getTime() + properties.getLeaseSeconds() * 1000L);
        return mapper.update(null, new UpdateWrapper<SearchInboxEvent>().eq("consumer_group", group).eq("event_id", eventId)
                .ne("status", SUCCEEDED).and(w -> w.isNull("lease_until").or().lt("lease_until", now))
                .set("status", "PROCESSING").set("lease_owner", instanceId).set("lease_until", until.getTime())
                .setSql("retry_count = COALESCE(retry_count, 0) + 1")) > 0;
    }

    public boolean isSucceeded(String group, String eventId) {
        SearchInboxEvent row = find(group, eventId); return row != null && SUCCEEDED.equals(row.getStatus());
    }

    public void markSucceeded(String group, String eventId) {
        mapper.update(null, new UpdateWrapper<SearchInboxEvent>().eq("consumer_group", group).eq("event_id", eventId)
                .set("status", SUCCEEDED).set("lease_owner", null).set("lease_until", null)
                .set("last_error", null).set("processed_time", new Date()));
    }

    public void markFailed(String group, String eventId, String error) {
        String message = error == null ? "消费失败" : error.substring(0, Math.min(error.length(), 1900));
        mapper.update(null, new UpdateWrapper<SearchInboxEvent>().eq("consumer_group", group).eq("event_id", eventId)
                .ne("status", SUCCEEDED).set("status", "FAILED").set("lease_owner", null).set("lease_until", null)
                .set("last_error", message));
    }

    private SearchInboxEvent find(String group, String eventId) {
        return mapper.selectOne(new QueryWrapper<SearchInboxEvent>().eq("consumer_group", group).eq("event_id", eventId).last("LIMIT 1"));
    }
}
