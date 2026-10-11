package com.spt.sptpicturebackend.manager.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.sptpicturebackend.manager.disruptor.PictureEditEventProducer;
import com.spt.sptpicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.spt.sptpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.spt.sptpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.spt.sptpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.spt.sptpicturebackend.model.entity.User;
import com.spt.sptpicturebackend.service.UserService;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** WebSocket handler with local connections and Redis-backed cross-pod state. */
@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler implements MessageListener {
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    @Resource private UserService userService;
    @Resource private PictureEditEventProducer pictureEditEventProducer;
    @Resource private PictureEditRedisLock lock;
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource private com.spt.sptpicturebackend.config.PictureWebSocketProperties properties;
    @Resource private ObjectMapper objectMapper;
    private final String podId = System.getenv().getOrDefault("HOSTNAME", "pod-" + UUID.randomUUID());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.computeIfAbsent(pictureId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        publish(pictureId, info(user.getUserName() + "加入编辑"), null);
        sendCurrentOwner(session, pictureId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        PictureEditRequestMessage request = objectMapper.readValue(message.getPayload(), PictureEditRequestMessage.class);
        Map<String, Object> attributes = session.getAttributes();
        pictureEditEventProducer.publishEvent(request, session,
                (User) attributes.get("user"), (Long) attributes.get("pictureId"));
    }

    public void handleEnterEditMessage(PictureEditRequestMessage request, WebSocketSession session,
                                       User user, Long pictureId) throws Exception {
        if (!session.isOpen()) return;
        if (!lock.acquire(pictureId, session.getId(), user.getId())) {
            Long ownerId = parseUserId(lock.currentOwner(pictureId));
            User owner = ownerId == null ? null : userService.getById(ownerId);
            PictureEditResponseMessage response = error(owner == null
                    ? "图片正在被其他用户编辑" : owner.getUserName() + "正在编辑图片", user);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return;
        }
        publish(pictureId, enter(user), null);
    }

    public void handleEditActionMessage(PictureEditRequestMessage request, WebSocketSession session,
                                        User user, Long pictureId) throws Exception {
        PictureEditActionEnum action = PictureEditActionEnum.getEnumByValue(request.getEditAction());
        if (action == null || !lock.isOwner(pictureId, session.getId(), user.getId())) return;
        PictureEditResponseMessage response = new PictureEditResponseMessage();
        response.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
        response.setMessage(String.format("%s执行%s", user.getUserName(), action.getText()));
        response.setEditAction(request.getEditAction());
        response.setUser(userService.getUserVO(user));
        publish(pictureId, response, session.getId());
    }

    public void handleExitEditMessage(PictureEditRequestMessage request, WebSocketSession session,
                                      User user, Long pictureId) throws Exception {
        if (!lock.release(pictureId, session.getId(), user.getId())) return;
        publish(pictureId, exit(user), null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        User user = (User) session.getAttributes().get("user");
        boolean released = false;
        try {
            if (pictureId != null && user != null) {
                released = lock.release(pictureId, session.getId(), user.getId());
            }
        } catch (Exception error) {
            // Redis may be temporarily unavailable while a socket is closing. The
            // TTL still protects the lock; local state must be cleaned regardless.
            log.warn("释放图片编辑锁失败，pictureId={}, sessionId={}", pictureId, session.getId(), error);
        } finally {
            if (pictureId != null) {
                Set<WebSocketSession> sessions = pictureSessions.get(pictureId);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) pictureSessions.remove(pictureId);
                }
            }
        }
        if (pictureId != null && user != null) {
            try {
                publish(pictureId, info(user.getUserName() + "离开编辑"), null);
                if (released) publish(pictureId, exit(user), null);
            } catch (Exception error) {
                log.debug("发布图片编辑离开事件失败，pictureId={}", pictureId, error);
            }
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            PictureEditRedisEvent event = objectMapper.readValue(message.getBody(), PictureEditRedisEvent.class);
            broadcastLocal(event.getPictureId(), event.getMessage(), event.getExcludeSessionId());
        } catch (Exception ignored) {
            // Pub/Sub 是瞬时通知；当前编辑者状态仍由 Redis 锁权威维护。
        }
    }

    private void publish(Long pictureId, PictureEditResponseMessage response, String excludeSessionId) throws Exception {
        PictureEditRedisEvent event = new PictureEditRedisEvent();
        event.setPictureId(pictureId);
        event.setOriginPodId(podId);
        event.setExcludeSessionId(excludeSessionId);
        event.setMessage(response);
        redisTemplate.convertAndSend(properties.getChannel(), objectMapper.writeValueAsString(event));
    }

    private void broadcastLocal(Long pictureId, PictureEditResponseMessage response, String excludeSessionId) throws Exception {
        Set<WebSocketSession> sessions = pictureSessions.get(pictureId);
        if (sessions == null) return;
        TextMessage text = new TextMessage(objectMapper.writeValueAsString(response));
        for (WebSocketSession current : sessions) {
            if (excludeSessionId != null && excludeSessionId.equals(current.getId())) continue;
            if (current.isOpen()) current.sendMessage(text);
        }
    }

    private void sendCurrentOwner(WebSocketSession session, Long pictureId) throws Exception {
        Long ownerId = parseUserId(lock.currentOwner(pictureId));
        if (ownerId == null) return;
        User owner = userService.getById(ownerId);
        if (owner != null) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(enter(owner))));
    }

    private Long parseUserId(String owner) {
        if (owner == null) return null;
        String[] parts = owner.split("\\|", -1);
        if (parts.length != 3) return null;
        try { return Long.valueOf(parts[2]); } catch (NumberFormatException ignored) { return null; }
    }

    private PictureEditResponseMessage info(String message) {
        PictureEditResponseMessage result = new PictureEditResponseMessage();
        result.setType(PictureEditMessageTypeEnum.INFO.getValue());
        result.setMessage(message);
        return result;
    }

    private PictureEditResponseMessage enter(User user) {
        PictureEditResponseMessage result = new PictureEditResponseMessage();
        result.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        result.setMessage(user.getUserName() + "开始编辑图片");
        result.setUser(userService.getUserVO(user));
        return result;
    }

    private PictureEditResponseMessage exit(User user) {
        PictureEditResponseMessage result = new PictureEditResponseMessage();
        result.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
        result.setMessage(user.getUserName() + "退出编辑图片");
        result.setUser(userService.getUserVO(user));
        return result;
    }

    private PictureEditResponseMessage error(String message, User user) {
        PictureEditResponseMessage result = new PictureEditResponseMessage();
        result.setType(PictureEditMessageTypeEnum.ERROR.getValue());
        result.setMessage(message);
        result.setUser(userService.getUserVO(user));
        return result;
    }
}
