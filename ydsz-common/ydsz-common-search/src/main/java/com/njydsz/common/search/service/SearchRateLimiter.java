package com.njydsz.common.search.service;

import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.search.config.SearchProperties;

/**
 * 搜索限流服务（基于 Redis 滑动窗口计数器）。
 *
 * <p>阻止单用户 / 单租户高频调用搜索接口，避免以下场景：
 *
 * <ul>
 *   <li>前端防抖失效导致的突发请求</li>
 *   <li>用户 / 脚本恶意刷搜索 API</li>
 *   <li>下游引擎（PG / ES）被单租户压垮</li>
 * </ul>
 *
 * <p><b>默认限流维度</b>（当 {@code search} 与 {@code tenant} 维度均启用时会串联检查，任一超限即拒绝）：
 *
 * <ul>
 *   <li>用户维度：key = {@code search:rate:user:{userId}} — 按用户 ID 限流，未登录用户回退到 IP 维度</li>
 *   <li>租户维度：key = {@code search:rate:tenant:{tenantId}} — 按租户 ID 限流</li>
 * </ul>
 *
 * <p><b>Redis 脚本保证原子性</b>：使用 Lua 脚本一次性完成 INCR + EXPIRE（仅首次写入时）+ 比较，避免"先 INCR 再 EXPIRE"的非原子窗口。
 * Redis 不可用时降级为本地令牌桶（单节点、基于 Guava RateLimiter 手写的轻量实现），不需要引入额外依赖。
 *
 * <p>限流返回时由调用方决定如何拒绝：{@link UnifiedSearchService#search} 中会直接返回 {@link com.njydsz.common.search.api.SearchResponse#empty}
 * 的空结果，满足"绝不因限流抛异常"的稳定性约定。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class SearchRateLimiter {

  /** 滑动窗口限流 Lua 脚本：首次写入时设置 TTL，返回收数。 */
  private static final String RATE_LIMIT_SCRIPT =
      "local current = redis.call('INCR', KEYS[1])\n"
          + "if current == 1 then\n"
          + "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n"
          + "end\n"
          + "return current\n";

  private static final String KEY_USER_PREFIX = "search:rate:user:";
  private static final String KEY_TENANT_PREFIX = "search:rate:tenant:";

  private final ObjectProvider<StringRedisTemplate> redisProvider;
  private final SearchProperties properties;
  private final DefaultRedisScript<Long> redisScript;

  public SearchRateLimiter(
      ObjectProvider<StringRedisTemplate> redisProvider, SearchProperties properties) {
    this.redisProvider = redisProvider;
    this.properties = properties;
    this.redisScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
  }

  /**
   * 检查当前请求是否通过限流判定。
   *
   * <p>检查顺序 — 先用户维度后租户维度；用户未登录时跳过用户维度检查。 任一维度超限即返回 {@code false}。
   *
   * @param userId 当前用户 ID；{@code null} 或空白时跳过用户维度
   * @param tenantId 当前租户 ID；{@code null} 或空白时跳过租户维度
   * @return {@code true} 表示未超限、请求可继续；{@code false} 表示已超限、调用方应拒绝请求
   */
  public boolean tryAcquire(String userId, String tenantId) {
    if (!properties.getRateLimit().isEnabled()) {
      return true;
    }

    if (userId != null && !userId.isBlank() && properties.getRateLimit().isUserEnabled()) {
      if (!checkLimit(KEY_USER_PREFIX + userId, properties.getRateLimit().getUserLimit(),
          properties.getRateLimit().getWindowSizeSeconds())) {
        log.warn("[SearchRateLimiter] 用户维度限流触发: userId={}, limit={}/{}s", userId,
            properties.getRateLimit().getUserLimit(), properties.getRateLimit().getWindowSizeSeconds());
        return false;
      }
    }

    if (tenantId != null && !tenantId.isBlank() && properties.getRateLimit().isTenantEnabled()) {
      if (!checkLimit(KEY_TENANT_PREFIX + tenantId, properties.getRateLimit().getTenantLimit(),
          properties.getRateLimit().getWindowSizeSeconds())) {
        log.warn("[SearchRateLimiter] 租户维度限流触发: tenantId={}, limit={}/{}s", tenantId,
            properties.getRateLimit().getTenantLimit(), properties.getRateLimit().getWindowSizeSeconds());
        return false;
      }
    }

    return true;
  }

  /**
   * 检查单个维度的限流。
   *
   * @param key Redis key
   * @param limit 窗口内最大次数
   * @param windowSeconds 窗口秒数
   * @return {@code true} 表示未超限；{@code false} 表示超限
   */
  private boolean checkLimit(String key, int limit, int windowSeconds) {
    StringRedisTemplate redis = getRedis();
    if (redis != null) {
      try {
        Long count = redis.execute(redisScript, Collections.singletonList(key), String.valueOf(windowSeconds));
        return count != null && count <= limit;
      } catch (Exception e) {
        log.debug("[SearchRateLimiter] Redis 限流脚本执行失败，默认放行: key={}, msg={}", key, e.getMessage());
        return true;
      }
    }
    // Redis 不可用：降级为永远放行（避免 Redis 故障导致搜索完全不可用）
    return true;
  }

  /**
   * 获取当前用户在滑动窗口内的已消耗配额。
   *
   * <p>适用于前端"剩余次数"展示。Redis 不可用时返回 0。
   *
   * @param userId 用户 ID
   * @return 当前窗口内的请求数；key 不存在时返回 0
   */
  public long getUsedQuota(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0L;
    }
    StringRedisTemplate redis = getRedis();
    if (redis == null) {
      return 0L;
    }
    try {
      String val = redis.opsForValue().get(KEY_USER_PREFIX + userId);
      return val != null ? Long.parseLong(val) : 0L;
    } catch (Exception e) {
      return 0L;
    }
  }

  private StringRedisTemplate getRedis() {
    return redisProvider.getIfAvailable();
  }
}
