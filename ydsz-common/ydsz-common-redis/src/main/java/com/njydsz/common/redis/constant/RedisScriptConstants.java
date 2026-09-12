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
 *   <li>{@link #TOKEN_BUCKET_LUA_MULTI_DIMENSION} — 多维度批量令牌桶（秒精度），适用于响应式栈
 *       （{@code ReactiveStringRedisTemplate}）单次往返同时校验 IP/用户等多维度限流，
 *       消费方为 {@code ydsz-gateway} 的 {@code RateLimitFilter}
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

  // ======================== 令牌桶（多维度批量，秒精度） ========================

  /**
   * 多维度批量令牌桶限流 Lua 脚本（秒精度，响应式栈适用）。
   *
   * <p>与 {@link #TOKEN_BUCKET_LUA_MS} 算法同源，差异在于：单次 Redis 往返同时校验
   * {@code KEYS[1..2]} 指向的多维度（如 IP 维度 + 用户维度），避免逐维度调用产生的网络开销。
   * 秒精度时间戳与 TTL 由 {@code ReactiveStringRedisTemplate} 的调用模型决定。
   *
   * <p>参数：
   *
   * <pre>
   *   KEYS[1] = 第一维度 key（如 ip）  KEYS[2] = 第二维度 key（如 user）
   *   ARGV[1..3] = 第一维度 rate/capacity/enabled（enabled=0 时该维度直接放行）
   *   ARGV[4..6] = 第二维度 rate/capacity/enabled
   *   ARGV[7] = timestamp_seconds  ARGV[8] = requested_tokens
   * </pre>
   *
   * <p>返回：{dim1_allowed, dim1_remaining, dim1_reset, dim2_allowed, dim2_remaining, dim2_reset}
   *
   * <p><b>来源（ADR-4）：</b>自 {@code ydsz-gateway} 的 {@code RateLimitFilter} 内联脚本下沉，
   * 消除业务模块内联 Lua（规范 §33.2/§33.3）。修改本脚本时须与 {@link #TOKEN_BUCKET_LUA_MS}
   * 的令牌补充/扣减语义保持一致。
   */
  public static final String TOKEN_BUCKET_LUA_MULTI_DIMENSION =
      """
            -- 令牌桶算法
            local function token_bucket(key, rate, capacity, now, requested)
                local bucket = redis.call('hmget', key, 'tokens', 'timestamp')
                local tokens = tonumber(bucket[1])
                local last_refill = tonumber(bucket[2])

                if tokens == nil then
                    tokens = capacity
                    last_refill = now
                end

                local elapsed = math.max(0, now - last_refill)
                local refill = elapsed * rate
                tokens = math.min(capacity, tokens + refill)

                local allowed = 0
                local remaining = tokens

                if tokens >= requested then
                    tokens = tokens - requested
                    allowed = 1
                    remaining = tokens
                end

                local ttl = math.ceil(capacity / rate * 2)
                redis.call('hmset', key, 'tokens', tokens, 'timestamp', now)
                redis.call('expire', key, ttl)

                local reset = math.ceil((capacity - tokens) / rate)
                return allowed, remaining, reset
            end

            local now = tonumber(ARGV[7])
            local requested = tonumber(ARGV[8])

            local results = {}

            -- 遍历 2 个维度（每个维度 3 个参数：rate, capacity, enabled）
            for i = 1, 2 do
                local key_index = i
                local arg_base = (i - 1) * 3
                local enabled = tonumber(ARGV[arg_base + 3])

                if enabled == 1 then
                    local rate = tonumber(ARGV[arg_base + 1])
                    local capacity = tonumber(ARGV[arg_base + 2])
                    local allowed, remaining, reset = token_bucket(KEYS[key_index], rate, capacity, now, requested)
                    results[i * 3 - 2] = allowed
                    results[i * 3 - 1] = remaining
                    results[i * 3] = reset
                else
                    results[i * 3 - 2] = 1
                    results[i * 3 - 1] = 0
                    results[i * 3] = 0
                end
            end

            return results
            """;

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
