package com.njydsz.common.safe.ratelimit.cluster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * Redis 集群限流器（基于 Lua 脚本的令牌桶 / 滑动窗口）
 *
 * <p><b>工作原理：</b>
 *
 * <ul>
 *   <li>每个资源在 Redis 中维护一个 hash key，存放 {tokens, lastRefill}
 *   <li>通过 Lua 脚本原子完成「填充 → 扣减 → 返回」
 *   <li>本地未启用时降级为「纯 Redis 限流」
 * </ul>
 *
 * <p><b>Lua 脚本原子性优势：</b>避免「先 GET 再 SET」期间的竞态。
 *
 * <p><b>降级策略：</b>当 {@link StringRedisTemplate} 不可用时（如 Redis 故障）， 按 {@code fallbackOnError}
 * 配置决定：PASS（放行，默认）/ BLOCK（拒绝）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisClusterRateLimiter implements ClusterRateLimiter {

  /**
   * 令牌桶 Lua 脚本（KEYS[1]=bucket, ARGV[1]=rate, ARGV[2]=capacity, ARGV[3]=now, ARGV[4]=cost）
   *
   * <pre>
   * local key = KEYS[1]
   * local rate = tonumber(ARGV[1])         -- 每秒填充速率
   * local capacity = tonumber(ARGV[2])     -- 桶容量
   * local now = tonumber(ARGV[3])          -- 当前时间（毫秒）
   * local cost = tonumber(ARGV[4])         -- 消耗令牌数（默认 1）
   * local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
   * local tokens = tonumber(data[1]) or capacity
   * local lastRefill = tonumber(data[2]) or now
   * local elapsed = math.max(0, now - lastRefill)
   * tokens = math.min(capacity, tokens + (elapsed / 1000.0) * rate)
   * if tokens >= cost then
   *     tokens = tokens - cost
   *     redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
   *     redis.call('PEXPIRE', key, 60000)
   *     return {1, tokens}
   * else
   *     redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
   *     redis.call('PEXPIRE', key, 60000)
   *     return {0, tokens}
   * end
   * </pre>
   */
  public static final String TOKEN_BUCKET_LUA =
      ""
          + "local key = KEYS[1]\n"
          + "local rate = tonumber(ARGV[1])\n"
          + "local capacity = tonumber(ARGV[2])\n"
          + "local now = tonumber(ARGV[3])\n"
          + "local cost = tonumber(ARGV[4])\n"
          + "local data = redis.call('HMGET', key, 'tokens', 'lastRefill')\n"
          + "local tokens = tonumber(data[1])\n"
          + "if tokens == nil then tokens = capacity end\n"
          + "local lastRefill = tonumber(data[2])\n"
          + "if lastRefill == nil then lastRefill = now end\n"
          + "local elapsed = now - lastRefill\n"
          + "if elapsed < 0 then elapsed = 0 end\n"
          + "tokens = math.min(capacity, tokens + (elapsed / 1000.0) * rate)\n"
          + "if tokens >= cost then\n"
          + "  tokens = tokens - cost\n"
          + "  redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)\n"
          + "  redis.call('PEXPIRE', key, 60000)\n"
          + "  return {1, tokens}\n"
          + "else\n"
          + "  redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)\n"
          + "  redis.call('PEXPIRE', key, 60000)\n"
          + "  return {0, tokens}\n"
          + "end";

  /** Redis Key 前缀（默认值，可被 RateLimitProperties.clusterKeyPrefix 覆盖） */
  public static final String DEFAULT_KEY_PREFIX = "ydsz:ratelimit:";

  /** 令牌桶 RedisScript（返回 List：[passed, remaining]） */
  private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT = buildScript(TOKEN_BUCKET_LUA);

  /** Redis 客户端（可能为 null，表示 Redis 不可用） */
  private final StringRedisTemplate redisTemplate;

  /** Redis Key 前缀 */
  private final String keyPrefix;

  /** Redis 不可用时降级策略：PASS（放行）/ BLOCK（拒绝） */
  private final String fallbackOnError;

  public RedisClusterRateLimiter(
      StringRedisTemplate redisTemplate, String keyPrefix, String fallbackOnError) {
    this.redisTemplate = redisTemplate;
    this.keyPrefix = (keyPrefix == null || keyPrefix.isEmpty()) ? DEFAULT_KEY_PREFIX : keyPrefix;
    this.fallbackOnError =
        (fallbackOnError == null || fallbackOnError.isEmpty())
            ? "PASS"
            : fallbackOnError.toUpperCase();
    if (redisTemplate == null) {
      log.warn(
          "RedisClusterRateLimiter initialized without StringRedisTemplate; cluster rate limit will fall back to {}",
          this.fallbackOnError);
    } else {
      log.info(
          "RedisClusterRateLimiter initialized with keyPrefix={}, fallbackOnError={}",
          this.keyPrefix,
          this.fallbackOnError);
    }
  }

  @Override
  public RateLimitDecision tryAcquire(RateLimitRule rule, RateLimitContext context) {
    if (redisTemplate == null) {
      return fallbackDecision(rule, context, "StringRedisTemplate not available");
    }
    try {
      String key = buildKey(context);
      DefaultRedisScript<List> script = selectScript(rule.getAlgorithm());
      List<?> result =
          redisTemplate.execute(script, Collections.singletonList(key), buildArgs(rule, context));
      return parseResult(result, rule, context, key);
    } catch (Exception ex) {
      log.error(
          "Redis cluster rate limit failed for resource={}, fallback={}",
          context.getResource(),
          fallbackOnError,
          ex);
      return fallbackDecision(rule, context, "redis error: " + ex.getMessage());
    }
  }

  @Override
  public List<RateLimitDecision> tryAcquireBatch(
      RateLimitRule rule, RateLimitContext context, int count) {
    return Collections.nCopies(count, tryAcquire(rule, context));
  }

  @Override
  public RateLimitMode getMode() {
    return RateLimitMode.CLUSTER;
  }

  /**
   * 构造 Redis key。
   *
   * @param context 限流上下文
   * @return Redis key（前缀 + 资源名）
   */
  public String buildKey(RateLimitContext context) {
    return keyPrefix + context.getResource();
  }

  /**
   * 根据算法选择 Lua 脚本
   *
   * <p>统一使用令牌桶算法。
   */
  private static DefaultRedisScript<List> selectScript(RateLimitAlgorithm algorithm) {
    return TOKEN_BUCKET_SCRIPT;
  }

  /** 构造 Lua 脚本参数 */
  private Object[] buildArgs(RateLimitRule rule, RateLimitContext context) {
    long now = Instant.now().toEpochMilli();
    double rate = rule.getThreshold();
    long capacity =
        rule.getBurstCapacity() > 0 ? rule.getBurstCapacity() : (long) rule.getThreshold();
    double cost = 1.0;
    return new Object[] {
      String.valueOf(rate), String.valueOf(capacity), String.valueOf(now), String.valueOf(cost)
    };
  }

  /** 解析 Lua 脚本返回结果 */
  private RateLimitDecision parseResult(
      List<?> result, RateLimitRule rule, RateLimitContext context, String key) {
    if (result == null || result.size() < 2) {
      return fallbackDecision(rule, context, "redis returned null/empty result");
    }
    long passed = toLong(result.get(0));
    double remaining = toDouble(result.get(1));
    RateLimitResult res = (passed == 1L) ? RateLimitResult.PASS : RateLimitResult.BLOCKED;
    return RateLimitDecision.builder()
        .resource(context.getResource())
        .key(key)
        .rule(rule)
        .result(res)
        .remaining(remaining)
        .threshold(rule.getThreshold())
        .timestamp(Instant.now())
        .reason(
            res == RateLimitResult.PASS
                ? "redis cluster limiter pass"
                : "redis cluster limiter blocked")
        .build();
  }

  /** Redis 不可用时的降级决策 */
  private RateLimitDecision fallbackDecision(
      RateLimitRule rule, RateLimitContext context, String reason) {
    RateLimitResult res =
        "BLOCK".equals(fallbackOnError) ? RateLimitResult.BLOCKED : RateLimitResult.PASS;
    return RateLimitDecision.builder()
        .resource(context.getResource())
        .rule(rule)
        .result(res)
        .remaining(res == RateLimitResult.PASS ? rule.getThreshold() : 0)
        .threshold(rule.getThreshold())
        .timestamp(Instant.now())
        .reason("cluster limiter fallback: " + reason)
        .build();
  }

  private static long toLong(Object o) {
    if (o == null) {
      return 0L;
    }
    if (o instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(o.toString());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static double toDouble(Object o) {
    if (o == null) {
      return 0.0;
    }
    if (o instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(o.toString());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  /** 构造 RedisScript（返回类型固定为 List，遵循 Spring Data Redis 的 DefaultRedisScript 设计） */
  private static DefaultRedisScript<List> buildScript(String lua) {
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setScriptText(lua);
    script.setResultType(List.class);
    return script;
  }
}
