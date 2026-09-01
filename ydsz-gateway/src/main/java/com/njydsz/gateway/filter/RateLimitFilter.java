package com.njydsz.gateway.filter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayErrorWriter;
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
 * @since 26.09.01
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

  /**
   * 二维度（IP + 用户）合并令牌桶 Lua 脚本。
   *
   * <p>参数:
   *
   * <pre>
   *   KEYS[1] = ip key         KEYS[2] = user key
   *   ARGV[1..3] = ip rate/capacity/enabled
   *   ARGV[4..6] = user rate/capacity/enabled
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

  /** 预编译 Lua 脚本 */
  private final RedisScript<List> tokenBucketScript =
      RedisScript.of(new ByteArrayResource(TOKEN_BUCKET_SCRIPT.getBytes()), List.class);

  /**
   * 限流过滤器入口。
   *
   * <p>先检查白名单路径，再按 IP → 用户 两个维度执行令牌桶限流
   * （维度启用与否由配置 {@code ydsz.gateway.ratelimit.per-*.enabled} 控制）。
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
              // 按优先级检查各维度限流：IP → USER
              if (!result.ipAllowed()) {
                return rejectWithRateLimit(
                    exchange, "IP", clientIp, properties.getPerIp().getDefaultQps(), result.ipReset());
              }
              if (!result.userAllowed()) {
                return rejectWithRateLimit(
                    exchange, "USER", userId, properties.getPerUser().getDefaultQps(), result.userReset());
              }
              return chain.filter(exchange);
            });
  }

  /**
   * 执行二维度令牌桶限流检查（IP + 用户）。
   *
   * <p>维度标识来源：IP（可信代理解析）、用户（X-User-Id）。
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
      log.warn("[RateLimit] Redis 连续失败 {} 次，限流降级放行", redisFailureCount.get());
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
      return Mono.just(allAllowedResult());
    }

    long now = System.currentTimeMillis() / 1000;
    List<String> keys =
        List.of(
            "ydsz:ratelimit:ip:" + (clientIp != null ? clientIp : ""),
            "ydsz:ratelimit:user:" + (userId != null ? userId : ""));

    // ARGV[1..6] = 2 维度 × (rate, capacity, enabled)；ARGV[7]=now；ARGV[8]=requested
    List<Object> args = new ArrayList<>(8);
    appendDimensionArgs(
        args, properties.getPerIp().getDefaultQps(), properties.getPerIp().getBurstCapacity(), ipEnabled);
    appendDimensionArgs(
        args, properties.getPerUser().getDefaultQps(), properties.getPerUser().getBurstCapacity(), userEnabled);
    args.add(String.valueOf(now));
    args.add("1");

    return redisTemplate
        .execute(tokenBucketScript, keys, args)
        .next()
        .map(
            result -> {
              if (result == null || result.size() < 6) {
                redisFailureCount.incrementAndGet();
                return allAllowedResult();
              }
              boolean ipAllowed = getLong(result, 0) != null && getLong(result, 0) == 1L;
              int ipRemaining = getLong(result, 1) != null ? getLong(result, 1).intValue() : 0;
              int ipReset = getLong(result, 2) != null ? getLong(result, 2).intValue() : 0;
              boolean userAllowed = getLong(result, 3) != null && getLong(result, 3) == 1L;
              int userRemaining = getLong(result, 4) != null ? getLong(result, 4).intValue() : 0;
              int userReset = getLong(result, 5) != null ? getLong(result, 5).intValue() : 0;

              redisFailureCount.set(0);
              return new RateLimitResult(
                  ipAllowed, ipRemaining, ipReset,
                  userAllowed, userRemaining, userReset);
            })
        .onErrorResume(
            e -> {
              int count = redisFailureCount.incrementAndGet();
              log.warn("[RateLimit] Redis 限流检查异常 (连续 {} 次)，降级放行: path={} err={}",
                  count, exchange.getRequest().getURI().getPath(), e.getMessage());
              return Mono.just(localFallback());
            })
        .defaultIfEmpty(allAllowedResult());
  }

  /**
   * 追加单个限流维度的 Lua 参数（rate, capacity, enabled）。
   *
   * @param args 参数列表
   * @param qps 令牌桶速率
   * @param capacity 令牌桶容量
   * @param enabled 该维度是否启用
   */
  private void appendDimensionArgs(List<Object> args, int qps, int capacity, boolean enabled) {
    args.add(String.valueOf(qps));
    args.add(String.valueOf(capacity));
    args.add(enabled ? "1" : "0");
  }

  /** 限流结果记录（IP + 用户二维度） */
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
    return allAllowedResult();
  }

  /** 全部维度放行的限流结果（未启用维度与异常降级时使用） */
  private RateLimitResult allAllowedResult() {
    return new RateLimitResult(
        true, 0, 0,
        true, 0, 0);
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
   * 返回 429 限流响应（P0-D1：统一错误响应写出器）。
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
    // 限流响应头（X-RateLimit-* / Retry-After / 绝对时间戳）
    if (properties.getResponseHeaders().isEnabled()) {
      ServerHttpResponse response = exchange.getResponse();
      response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
      response.getHeaders().add("X-RateLimit-Remaining", "0");
      response.getHeaders().add("X-RateLimit-Reset", String.valueOf(resetSeconds));
      // E2: Retry-After 同时提供相对秒数和绝对时间戳（RFC 9110 / ISO 8601），便于客户端精确等待
      response.getHeaders().add("Retry-After", String.valueOf(resetSeconds));
      Instant resetAt = Instant.now().plus(resetSeconds, ChronoUnit.SECONDS);
      response.getHeaders().add("X-RateLimit-Reset-Time", resetAt.toString());
    }

    if (gatewayMetrics != null) {
      gatewayMetrics.incrementRatelimitTriggered(dimension, exchange.getRequest().getURI().getPath());
    }

    GatewayErrorCode errorCode = resolveRateLimitErrorCode(dimension);
    log.info("[RateLimit] 限流触发: dimension={} identity={} path={} reset={}s",
        dimension, maskIdentity(identity), exchange.getRequest().getURI().getPath(), resetSeconds);
    return GatewayErrorWriter.write(
        exchange,
        HttpStatus.TOO_MANY_REQUESTS,
        errorCode,
        errorCode.getMessageKey(),
        exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID));
  }

  /**
   * 按限流维度解析业务错误码。
   *
   * @param dimension 限流维度（IP / USER）
   * @return 对应错误码，未知维度返回通用限流错误码
   */
  private GatewayErrorCode resolveRateLimitErrorCode(String dimension) {
    if (dimension == null) {
      return GatewayErrorCode.RATE_LIMITED;
    }
    return switch (dimension.toUpperCase()) {
      case "IP" -> GatewayErrorCode.RATE_LIMITED_IP;
      case "USER" -> GatewayErrorCode.RATE_LIMITED_USER;
      default -> GatewayErrorCode.RATE_LIMITED;
    };
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
