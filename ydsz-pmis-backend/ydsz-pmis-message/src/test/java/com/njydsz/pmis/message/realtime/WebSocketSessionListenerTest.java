package com.njydsz.pmis.message.realtime;

import com.njydsz.pmis.message.constant.MessageConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4: WebSocketSessionListener 单元测试。
 *
 * <p>验证连接事件标记上线 + 补偿离线消息、断开事件标记下线、属性缺失降级。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class WebSocketSessionListenerTest {

    private OnlineUserService onlineUserService;
    private OfflineMessageService offlineMessageService;
    private SimpMessagingTemplate messagingTemplate;
    private WebSocketSessionListener listener;

    @BeforeEach
    void setUp() {
        onlineUserService = mock(OnlineUserService.class);
        offlineMessageService = mock(OfflineMessageService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        listener = new WebSocketSessionListener(onlineUserService, offlineMessageService, messagingTemplate);
    }

    /**
     * 构造连接事件：使用 StompCommand.CONNECT 避免歧义。
     */
    private SessionConnectedEvent createConnectedEvent(String userId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (userId != null) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(MessageConstants.WS_ATTR_USER_ID, userId);
            accessor.setSessionAttributes(attrs);
        }
        accessor.setSessionId(sessionId);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionConnectedEvent(this, msg);
    }

    /**
     * 构造断开事件：使用 StompCommand.DISCONNECT 避免歧义。
     */
    private SessionDisconnectEvent createDisconnectEvent(String userId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        if (userId != null) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(MessageConstants.WS_ATTR_USER_ID, userId);
            accessor.setSessionAttributes(attrs);
        }
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, msg, sessionId, null);
    }

    @Test
    void handleSessionConnected_marksOnlineAndDrainsOffline() {
        SessionConnectedEvent event = createConnectedEvent("u1", "sess-1");
        when(offlineMessageService.drainOffline("u1"))
                .thenReturn(List.of("msg1", "msg2"));

        listener.handleSessionConnected(event);

        verify(onlineUserService).markOnline("u1", "sess-1");
        verify(offlineMessageService).drainOffline("u1");
        // 两条离线消息应被推送
        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/user/u1/notifications"), any(String.class));
    }

    @Test
    void handleSessionConnected_skipsWhenNoSessionAttributes() {
        // 构造无 sessionAttributes 的事件
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("sess-2");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionConnectedEvent event = new SessionConnectedEvent(this, msg);

        listener.handleSessionConnected(event);

        verify(onlineUserService, never()).markOnline(any(), any());
        verify(offlineMessageService, never()).drainOffline(any());
    }

    @Test
    void handleSessionConnected_skipsWhenUserIdMissing() {
        // sessionAttributes 存在但无 userId
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> attrs = new HashMap<>();
        accessor.setSessionAttributes(attrs);
        accessor.setSessionId("sess-3");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionConnectedEvent event = new SessionConnectedEvent(this, msg);

        listener.handleSessionConnected(event);

        verify(onlineUserService, never()).markOnline(any(), any());
    }

    @Test
    void handleSessionConnected_noOfflineMessagesDoesNotPush() {
        SessionConnectedEvent event = createConnectedEvent("u2", "sess-4");
        when(offlineMessageService.drainOffline("u2")).thenReturn(List.of());

        listener.handleSessionConnected(event);

        verify(onlineUserService).markOnline("u2", "sess-4");
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void handleSessionDisconnect_marksOffline() {
        SessionDisconnectEvent event = createDisconnectEvent("u1", "sess-1");

        listener.handleSessionDisconnect(event);

        verify(onlineUserService).markOffline("u1", "sess-1");
    }

    @Test
    void handleSessionDisconnect_skipsWhenNoAttributes() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, msg, "sess-1", null);

        listener.handleSessionDisconnect(event);

        verify(onlineUserService, never()).markOffline(any(), any());
    }

    @Test
    void handleSessionDisconnect_skipsWhenUserIdMissing() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        Map<String, Object> attrs = new HashMap<>();
        accessor.setSessionAttributes(attrs);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, msg, "sess-1", null);

        listener.handleSessionDisconnect(event);

        verify(onlineUserService, never()).markOffline(any(), any());
    }

    @Test
    void handleSessionConnected_offlinePushFailureDoesNotThrow() {
        SessionConnectedEvent event = createConnectedEvent("u3", "sess-5");
        when(offlineMessageService.drainOffline("u3"))
                .thenReturn(List.of("msg1"));
        // 模拟推送异常
        org.mockito.Mockito.doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate)
                .convertAndSend(any(String.class), any(String.class));

        // 不应抛异常
        listener.handleSessionConnected(event);

        verify(onlineUserService).markOnline("u3", "sess-5");
    }
}
