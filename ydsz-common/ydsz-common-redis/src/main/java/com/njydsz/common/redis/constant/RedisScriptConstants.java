package com.njydsz.common.redis.constant;

/**
 * Redis Lua 脚本常量仓库。
 *
 * <p>统一存放 ydsz-common 中所有基于 Redis + Lua 原子操作的脚本文本。
 * 限流类脚本（固定窗口、滑动窗口、令牌桶）在此统一定义，避免业务模块各自内联同源脚本时出现算法级漂移。
 *
 * <p><b>脚本变体说明：</b>
 *
 * <ul>
 *   <li>{@link #TOKEN_BUCKET_LUA_MS} — 毫秒精度版，适用于同步 {@link
 *       org.springframework.data.redis.core.RedisTemplate}， 使用 {@code PEXPIRE}（毫秒 TTL）和毫秒时间戳
 *   <li>响应式栈版本（基于秒精度）由 {@code ydsz-gateway} 的 {@code RateLimitFilter} 持有， 差异源于 {@link
 *       org.springframework.data.redis.core.ReactiveStringRedisTemplate} 的调用模型； 两者算法同源，修改时请保持语义一致
 * </ul>
 *
 * <p><b>使用约束：</b>
 *
 * <ul>
 *   <li>脚本文本仅应在此处修改 — 业务模块通过 {@code RedisScript.of(...)} 引用，禁止内联复制
 *   <li>修改脚本前必须运行 {@code RedisRateLimiter} 单测验证三类算法行为不退化
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.redis.service.RedisRateLimiter
 */
public final class RedisScriptConstants {

  private RedisScriptConstants() {
    throw new UnsupportedOperationException("Constants class");
  }

  // ======================== INCR + EXPIRE 原子计数 ========================

  /**
   * 原子 INCR + EXPIRE Lua 脚本（固定窗口计数器）。
   *
   * <p>逻辑：INCR key，若值为 1 则设置过期时间；返回当前值。 原子性保证：Redis 单线程执行 Lua 脚本，INCR + EXPIRE 不会分裂。
   *
   * <p>参数：KEYS[1]=key, ARGV[1]=window_seconds
   *
   * <p>返回：current_count (Long)
   *
   * <p><b>适用场景：</b>固定窗口限流、WebSocket 消息频率统计、 任何需要"计数 + 自动过期"原子操作的场景。
   */
  public static final String FIXED_WINDOW_LUA =
      "local current = redis.call('INCR', KEYS[1]) "
          + "if current == 1 then "
          + "  redis.call('EXPIRE', KEYS[1], ARGV[1]) "
          + "end "
          + "return current";

  /**
   * {@link #FIXED_WINDOW_LUA} 的别名，语义同"带过期的原子递增"。
   *
   * <p>供 WebSocket 模块等业务侧明确表达"INCR + 首次创建 EXPIRE"语义时使用。
   */
  public static final String INCR_WITH_EXPIRE_LUA = FIXED_WINDOW_LUA;

  // ======================== 令牌桶（毫秒精度） ========================

  /**
   * 令牌桶限流 Lua 脚本（毫秒精度版）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>读取桶中当前令牌数与上次刷新时间（毫秒）
   *   <li>计算自上次刷新以来应补充的令牌数（floor(elapsed * rate / periodMs)）
   *   <li>更新令牌数（不超过 capacity），可选仅在有新增令牌时推进 lastRefill
   *   <li>若令牌数 >= requested，扣减并返回 {1, tokens}；否则返回 {0, tokens}
   * </ol>
   *
   * <p>参数：KEYS[1]=key, ARGV[1]=capacity, ARGV[2]=rate, ARGV[3]=periodMs, ARGV[4]=now_ms,
   * ARGV[5]=requested
   *
   * <p>返回：{allowed(0/1), remaining_tokens}
   */
  public static final String TOKEN_BUCKET_LUA_MS =
      "local key = KEYS[1] "
          + "local capacity = tonumber(ARGV[1]) "
          + "local rate = tonumber(ARGV[2]) "
          + "local periodMs = tonumber(ARGV[3]) "
          + "local now = tonumber(ARGV[4]) "
          + "local requested = tonumber(ARGV[5]) "
          + "local data = redis.call('HMGET', key, 'tokens', 'lastRefillMs') "
          + "local tokens = tonumber(data[1]) "
          + "local lastRefill = tonumber(data[2]) "
          + "if tokens == nil then "
          + "  tokens = capacity "
          + "  lastRefill = now "
          + "end "
          + "local elapsed = now - lastRefill "
          + "if elapsed > 0 then "
          + "  local refill = math.floor(elapsed * rate / periodMs) "
          + "  if refill > 0 then "
          + "    tokens = math.min(capacity, tokens + refill) "
          + "    lastRefill = now "
          + "  end "
          + "end "
          + "local allowed = 0 "
          + "if tokens >= requested then "
          + "  tokens = tokens - requested "
          + "  allowed = 1 "
          + "end "
          + "redis.call('HMSET', key, 'tokens', tokens, 'lastRefillMs', lastRefill) "
          + "redis.call('PEXPIRE', key, math.ceil(periodMs * 2 / 1000) + 1) "
          + "return {allowed, tokens}";

  // ======================== 滑动窗口（分桶） ========================

  /**
   * 分桶滑动窗口限流 Lua 脚本。
   *
   * <p>使用 Hash 存储时间桶计数，替代 ZSET 存储每个请求的唯一 member。 内存占用恒定为 O(bucketCount)，不受请求数量影响。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>计算当前时间桶编号
   *   <li>删除超出窗口的旧桶
   *   <li>统计所有存活桶的总计数
   *   <li>若总计数 ≥ limit，拒绝并返回 {0, totalCount}
   *   <li>递增当前桶计数，设置 Key 过期时间
   * </ol>
   *
   * <p>参数：KEYS[1]=key, ARGV[1]=now_ms, ARGV[2]=windowMs, ARGV[3]=limit, ARGV[4]=bucketCount
   *
   * <p>返回：{allowed(0/1), total_count}
   */
  public static final String SLIDING_WINDOW_BUCKETED_LUA =
      "local key = KEYS[1] "
          + "local now = tonumber(ARGV[1]) "
          + "local windowMs = tonumber(ARGV[2]) "
          + "local limit = tonumber(ARGV[3]) "
          + "local bucketCount = tonumber(ARGV[4]) "
          + "local bucketSize = math.floor(windowMs / bucketCount) "
          + "local currentBucket = math.floor(now / bucketSize) "
          + "local fields = redis.call('HKEYS', key) "
          + "for i = 1, #fields do "
          + "  if tonumber(fields[i]) < currentBucket - bucketCount then "
          + "    redis.call('HDEL', key, fields[i]) "
          + "  end "
          + "end "
          + "local allValues = redis.call('HVALS', key) "
          + "local totalCount = 0 "
          + "for i = 1, #allValues do "
          + "  totalCount = totalCount + tonumber(allValues[i]) "
          + "end "
          + "if totalCount >= limit then "
          + "  return {0, totalCount} "
          + "end "
          + "redis.call('HINCRBY', key, currentBucket, 1) "
          + "redis.call('PEXPIRE', key, windowMs + 1000) "
          + "return {1, totalCount + 1}";
}
