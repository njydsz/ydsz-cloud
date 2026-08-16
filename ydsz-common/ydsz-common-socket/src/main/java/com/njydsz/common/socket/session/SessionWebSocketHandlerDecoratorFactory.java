package com.njydsz.common.socket.session;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

/**
 * WebSocket Handler 装饰器工厂，用于捕获 Session 注册到 {@link LocalSessionRegistry}。
 *
 * <p>在连接建立时从 handshake attributes 中提取 userId 并注册 Session，
 * 在连接关闭时注销 Session。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SessionWebSocketHandlerDecoratorFactory implements WebSocketHandlerDecoratorFactory {

    private final LocalSessionRegistry sessionRegistry;

    public SessionWebSocketHandlerDecoratorFactory(LocalSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new SessionTrackingDecorator(handler, sessionRegistry);
    }

    /**
     * Session 追踪装饰器。
     */
    private static class SessionTrackingDecorator extends WebSocketHandlerDecorator {

        private final LocalSessionRegistry sessionRegistry;

        SessionTrackingDecorator(WebSocketHandler delegate, LocalSessionRegistry sessionRegistry) {
            super(delegate);
            this.sessionRegistry = sessionRegistry;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            String userId = extractUserId(session);
            if (userId != null) {
                sessionRegistry.register(userId, session.getId(), session);
            }
            super.afterConnectionEstablished(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            String userId = extractUserId(session);
            if (userId != null) {
                sessionRegistry.unregister(userId, session.getId());
            }
            super.afterConnectionClosed(session, closeStatus);
        }

        private String extractUserId(WebSocketSession session) {
            Principal principal = session.getPrincipal();
            if (principal != null) {
                return principal.getName();
            }
            Map<String, Object> attributes = session.getAttributes();
            Object userId = attributes.get("userId");
            return userId != null ? userId.toString() : null;
        }
    }
}
