package com.njydsz.system.server.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 跨实例缓存失效消息发布者（P1-7 改进）。
 *
 * <p>通过 Redis Pub/Sub 发布缓存失效消息，各实例订阅该频道后清除本地缓存，实现跨实例实时一致性（替代原有 TTL 自然过期的最终一致方案）。
 *
 * <p><b>频道设计：</b>{@code ydsz:system:cache-invalidation}，消息格式：{@code cacheName:key}（与 {@link
 * CacheKeyBuilder} 生成的键格式一致）。
 *
 * <p><b>容错：</b>Redis 不可用时降级为仅本地失效（原有 {@link
 * com.njydsz.system.server.listener.CrossModuleEventListener} 兜底），不影响核心写入流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

  /** 缓存失效 Pub/Sub 频道 */
  public static final String CHANNEL = "ydsz:system:cache-invalidation";

  private final StringRedisTemplate redisTemplate;

  /**
   * 发布缓存失效消息（指定缓存名和键）。
   *
   * <p>消息格式：{@code cacheName:key}（冒号分隔）。发送失败时仅打日志，不阻塞调用方。
   *
   * @param cacheName 缓存名称
   * @param key 缓存键
   */
  public void publishEviction(String cacheName, String key) {
    try {
      String message = cacheName + ":" + key;
      redisTemplate.convertAndSend(CHANNEL, message);
      log.debug("[CacheInvalidationPublished] 发布缓存失效消息: {}", message);
    } catch (Exception e) {
      // Redis 不可用时降级（核心写入流程不受影响）
      log.warn("[CacheInvalidationPublisher] 发布缓存失效消息失败（将降级为本地失效）: cacheName={}, key={}", cacheName, key, e);
    }
  }
}
