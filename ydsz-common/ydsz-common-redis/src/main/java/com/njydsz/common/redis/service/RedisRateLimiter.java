package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.constant.RedisScriptConstants;
import com.njydsz.common.redis.enums.FailOpenPolicy;
import com.njydsz.common.redis.enums.RedisOperationException;

/**
 * 分布式限流器（基于 Redis + Lua）
 *
 * <p>提供三种工业级限流算法，全部基于 Redis Lua 脚本保证原子性，避免竞态条件：
 *
 * <ul>
 *   <li><b>固定窗口（Fixed Window）</b> - 最简单，按时间窗口计数，窗口切换时存在 2x 突发问题
 *   <li><b>滑动窗口（Sliding Window）</b> - 借助 Hash 分桶计数，内存恒定 O(bucketCount)，限流平滑
 *   <li><b>令牌桶（Token Bucket）</b> - 支持突发流量，按速率持续补充令牌
 * </ul>
 *
 * <p><b>算法对比：</b>
 *
 * <table>
 *   <tr><th>算法</th><th>精度</th><th>突发容忍</th><th>实现复杂度</th><th>适用场景</th></tr>
 *   <tr><td>固定窗口</td><td>低</td><td>2x 突发</td><td>低</td><td>粗粒度限流</td></tr>
 *   <tr><td>滑动窗口</td><td>中</td><td>无</td><td>中</td><td>严格限流</td></tr>
 *   <tr><td>令牌桶</td><td>高</td><td>可配置</td><td>中</td><td>流量整形</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 滑动窗口：每分钟最多 100 次
 * boolean allowed = rateLimiter.tryAcquireSlidingWindow(
 *     "api:user:10086", 100, Duration.ofMinutes(1));
 *
 * // 令牌桶：每秒 10 个令牌，桶容量 50
 * boolean allowed = rateLimiter.tryAcquireTokenBucket(
 *     "api:order", 10, 50, Duration.ofSeconds(1));
 *
 * // 固定窗口：每秒 5 次
 * boolean allowed = rateLimiter.tryAcquireFixedWindow(
 *     "sms:send", 5, Duration.ofSeconds(1));
 * }</pre>
 *
 * <p><b>线程安全：</b>所有方法均为线程安全，底层基于 Redis 单线程 + Lua 原子性保证。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisRateLimiter {

  private final RedisTemplate<String, Object> redisTemplate;
  private final RedisProperties redisProperties;
  private final FailOpenPolicy failOpenPolicy;

  /** 编译后的 Lua 脚本缓存 */
  private final ConcurrentHashMap<String, DefaultRedisScript<?>> scriptCache =
      new ConcurrentHashMap<>();

  public RedisRateLimiter(
      RedisTemplate<String, Object> redisTemplate, RedisProperties redisProperties) {
    this(
        redisTemplate,
        redisProperties,
        redisProperties.getRateLimiter() != null
            ? redisProperties.getRateLimiter().getFailOpenPolicy()
            : FailOpenPolicy.FAIL_CLOSED);
  }

  public RedisRateLimiter(
      RedisTemplate<String, Object> redisTemplate,
      RedisProperties redisProperties,
      FailOpenPolicy failOpenPolicy) {
    this.redisTemplate = redisTemplate;
    this.redisProperties = redisProperties;
    this.failOpenPolicy = failOpenPolicy != null ? failOpenPolicy : FailOpenPolicy.FAIL_CLOSED;
  }

  /**
   * 固定窗口限流
   *
   * <p>在指定时间窗口内最多允许 limit 次请求。窗口切换时可能存在 2x 突发流量。 适用场景：粗粒度限流、对精度要求不高的场景。
   *
   * @param key 限流维度键（如 "api:user:10086"）
   * @param limit 窗口内最大请求数
   * @param window 时间窗口长度
   * @return true=允许，false=拒绝
   */
  public boolean tryAcquireFixedWindow(String key, int limit, Duration window) {
    if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
      return false;
    }
    try {
      String formattedKey = formatKey(key);
      long windowSeconds = Math.max(1, window.toSeconds());
      DefaultRedisScript<?> script =
          getOrCreateScript("fixed_window", RedisScriptConstants.FIXED_WINDOW_LUA, Long.class);
      Object rawResult =
          redisTemplate.execute(
              script, Collections.singletonList(formattedKey), String.valueOf(windowSeconds));
      long count = rawResult instanceof Number ? ((Number) rawResult).longValue() : 0L;
      return count <= limit;
    } catch (Exception e) {
      log.error("【RedisRateLimiter】固定窗口限流异常 | key={} | error={}", key, e);
      return handleException("固定窗口限流", key, e);
    }
  }

  /**
   * 令牌桶限流（自定义周期和请求令牌数）
   *
   * <p>桶容量为 capacity，以每 period 补充 rate 个令牌的速率持续补充。 当桶中有足够令牌时允许请求，否则拒绝。允许突发流量（最多 burst 到 capacity）。
   * 适用场景：流量整形、API 限流。
   *
   * @param key 限流维度键
   * @param rate 周期内补充的令牌数
   * @param capacity 桶容量
   * @param period 补充周期
   * @param permits 本次请求消耗的令牌数
   * @return true=允许，false=拒绝
   */
  public boolean tryAcquireTokenBucket(
      String key, int rate, int capacity, Duration period, int permits) {
    if (key == null
        || rate <= 0
        || capacity <= 0
        || period == null
        || period.isZero()
        || period.isNegative()
        || permits <= 0) {
      return false;
    }
    try {
      String formattedKey = formatKey(key);
      long now = System.currentTimeMillis();
      long periodMs = period.toMillis();
      DefaultRedisScript<?> script =
          getOrCreateScript("token_bucket", RedisScriptConstants.TOKEN_BUCKET_LUA_MS, List.class);
      Object rawResult =
          redisTemplate.execute(
              script,
              Collections.singletonList(formattedKey),
              String.valueOf(capacity),
              String.valueOf(rate),
              String.valueOf(periodMs),
              String.valueOf(now),
              String.valueOf(permits));
      List<Long> result = castToLongList(rawResult);
      if (result.isEmpty()) {
        return false;
      }
      return result.get(0) != null && result.get(0) == 1L;
    } catch (Exception e) {
      log.error("【RedisRateLimiter】令牌桶限流异常 | key={} | error={}", key, e);
      return handleException("令牌桶限流", key, e);
    }
  }

  /**
   * 滑动窗口限流（分桶计数法，内存优化版）
   *
   * <p>使用 Redis Hash 存储时间桶计数，内存占用恒定为 O(bucketCount)，默认 10 个桶。 适用场景：绝大多数限流场景，高并发下比 ZSET 节省 90%+ 内存。
   *
   * @param key 限流维度键
   * @param limit 窗口内最大请求数
   * @param window 时间窗口长度
   * @return true=允许，false=拒绝
   */
  public boolean tryAcquireSlidingWindow(String key, int limit, Duration window) {
    if (key == null || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
      return false;
    }
    try {
      String formattedKey = formatBucketedKey(key);
      long now = System.currentTimeMillis();
      long windowMs = window.toMillis();
      int bucketCount = 10;
      DefaultRedisScript<?> script =
          getOrCreateScript(
              "sliding_window_bucketed",
              RedisScriptConstants.SLIDING_WINDOW_BUCKETED_LUA,
              List.class);
      Object rawResult =
          redisTemplate.execute(
              script,
              Collections.singletonList(formattedKey),
              String.valueOf(now),
              String.valueOf(windowMs),
              String.valueOf(limit),
              String.valueOf(bucketCount));
      List<Long> result = castToLongList(rawResult);
      if (result.isEmpty()) {
        return false;
      }
      return result.get(0) != null && result.get(0) == 1L;
    } catch (Exception e) {
      log.error("【RedisRateLimiter】滑动窗口限流异常 | key={} | error={}", key, e);
      return handleException("滑动窗口限流", key, e);
    }
  }

  /**
   * 重置限流器状态
   *
   * @param key 限流维度键
   */
  public void reset(String key) {
    if (key == null) {
      return;
    }
    try {
      redisTemplate.delete(formatKey(key));
    } catch (Exception e) {
      log.warn("【RedisRateLimiter】重置限流状态失败 | key={} | error={}", key, e);
    }
  }

  /**
   * 查询限流键的剩余生存时间（秒）。
   *
   * <p>用于诊断限流状态，例如查询催办冷却剩余时间。 键不存在时返回 0，查询异常时返回 -1。
   *
   * @param key 限流维度键（已含限流器内部前缀），若传入原始键则自动格式化
   * @return 剩余秒数（0=可操作，>0=冷却中，<0=键不存在或查询失败）
   */
  public long getRemainingSeconds(String key) {
    if (key == null) {
      return -1;
    }
    try {
      String formattedKey = formatKey(key);
      Long ttl = redisTemplate.getExpire(formattedKey);
      return ttl == null ? 0L : ttl;
    } catch (Exception e) {
      log.warn("【RedisRateLimiter】获取剩余时间失败 | key={}", key, e);
      return -1;
    }
  }

  // ==================== 私有方法 ====================

  private String formatKey(String key) {
    String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
    if (prefix == null || prefix.isEmpty()) {
      return "ratelimit:" + key;
    }
    return prefix + ":ratelimit:" + key;
  }

  private String formatBucketedKey(String key) {
    String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
    if (prefix == null || prefix.isEmpty()) {
      return "ratelimit:bucketed:" + key;
    }
    return prefix + ":ratelimit:bucketed:" + key;
  }

  /**
   * 将 Redis 脚本执行结果安全转换为 List<Long>
   *
   * @param rawResult Redis 执行返回的原始对象
   * @return 转换后的 Long 列表，无法转换时返回空列表
   */
  private static List<Long> castToLongList(Object rawResult) {
    if (rawResult instanceof List<?> list) {
      List<Long> result = new ArrayList<>(list.size());
      for (Object item : list) {
        if (item instanceof Number num) {
          result.add(num.longValue());
        } else {
          result.add(0L);
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  private DefaultRedisScript<?> getOrCreateScript(
      String name, String scriptText, Class<?> returnType) {
    return scriptCache.computeIfAbsent(
        name,
        k -> {
          DefaultRedisScript<?> script = new DefaultRedisScript<>();
          script.setScriptText(scriptText);
          ((DefaultRedisScript) script).setResultType(returnType);
          return script;
        });
  }

  /**
   * 处理限流异常
   *
   * <p>根据配置的故障转移策略决定在 Redis 异常时的行为：
   *
   * <ul>
   *   <li>FAIL_OPEN: 放行请求（返回 true），保证可用性
   *   <li>FAIL_CLOSED: 拒绝请求（返回 false），保证安全性
   *   <li>FAIL_THROW: 抛出异常，由业务层处理
   * </ul>
   *
   * @param operation 操作名称（用于日志）
   * @param key 限流键
   * @param e 异常
   * @return true=放行，false=拒绝（仅 FAIL_OPEN/FAIL_CLOSED 策略）
   * @throws RuntimeException 当策略为 FAIL_THROW 时
   */
  private boolean handleException(String operation, String key, Exception e) {
    return switch (failOpenPolicy) {
      case FAIL_OPEN -> {
        log.warn("【RedisRateLimiter】{} 异常，策略=FAIL_OPEN，放行请求 | key={}", operation, key);
        yield true;
      }
      case FAIL_CLOSED -> {
        log.warn("【RedisRateLimiter】{} 异常，策略=FAIL_CLOSED，拒绝请求 | key={}", operation, key);
        yield false;
      }
      case FAIL_THROW -> {
        log.error("【RedisRateLimiter】{} 异常，策略=FAIL_THROW，抛出异常 | key={}", operation, key);
        throw new RedisOperationException(key, "RATE_LIMITER_" + operation, e);
      }
    };
  }
}
