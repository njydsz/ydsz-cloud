package com.njydsz.common.socket.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.StringUtils;

import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.handler.WebSocketMessageDispatcher;
import com.njydsz.common.socket.ratelimit.WebSocketRateLimiter;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

/**
 * STOMP 消息拦截器（P3-1）。
 *
 * <p>在 STOMP 消息发送前执行统一处理：
 *
 * <ul>
 *   <li>CONNECT：注入 traceId 到 Session 属性
 *   <li>SEND：速率限制检查 + {@link WebSocketMessageDispatcher} 路由分发（FEAT-002）+ 审计日志
 *   <li>SUBSCRIBE：权限校验扩展点（{@code TopicAclPolicy}，FEAT-003）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class StompMessageInterceptor implements ChannelInterceptor {

  private final WebSocketRateLimiter rateLimiter;
  private final WebSocketAuditService auditService;
  private final WebSocketMessageDispatcher messageDispatcher;

  @SuppressWarnings("java:S3776")
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
      default -> { /* 其他命令不处理 */ }
    }

    return message;
  }

  /** 处理 CONNECT 帧：注入 traceId。 */
  private void handleConnect(StompHeaderAccessor accessor) {
    String traceId = WebSocketTraceContext.generateTraceId();
    WebSocketTraceContext.setTraceId(traceId);
    if (accessor.getSessionAttributes() != null) {
      accessor.getSessionAttributes().put(WebSocketTraceContext.TRACE_ID_KEY, traceId);
    }
    log.debug("[STOMP] CONNECT: traceId={}", traceId);
  }

  /** 处理 SEND 帧：速率限制 + C→S 路由分发（FEAT-002）+ 审计。 */
  private void handleSend(StompHeaderAccessor accessor) {
    var sessionAttrs = accessor.getSessionAttributes();
    String userId = null;
    if (sessionAttrs != null) {
      userId = (String) sessionAttrs.get(WebSocketConstants.WS_ATTR_USER_ID);
    }

    // ① 速率限制
    if (userId != null && rateLimiter != null) {
      if (!rateLimiter.checkUser(userId)) {
        log.warn("[STOMP] 用户消息速率超限: userId={}", userId);
        return;
      }
    }

    // ② C→S 消息路由分发（FEAT-002）
    if (messageDispatcher != null) {
      String action = accessor.getFirstNativeHeader(WebSocketMessageDispatcher.ACTION_HEADER);
      if (StringUtils.hasText(action)) {
        Object payload = accessor.getMessageHeaders().getOrDefault("simpPayload", null);
        boolean dispatched =
            messageDispatcher.dispatch(action, payload != null ? payload.toString() : "", sessionAttrs);
        if (dispatched) {
          // 已分发的消息无需再审计（由处理器内部处理）
          return;
        }
      }
    }

    // ③ 审计
    if (auditService != null) {
      auditService.auditPush("CLIENT_SEND", userId, null, true, 0, null);
    }
  }
}
