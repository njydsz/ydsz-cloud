package com.njydsz.pmis.common.socket.interceptor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.socket.audit.WebSocketAuditService;
import com.njydsz.pmis.common.socket.constant.WebSocketConstants;
import com.njydsz.pmis.common.socket.ratelimit.WebSocketRateLimiter;
import com.njydsz.pmis.common.socket.trace.WebSocketTraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 消息拦截器（P3-1）。
 *
 * <p>在 STOMP 消息发送前执行统一处理：
 * <ul>
 *   <li>CONNECT：注入 traceId 到 Session 属性</li>
 *   <li>SEND：速率限制检查 + 审计日志</li>
 *   <li>SUBSCRIBE：权限校验（预留扩展点）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class StompMessageInterceptor implements ChannelInterceptor {

    private final WebSocketRateLimiter rateLimiter;
    private final WebSocketAuditService auditService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SEND -> handleSend(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            default -> { }
        }

        return message;
    }

    /**
     * 处理 CONNECT 帧：注入 traceId。
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        String traceId = WebSocketTraceContext.generateTraceId();
        WebSocketTraceContext.setTraceId(traceId);
        if (accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put(WebSocketTraceContext.TRACE_ID_KEY, traceId);
        }
        log.debug("[STOMP] CONNECT: traceId={}", traceId);
    }

    /**
     * 处理 SEND 帧：速率限制 + 审计。
     */
    private void handleSend(StompHeaderAccessor accessor) {
        var sessionAttrs = accessor.getSessionAttributes();
        String userId = null;
        if (sessionAttrs != null) {
            userId = (String) sessionAttrs.get(WebSocketConstants.WS_ATTR_USER_ID);
        }

        if (userId != null && rateLimiter != null) {
            if (!rateLimiter.checkUser(userId)) {
                log.warn("[STOMP] 用户消息速率超限: userId={}", userId);
                return;
            }
        }

        if (auditService != null) {
            auditService.auditPush("CLIENT_SEND", userId, null, true, 0, null);
        }
    }

    /**
     * 处理 SUBSCRIBE 帧：预留权限校验扩展点。
     */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (StringUtils.hasText(destination)) {
            log.debug("[STOMP] SUBSCRIBE: destination={}", destination);
        }
    }
}
