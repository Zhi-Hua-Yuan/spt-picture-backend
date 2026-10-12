package com.spt.sptpicturebackend.manager.websocket;

import com.spt.sptpicturebackend.config.PictureWebSocketProperties;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Redis authoritative edit lock. All mutations compare the complete owner value in Lua. */
@Component
public class PictureEditRedisLock {
    private static final String ACQUIRE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 0 then
              redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2])
              return 1
            end
            return 0
            """;
    private static final String RENEW_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """;
    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource private PictureWebSocketProperties properties;

    private final String podId = System.getenv().getOrDefault("HOSTNAME", "pod-" + UUID.randomUUID());
    private final Map<String, HeldLock> heldLocks = new ConcurrentHashMap<>();

    public String ownerFor(Long pictureId, String sessionId, Long userId) {
        return podId + "|" + sessionId + "|" + userId;
    }

    public boolean acquire(Long pictureId, String sessionId, Long userId) {
        String owner = ownerFor(pictureId, sessionId, userId);
        Long result = redisTemplate.execute(new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class),
                java.util.List.of(key(pictureId)), owner, String.valueOf(properties.getLockTtlMs()));
        if (Long.valueOf(1L).equals(result)) {
            heldLocks.put(sessionId, new HeldLock(pictureId, owner));
            return true;
        }
        return false;
    }

    public boolean isOwner(Long pictureId, String sessionId, Long userId) {
        return ownerFor(pictureId, sessionId, userId).equals(
                redisTemplate.opsForValue().get(key(pictureId)));
    }

    public boolean release(Long pictureId, String sessionId, Long userId) {
        String owner = ownerFor(pictureId, sessionId, userId);
        Long result = redisTemplate.execute(new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class),
                java.util.List.of(key(pictureId)), owner);
        heldLocks.remove(sessionId);
        return Long.valueOf(1L).equals(result);
    }

    public String currentOwner(Long pictureId) {
        return redisTemplate.opsForValue().get(key(pictureId));
    }

    @Scheduled(fixedDelayString = "${picture.websocket.lock-renew-ms:10000}")
    public void renewHeldLocks() {
        for (HeldLock held : new ArrayList<>(heldLocks.values())) {
            Long result = redisTemplate.execute(new DefaultRedisScript<>(RENEW_SCRIPT, Long.class),
                    java.util.List.of(key(held.pictureId)), held.owner,
                    String.valueOf(properties.getLockTtlMs()));
            if (!Long.valueOf(1L).equals(result)) {
                heldLocks.values().removeIf(value -> value == held);
            }
        }
    }

    private String key(Long pictureId) { return "picture:edit:lock:" + pictureId; }

    @Data
    private static class HeldLock {
        private final Long pictureId;
        private final String owner;
    }
}
