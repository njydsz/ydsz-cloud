package com.njydsz.common.socket.filter;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.push.PushContext;

/**
 * 消息幂等去重拦截器（FEAT-001）。
 *
 * <p>基于 {@code messageId} + Redis SETNX 实现服务端消息去重，防止同一条消息因重试或
 * 客户端重连时重复推送。
 *
 * <p>去重逻辑：
 *
 * <ul>
 *   <li>若 {@code messageId} 为 null 或空，放行（无法判断去重键）
 *   <li>以 Redis key {@code ydsz:ws:dedup:{messageId}} 调用 SETNX, TTL = 24h
 *   <li>SETNX 返回 true（首次）→ 放行
 *   <li>SETNX 返回 false（重复）→ 拦截
 * </ul>
 *
 * <p>降级策略：Redis 不可用或异常时放行所有消息（宁可重复也不丢消息）。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class WebSocketDedupInterceptor implements MessageFilter {

  /** 去重记录默认 TTL：24 小时。覆盖绝大多数重试窗口 */
  private static final Duration DEFAULT_DEDUP_TTL = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;
  private final boolean isEnabled;

  /**
   * 构造消息幂等去重拦截器。
   *
   * @param redisTemplate Redis 模板，为 null 时整体不启用去重（降级放行）
   */
  public WebSocketDedupInterceptor(StringRedisTemplate redisTemplate) {
    this(redisTemplate, true);
  }

  /**
   * 构造消息幂等去重拦截器（显式控制开关）。
   *
   * @param redisTemplate Redis 模板
   * @param isEnabled 是否启用去重
   */
  public WebSocketDedupInterceptor(StringRedisTemplate redisTemplate, boolean isEnabled) {
    this.redisTemplate = redisTemplate;
    this.isEnabled = isEnabled && redisTemplate != null;
  }

  /**
   * 判断消息是否应该发送（基于推送上下文）。
   *
   * <p>覆盖 PushContext 版入口以直接获取 messageId，避免走 payload.toString() 降级路径。
   *
   * @param context 推送上下文
   * @return true 表示允许发送（首次出现），false 表示重复拦截
   */
  @Override
  public boolean shouldSend(PushContext context) {
    if (!isEnabled) {
      return true;
    }
    String messageId = context.messageId();
    if (messageId == null || messageId.isEmpty()) {
      // messageId 为空时无法去重，放行
      return true;
    }
    // 高优先级消息跳过去重（如告警类消息可能被多次触发但有独立 messageId）
    if ("CRITICAL".equals(context.priority())) {
      return true;
    }
    String dedupKey = buildDedupKey(messageId);
    try {
      Boolean isNew =
          redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEFAULT_DEDUP_TTL);
      boolean allowed = Boolean.TRUE.equals(isNew);
      if (!allowed) {
        log.info(
            "[WS-Dedup] 重复消息已拦截: messageId={}, userId={}, pushType={}",
            messageId,
            context.userId(),
            context.pushType());
      }
      return allowed;
    } catch (Exception e) {
      log.warn("[WS-Dedup] Redis 去重查询异常, 降级放行: messageId={}, err={}", messageId, e.getMessage());
      return true;
    }
  }

  /**
   * 传统签名降级实现（基于 payload 的 hashCode 生成去重键 —— 可能冲突，不推荐）。
   *
   * <p>为兼容现有过滤器 SPI 签名而保留，但实际应使用 {@link #shouldSend(PushContext)}。
   *
   * @param userId 目标用户 ID
   * @param pushType 推送类型
   * @param payload 消息内容
   * @return true 表示允许发送
   */
  @Override
  public boolean shouldSend(String userId, String pushType, String payload) {
    return true;
  }

  /**
   * 获取过滤器名称。
   *
   * @return 过滤器名称
   */
  @Override
  public String getName() {
    return "WebSocketDedupInterceptor";
  }

  /**
   * 构建去重 Redis key。
   *
   * @param messageId 消息 ID
   * @return Redis key 字符串
   */
  private String buildDedupKey(String messageId) {
    return WebSocketConstants.WS_DEDUP_KEY_PREFIX + messageId;
  }

  /**
   * 是否启用去重。
   *
   * @return true 表示去重功能已启用
   */
  public boolean isEnabled() {
    return isEnabled;
  }
}
