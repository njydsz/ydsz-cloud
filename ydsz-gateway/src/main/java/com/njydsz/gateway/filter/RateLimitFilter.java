package com.njydsz.gateway.filter;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.RateLimitProperties;

/**
 * 限流全局过滤器。
 *
 * <p>基于 Redis + Lua 脚本实现的令牌桶限流，支持 IP 和用户两个维度：
 *
 * <ul>
 *   <li>IP 级限流：防止单 IP 暴力请求
 *   <li>用户级限流：按用户 ID 限流
 * </ul>
 *
 * <h3>令牌桶算法</h3>
 *
 * <p>使用 Redis Lua 脚本保证原子性：以固定速率向桶中添加令牌（replenishRate），桶容量有限（burstCapacity），
 * 每次请求消耗 1 个令牌，桶空时拒绝。两个维度合并为单次 Redis 调用，减少网络 IO。
 *
 * <h3>限流维度优先级</h3>
 *
 * <ol>
 *   <li>IP 级（最先检查，防止单 IP 暴力请求）
 *   <li>用户级（按 userId 限流）
 * </ol>
 *
 * <h3>降级策略</h3>
 *
 * <p>Redis 不可用时直接放行，保证可用性。生产环境建议通过集群 Redis 或 Sentinel 避免单点故障。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "rate-limit",
    havingValue = "true",
    matchIfMissing = true)
public class RateLimitFilter implements GlobalFilter, Ordered {

  private final RateLimitProperties properties;
  private final ReactiveStringRedisTemplate redisTemplate;
  private final GatewayMetrics gatewayMetrics;

  /** Redis 连续失败计数器 */
  private static final int CIRCUIT_THRESHOLD = 5;

  private final AtomicInteger redisFailureCount = new AtomicInteger(0);

  /** Redis 不可用时的本地兜底令牌桶 */
  private volatile long localBucketTokens = 200;
  private volatile long localBucketLastRefill = System.currentTimeMillis() / 1000;

  /**
   * IP + 用户二维度合并令牌桶 Lua 脚本。
   *
   * <p>参数:
   *
   * <pre>
   *   KEYS[1] = ip key         KEYS[2] = user key
   *   ARGV[1] = ip rate        ARGV[2] = ip capacity      ARGV[3] = ip enabled(1/0)
   *   ARGV[4] = user rate      ARGV[5] = user capacity    ARGV[6] = user enabled(1/0)
   *   ARGV[7] = timestamp_seconds  ARGV[8] = requested_tokens
   * </pre>
   *
   * <p>返回: {ip_allowed, ip_remaining, ip_reset, user_allowed, user_remaining, user_reset}
   */
  private static final String TOKEN_BUCKET_SCRIPT =
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

            local ip_key = KEYS[1]
            local user_key = KEYS[2]

            local ip_rate = tonumber(ARGV[1])
            local ip_capacity = tonumber(ARGV[2])
            local ip_enabled = tonumber(ARGV[3])

            local user_rate = tonumber(ARGV[4])
            local user_capacity = tonumber(ARGV[5])
            local user_enabled = tonumber(ARGV[6])

            local now = tonumber(ARGV[7])
            local requested = tonumber(ARGV[8])

            local ip_allowed, ip_remaining, ip_reset = 1, 0, 0
            local user_allowed, user_remaining, user_reset = 1, 0, 0

            if ip_enabled == 1 then
                ip_allowed, ip_remaining, ip_reset = token_bucket(ip_key, ip_rate, ip_capacity, now, requested)
            end

            if user_enabled == 1 then
                user_allowed, user_remaining, user_reset = token_bucket(user_key, user_rate, user_capacity, now, requested)
            end

            return {ip_allowed, ip_remaining, ip_reset, user_allowed, user_remaining, user_reset}
            """;

  /** 预编译 Lua 脚本 */
  private final RedisScript<List> tokenBucketScript =
      RedisScript.of(new ByteArrayResource(TOKEN_BUCKET_SCRIPT.getBytes()), List.class);

  /**
   * 限流过滤器入口。
   *
   * <p>先检查白名单路径，再按 IP + 用户两个维度执行令牌桶限流。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或拒绝（429）的完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    // 白名单路径不限流
    if (isWhitelistPath(path)) {
      return chain.filter(exchange);
    }

    String clientIp = GatewayIpUtils.getClientIp(request);
    String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);

    // IP 白名单检查
    boolean ipWhitelisted =
        properties.getPerIp().getWhitelist() != null
            && clientIp != null
            && !clientIp.isEmpty()
            && properties.getPerIp().getWhitelist().contains(clientIp);

    return executeTokenBucket(exchange, clientIp, userId, ipWhitelisted)
        .flatMap(
            result -> {
              if (result == null || result.allAllowed()) {
                return chain.filter(exchange);
              }
              if (!result.ipAllowed()) {
                return rejectWithRateLimit(
                    exchange, "IP", clientIp, properties.getPerIp().getDefaultQps(), result.ipReset());
              }
              return rejectWithRateLimit(
                  exchange, "USER", userId, properties.getPerUser().getDefaultQps(), result.userReset());
            });
  }

  /**
   * 执行 IP + 用户二维度令牌桶限流检查。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param clientIp 客户端 IP
   * @param userId 用户 ID
   * @param ipWhitelisted IP 是否在白名单中
   * @return 限流结果 Mono
   */
  private Mono<RateLimitResult> executeTokenBucket(
      ServerWebExchange exchange,
      String clientIp,
      String userId,
      boolean ipWhitelisted) {
    // Redis 熔断检查
    if (redisFailureCount.get() >= CIRCUIT_THRESHOLD) {
      log.warn("[RateLimit] Redis 连续失败 {} 次，切换到本地兜底限流模式", redisFailureCount.get());
      return Mono.just(localFallback());
    }

    boolean ipEnabled =
        properties.getPerIp().isEnabled()
            && !ipWhitelisted
            && clientIp != null
            && !clientIp.isEmpty();
    boolean userEnabled =
        properties.getPerUser().isEnabled() && userId != null && !userId.isEmpty();

    if (!ipEnabled && !userEnabled) {
      return Mono.just(new RateLimitResult(true, 0, 0, true, 0, 0));
    }

    long now = System.currentTimeMillis() / 1000;
    String ipKey = "ydsz:ratelimit:ip:" + (clientIp != null ? clientIp : "");
    String userKey = "ydsz:ratelimit:user:" + (userId != null ? userId : "");

    List<String> keys = List.of(ipKey, userKey);
    List<Object> args =
        Arrays.asList(
            String.valueOf(properties.getPerIp().getDefaultQps()),
            String.valueOf(properties.getPerIp().getBurstCapacity()),
            ipEnabled ? "1" : "0",
            String.valueOf(properties.getPerUser().getDefaultQps()),
            String.valueOf(properties.getPerUser().getBurstCapacity()),
            userEnabled ? "1" : "0",
            String.valueOf(now),
            "1");

    return redisTemplate
        .execute(tokenBucketScript, keys, args)
        .next()
        .map(
            result -> {
              if (result == null || result.size() < 6) {
                redisFailureCount.incrementAndGet();
                return new RateLimitResult(true, 0, 0, true, 0, 0);
              }
              boolean ipAllowed = getLong(result, 0) != null && getLong(result, 0) == 1L;
              int ipRemaining = getLong(result, 1) != null ? getLong(result, 1).intValue() : 0;
              int ipReset = getLong(result, 2) != null ? getLong(result, 2).intValue() : 0;
              boolean userAllowed = getLong(result, 3) != null && getLong(result, 3) == 1L;
              int userRemaining = getLong(result, 4) != null ? getLong(result, 4).intValue() : 0;
              int userReset = getLong(result, 5) != null ? getLong(result, 5).intValue() : 0;

              redisFailureCount.set(0);
              return new RateLimitResult(ipAllowed, ipRemaining, ipReset, userAllowed, userRemaining, userReset);
            })
        .onErrorResume(
            e -> {
              int count = redisFailureCount.incrementAndGet();
              log.warn("[RateLimit] Redis 限流检查异常 (连续 {} 次)，降级放行: path={} err={}",
                  count, exchange.getRequest().getURI().getPath(), e.getMessage());
              return Mono.just(localFallback());
            })
        .defaultIfEmpty(new RateLimitResult(true, 0, 0, true, 0, 0));
  }

  /** 限流结果记录 */
  private record RateLimitResult(
      boolean ipAllowed,
      int ipRemaining,
      int ipReset,
      boolean userAllowed,
      int userRemaining,
      int userReset) {
    boolean allAllowed() {
      return ipAllowed && userAllowed;
    }
  }

  /** 本地兜底限流（Redis 不可用时直接放行） */
  private RateLimitResult localFallback() {
    if (gatewayMetrics != null) {
      gatewayMetrics.incrementRatelimitFallback();
    }
    return new RateLimitResult(true, 0, 0, true, 0, 0);
  }

  /** 安全类型转换 */
  private Long getLong(List list, int index) {
    if (list == null || index < 0 || index >= list.size()) {
      return null;
    }
    Object value = list.get(index);
    if (value instanceof Long l) {
      return l;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    if (value instanceof String s) {
      try {
        return Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * 返回 429 限流响应。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param dimension 限流维度
   * @param identity 限流标识
   * @param limit 限流配额
   * @param resetSeconds 重置时间（秒）
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectWithRateLimit(
      ServerWebExchange exchange, String dimension, String identity, int limit, int resetSeconds) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    if (properties.getResponseHeaders().isEnabled()) {
      response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
      response.getHeaders().add("X-RateLimit-Remaining", "0");
      response.getHeaders().add("X-RateLimit-Reset", String.valueOf(resetSeconds));
      response.getHeaders().add("Retry-After", String.valueOf(resetSeconds));
    }

    if (gatewayMetrics != null) {
      gatewayMetrics.incrementRatelimitTriggered(dimension, exchange.getRequest().getURI().getPath());
    }

    BaseResponse<Void> body =
        BaseResponse.error(
            BaseResultCode.TOO_MANY_REQUESTS,
            "请求过于频繁，请稍后重试 (" + dimension + "=" + maskIdentity(identity) + ")");
    byte[] bytes = YdszJson.toJsonBytes(body);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);

    log.info("[RateLimit] 限流触发: dimension={} identity={} path={} reset={}s",
        dimension, maskIdentity(identity), exchange.getRequest().getURI().getPath(), resetSeconds);
    return response.writeWith(Mono.just(buffer));
  }

  /** 白名单路径不限流 */
  private boolean isWhitelistPath(String path) {
    return path != null
        && (path.startsWith("/actuator")
            || path.startsWith("/health")
            || path.equals("/auth/login")
            || path.equals("/auth/captcha")
            || path.equals("/auth/refresh"));
  }

  /** 身份标识脱敏 */
  private String maskIdentity(String identity) {
    if (identity == null || identity.length() <= 4) {
      return "***";
    }
    return identity.substring(0, 2) + "***" + identity.substring(identity.length() - 2);
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.RATE_LIMIT.getOrder();
  }
}
