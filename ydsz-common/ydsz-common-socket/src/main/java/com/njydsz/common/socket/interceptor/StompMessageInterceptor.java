package com.njydsz.common.socket.interceptor;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.StringUtils;

import com.njydsz.common.socket.acl.TopicAclPolicy;
import com.njydsz.common.socket.audit.WebSocketAuditService;
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
 *   <li>SUBSCRIBE：{@link TopicAclPolicy} 订阅鉴权（FEAT-003），ACL 拒绝时向客户端发送 ERROR 帧
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class StompMessageInterceptor implements ChannelInterceptor {

  private final WebSocketRateLimiter rateLimiter;
  private final WebSocketAuditService auditService;
  private final WebSocketMessageDispatcher messageDispatcher;
  private final TopicAclPolicy topicAclPolicy;

  /**
   * 构造拦截器（显式全参）。
   *
   * @param rateLimiter 速率限制器
   * @param auditService 审计日志服务
   * @param messageDispatcher 消息分发器
   * @param topicAclPolicy 订阅 ACL 策略，可选依赖；为 null 时 SUBSCRIBE 不做 ACL 校验
   */
  public StompMessageInterceptor(
      WebSocketRateLimiter rateLimiter,
      WebSocketAuditService auditService,
      WebSocketMessageDispatcher messageDispatcher,
      TopicAclPolicy topicAclPolicy) {
    this.rateLimiter = rateLimiter;
    this.auditService = auditService;
    this.messageDispatcher = messageDispatcher;
    this.topicAclPolicy = topicAclPolicy;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) throws MessagingException {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    StompCommand command = accessor.getCommand();

    if (command == null) {
      return message;
    }

    switch (command) {
      case CONNECT -> handleConnect(accessor);
      case SEND -> handleSend(accessor);
      case SUBSCRIBE -> handleSubscribe(accessor);
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
        Object payload = message.getPayload();
        String payloadJson = payload instanceof byte[] bytes
            ? new String(bytes, StandardCharsets.UTF_8)
            : String.valueOf(payload);
        boolean dispatched = messageDispatcher.dispatch(action, payloadJson, sessionAttrs);
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

  /**
   * 处理 SUBSCRIBE 帧：订阅主题 ACL 校验（FEAT-003）。
   *
   * <p>当 {@link TopicAclPolicy} 拒绝时，抛出 {@link MessagingException} 终止订阅流程，
   * 框架将向客户端发送 ERROR 帧并关闭该订阅。ACL 未配置时放行（保持与旧版兼容）；
   * 用户私有前缀（/user/{privateUserId}/...）和 管理员保护区（/admin/**）由策略实现校验。
   *
   * @param accessor STOMP 帧头
   * @throws MessagingException 当 ACL 校验失败时抛出，框架向客户端投递 ERROR 帧
   */
  private void handleSubscribe(StompHeaderAccessor accessor) throws MessagingException {
    if (topicAclPolicy == null) {
      return;
    }
    var sessionAttrs = accessor.getSessionAttributes();
    String userId = null;
    if (sessionAttrs != null) {
      userId = (String) sessionAttrs.get(WebSocketConstants.WS_ATTR_USER_ID);
    }
    if (userId == null) {
      log.warn("[STOMP] SUBSCRIBE 拒绝: 未认证用户尝试订阅, destination={}", accessor.getDestination());
      throw new MessagingException(
          "SUBSCRIBE denied: no authenticated user for destination " + accessor.getDestination());
    }
    if (!topicAclPolicy.allowSubscription(userId, accessor.getDestination(), sessionAttrs)) {
      log.warn(
          "[STOMP] SUBSCRIBE 拒绝(ACL): userId={}, destination={}",
          userId,
          accessor.getDestination());
      throw new MessagingException(
          "SUBSCRIBE denied by ACL policy for destination " + accessor.getDestination());
    }
  }
}
