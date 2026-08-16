package com.njydsz.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.common.json.YdszJson;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.RateLimitProperties;

/**
 * P3-7: 精细化限流全局过滤器（三维度合并Lua脚本版）
 *
 * <p>基于 Redis + Lua 脚本实现的令牌桶限流，支持多维度：
 *
 * <ul>
 *   <li>用户级限流（按 X-User-ID）
 *   <li>IP 级限流（按客户端 IP）
 *   <li>租户级限流（按 X-Tenant-Id）
 * </ul>
 *
 * <h3>P3-7 性能优化（三维度合并）</h3>
 *
 * <p>原实现使用 {@code Mono.zip} 并行发起 3 次 Redis 调用（每维度一次）， 合并为单次 Redis 调用：在一个 Lua 脚本中完成三个维度的令牌桶计算， 减少
 * Redis 网络 IO 66%（3次→1次），降低 Redis 服务端压力。
 *
 * <h3>令牌桶算法</h3>
 *
 * <p>使用 Redis Lua 脚本保证原子性：
 *
 * <ol>
 *   <li>以固定速率向桶中添加令牌（replenishRate）
 *   <li>桶容量有限（burstCapacity），超出则丢弃
 *   <li>每次请求消耗 1 个令牌，桶空时拒绝
 * </ol>
 *
 * <h3>限流维度优先级</h3>
 *
 * <ol>
 *   <li>IP 级（最先检查，防止单 IP 暴力请求）
 *   <li>用户级（按 userId 限流）
 *   <li>租户级（按 tenantId 限流）
 * </ol>
 *
 * 任一维度触发限流即返回 429。
 *
 * <h3>响应头</h3>
 *
 * <p>限流触发时返回标准响应头：
 *
 * <ul>
 *   <li>{@code X-RateLimit-Limit}: 总配额
 *   <li>{@code X-RateLimit-Remaining}: 剩余配额
 *   <li>{@code X-RateLimit-Reset}: 重置时间（秒）
 *   <li>{@code Retry-After}: 建议重试等待时间（秒）
 * </ul>
 *
 * @since 3.7.0
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
  private final DiscoveryClient discoveryClient;

  /** P0-3: Redis 连续失败计数器，超过阈值切换本地兜底 */
  private static final int CIRCUIT_THRESHOLD = 5;

  private final AtomicInteger redisFailureCount = new AtomicInteger(0);

  /** P0-3: 本地兜底限流状态（Redis 不可用时的降级） */
  private volatile long localBucketTokens = 200;

  private volatile long localBucketLastRefill = System.currentTimeMillis() / 1000;

  /** P1-2: 网关实例数缓存（10 秒刷新，避免频繁调用 Nacos 服务发现） */
  private static final long INSTANCE_COUNT_CACHE_MS = 10_000;

  private volatile int cachedInstanceCount = 1;
  private volatile long instanceCountFetchedAt = 0;

  /**
   * P3-7: 三维度合并令牌桶 Lua 脚本
   *
   * <p>将 IP / 用户 / 租户三个维度的令牌桶计算合并为单次 Redis 调用， 减少 66% 的网络 IO。脚本内部定义 {@code token_bucket} 函数复用算法。
   *
   * <p>参数:
   *
   * <pre>
   *   KEYS[1] = ip key         KEYS[2] = user key         KEYS[3] = tenant key
   *   ARGV[1] = ip rate        ARGV[2] = ip capacity      ARGV[3] = ip enabled(1/0)
   *   ARGV[4] = user rate      ARGV[5] = user capacity    ARGV[6] = user enabled(1/0)
   *   ARGV[7] = tenant rate    ARGV[8] = tenant capacity  ARGV[9] = tenant enabled(1/0)
   *   ARGV[10] = timestamp_seconds  ARGV[11] = requested_tokens
   * </pre>
   *
   * <p>返回: {ip_allowed, ip_remaining, ip_reset, user_allowed, user_remaining, user_reset,
   * tenant_allowed, tenant_remaining, tenant_reset}
   */
  private static final String MERGED_TOKEN_BUCKET_SCRIPT =
      """
            -- 令牌桶算法（单维度，内部复用）
            local function token_bucket(key, rate, capacity, now, requested)
                local bucket = redis.call('hmget', key, 'tokens', 'timestamp')
                local tokens = tonumber(bucket[1])
                local last_refill = tonumber(bucket[2])

                if tokens == nil then
                    tokens = capacity
                    last_refill = now
                end

                -- 计算自上次填充以来应补充的令牌数
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

                -- 写回桶状态，设置 TTL（2 倍填充时间，避免无限存储）
                local ttl = math.ceil(capacity / rate * 2)
                redis.call('hmset', key, 'tokens', tokens, 'timestamp', now)
                redis.call('expire', key, ttl)

                local reset = math.ceil((capacity - tokens) / rate)
                return allowed, remaining, reset
            end

            -- 参数解析
            local ip_key = KEYS[1]
            local user_key = KEYS[2]
            local tenant_key = KEYS[3]

            local ip_rate = tonumber(ARGV[1])
            local ip_capacity = tonumber(ARGV[2])
            local ip_enabled = tonumber(ARGV[3])

            local user_rate = tonumber(ARGV[4])
            local user_capacity = tonumber(ARGV[5])
            local user_enabled = tonumber(ARGV[6])

            local tenant_rate = tonumber(ARGV[7])
            local tenant_capacity = tonumber(ARGV[8])
            local tenant_enabled = tonumber(ARGV[9])

            local now = tonumber(ARGV[10])
            local requested = tonumber(ARGV[11])

            -- 执行三维度检查（未启用维度默认放行）
            local ip_allowed, ip_remaining, ip_reset = 1, 0, 0
            local user_allowed, user_remaining, user_reset = 1, 0, 0
            local tenant_allowed, tenant_remaining, tenant_reset = 1, 0, 0

            if ip_enabled == 1 then
                ip_allowed, ip_remaining, ip_reset = token_bucket(ip_key, ip_rate, ip_capacity, now, requested)
            end

            if user_enabled == 1 then
                user_allowed, user_remaining, user_reset = token_bucket(user_key, user_rate, user_capacity, now, requested)
            end

            if tenant_enabled == 1 then
                tenant_allowed, tenant_remaining, tenant_reset = token_bucket(tenant_key, tenant_rate, tenant_capacity, now, requested)
            end

            return {ip_allowed, ip_remaining, ip_reset, user_allowed, user_remaining, user_reset, tenant_allowed, tenant_remaining, tenant_reset}
            """;

  /** P3-7: 预编译合并 Lua 脚本 */
  private final RedisScript<List> mergedTokenBucketScript =
      RedisScript.of(
          new ByteArrayResource(MERGED_TOKEN_BUCKET_SCRIPT.getBytes(StandardCharsets.UTF_8)),
          List.class);

  /**
   * P3-7: 三维度合并限流结果
   *
   * @param ipAllowed IP 维度是否放行
   * @param ipRemaining IP 维度剩余令牌
   * @param ipReset IP 维度重置时间
   * @param userAllowed 用户维度是否放行
   * @param userRemaining 用户维度剩余令牌
   * @param userReset 用户维度重置时间
   * @param tenantAllowed 租户维度是否放行
   * @param tenantRemaining 租户维度剩余令牌
   * @param tenantReset 租户维度重置时间
   */
  private record MergedRateLimitResult(
      boolean ipAllowed,
      int ipRemaining,
      int ipReset,
      boolean userAllowed,
      int userRemaining,
      int userReset,
      boolean tenantAllowed,
      int tenantRemaining,
      int tenantReset) {
    /** 是否全部维度放行 */
    boolean allAllowed() {
      return ipAllowed && userAllowed && tenantAllowed;
    }
  }

  /**
   * P3-7: 三维度合并限流核心过滤器（单次 Redis 调用）
   *
   * <p>替代原有的 Mono.zip 三维度并行方案，将三个维度的令牌桶计算 合并到单次 Redis Lua 脚本调用，减少 66% 网络 IO。
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

    String clientIp = extractClientIp(request);
    String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
    String tenantId = request.getHeaders().getFirst(DataPermissionHeaderConstants.X_TENANT_ID);

    // P3-7: IP 白名单检查（合并脚本内不处理，避免白名单 IP 写入不必要的 Redis key）
    boolean ipWhitelisted =
        properties.getPerIp().getWhitelist() != null
            && clientIp != null
            && !clientIp.isEmpty()
            && properties.getPerIp().getWhitelist().contains(clientIp);

    // P3-7: 三维度合并限流检查（单次 Redis 调用）
    return executeMergedTokenBucket(exchange, clientIp, userId, tenantId, ipWhitelisted, path)
        .flatMap(
            result -> {
              if (result == null || result.allAllowed()) {
                return chain.filter(exchange);
              }
              // 按优先级返回第一个被拒绝的维度
              if (!result.ipAllowed()) {
                return rejectWithRateLimit(
                    exchange,
                    "IP",
                    clientIp,
                    properties.getPerIp().getDefaultQps(),
                    result.ipReset());
              }
              if (!result.userAllowed()) {
                return rejectWithRateLimit(
                    exchange, "USER", userId, resolveUserQps(exchange), result.userReset());
              }
              return rejectWithRateLimit(
                  exchange,
                  "TENANT",
                  tenantId,
                  properties.getPerTenant().getDefaultQps(),
                  result.tenantReset());
            });
  }

  /**
   * P3-7: 执行三维度合并令牌桶限流检查（单次 Redis 调用）
   *
   * @param exchange 服务器 Web 交换上下文
   * @param clientIp 客户端 IP
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param ipWhitelisted IP 是否在白名单中
   * @param path 请求路径（用于异常日志）
   * @return 三维度合并限流结果
   */
  private Mono<MergedRateLimitResult> executeMergedTokenBucket(
      ServerWebExchange exchange,
      String clientIp,
      String userId,
      String tenantId,
      boolean ipWhitelisted,
      String path) {
    // P0-3: Redis 熔断检查 — 连续失败超过阈值时走本地兜底
    if (redisFailureCount.get() >= CIRCUIT_THRESHOLD) {
      log.warn("[RateLimit] Redis 连续失败 {} 次，切换到本地兜底限流模式", redisFailureCount.get());
      return Mono.just(localFallbackMerged());
    }

    // 构造三维度参数
    boolean ipEnabled =
        properties.getPerIp().isEnabled()
            && !ipWhitelisted
            && clientIp != null
            && !clientIp.isEmpty();
    boolean userEnabled =
        properties.getPerUser().isEnabled() && userId != null && !userId.isEmpty();
    boolean tenantEnabled =
        properties.getPerTenant().isEnabled() && tenantId != null && !tenantId.isEmpty();

    // 三维度全部未启用（或无对应标识），直接放行
    if (!ipEnabled && !userEnabled && !tenantEnabled) {
      return Mono.just(new MergedRateLimitResult(true, 0, 0, true, 0, 0, true, 0, 0));
    }

    long now = System.currentTimeMillis() / 1000;
    String ipKey = "ydsz:ratelimit:ip:" + (clientIp != null ? clientIp : "");
    String userKey = "ydsz:ratelimit:user:" + (userId != null ? userId : "");
    String tenantKey = "ydsz:ratelimit:tenant:" + (tenantId != null ? tenantId : "");

    List<String> keys = List.of(ipKey, userKey, tenantKey);
    List<Object> args =
        Arrays.asList(
            String.valueOf(properties.getPerIp().getDefaultQps()),
            String.valueOf(properties.getPerIp().getBurstCapacity()),
            ipEnabled ? "1" : "0",
            String.valueOf(resolveUserQps(exchange)),
            String.valueOf(properties.getPerUser().getBurstCapacity()),
            userEnabled ? "1" : "0",
            String.valueOf(properties.getPerTenant().getDefaultQps()),
            String.valueOf(properties.getPerTenant().getBurstCapacity()),
            tenantEnabled ? "1" : "0",
            String.valueOf(now),
            "1");

    return redisTemplate
        .execute(mergedTokenBucketScript, keys, args)
        .next()
        .map(
            result -> {
              if (result == null || result.size() < 9) {
                redisFailureCount.incrementAndGet();
                return new MergedRateLimitResult(true, 0, 0, true, 0, 0, true, 0, 0);
              }
              // P0-1: 安全类型转换
              boolean ipAllowed = getLong(result, 0) != null && getLong(result, 0) == 1L;
              int ipRemaining = getLong(result, 1) != null ? getLong(result, 1).intValue() : 0;
              int ipReset = getLong(result, 2) != null ? getLong(result, 2).intValue() : 0;
              boolean userAllowed = getLong(result, 3) != null && getLong(result, 3) == 1L;
              int userRemaining = getLong(result, 4) != null ? getLong(result, 4).intValue() : 0;
              int userReset = getLong(result, 5) != null ? getLong(result, 5).intValue() : 0;
              boolean tenantAllowed = getLong(result, 6) != null && getLong(result, 6) == 1L;
              int tenantRemaining = getLong(result, 7) != null ? getLong(result, 7).intValue() : 0;
              int tenantReset = getLong(result, 8) != null ? getLong(result, 8).intValue() : 0;

              redisFailureCount.set(0);
              return new MergedRateLimitResult(
                  ipAllowed,
                  ipRemaining,
                  ipReset,
                  userAllowed,
                  userRemaining,
                  userReset,
                  tenantAllowed,
                  tenantRemaining,
                  tenantReset);
            })
        .onErrorResume(
            e -> {
              int count = redisFailureCount.incrementAndGet();
              log.warn(
                  "[RateLimit] Redis 限流检查异常 (连续 {} 次)，降级到本地兜底: path={} err={}",
                  count,
                  path,
                  e.getMessage());
              return Mono.just(localFallbackMerged());
            })
        .defaultIfEmpty(new MergedRateLimitResult(true, 0, 0, true, 0, 0, true, 0, 0));
  }

  /** P0-1: 安全地从 List 中获取 Long 值 */
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
   * P3-7: 本地兜底限流（Redis 不可用时的降级策略，三维度合并返回）
   *
   * <p>P1-2 分布式协调增强：本地兜底按网关实例数自适应分摊配额。
   */
  private MergedRateLimitResult localFallbackMerged() {
    if (gatewayMetrics != null) {
      gatewayMetrics.incrementRatelimitFallback();
    }
    int effectiveRate = resolveFallbackRate(properties.getPerIp().getDefaultQps());
    int effectiveCapacity =
        Math.max(
            1, properties.getPerIp().getBurstCapacity() / Math.max(1, getGatewayInstanceCount()));
    long now = System.currentTimeMillis() / 1000;
    long elapsed = now - localBucketLastRefill;
    long refill = elapsed * effectiveRate;
    long tokens = Math.min(effectiveCapacity, localBucketTokens + refill);

    boolean allowed = tokens >= 1;
    if (allowed) {
      tokens = tokens - 1;
    }
    localBucketTokens = tokens;
    localBucketLastRefill = now;

    if (gatewayMetrics != null) {
      gatewayMetrics.setRatelimitFallbackQuota(effectiveRate);
    }

    int reset =
        effectiveRate > 0
            ? (int) Math.ceil((double) (effectiveCapacity - tokens) / effectiveRate)
            : properties.getResponseHeaders().getRetryAfter();

    // 本地兜底模式：三个维度共享一个令牌桶（降级模式下保持行为一致）
    return new MergedRateLimitResult(
        allowed, (int) tokens, reset, allowed, (int) tokens, reset, allowed, (int) tokens, reset);
  }

  /** P1-2: 解析本地兜底的有效速率（按实例数分摊，下限 1） */
  private int resolveFallbackRate(int replenishRate) {
    int instanceCount = getGatewayInstanceCount();
    return instanceCount > 1 ? Math.max(1, replenishRate / instanceCount) : replenishRate;
  }

  /**
   * P1-2: 获取网关实例数（10 秒缓存，从 Nacos 服务发现读取）
   *
   * <p>服务发现不可用或异常时返回 1（退化为单机模式，保证可用性）。
   *
   * @return 当前网关服务实例数（>= 1）
   */
  private int getGatewayInstanceCount() {
    long now = System.currentTimeMillis();
    if (now - instanceCountFetchedAt < INSTANCE_COUNT_CACHE_MS) {
      return Math.max(1, cachedInstanceCount);
    }
    int count = 1;
    try {
      List<?> instances = discoveryClient.getInstances("ydsz-gateway");
      count = instances == null ? 1 : instances.size();
    } catch (Exception e) {
      log.warn("[RateLimit] 获取网关实例数失败，按单机处理: {}", e.getMessage());
    }
    cachedInstanceCount = count;
    instanceCountFetchedAt = now;
    return Math.max(1, count);
  }

  /** 根据用户角色解析 QPS 限制 */
  private int resolveUserQps(ServerWebExchange exchange) {
    String rolesHeader =
        exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_USER_ROLES);
    if (rolesHeader != null
        && !rolesHeader.isEmpty()
        && properties.getPerUser().getRoleLimits() != null) {
      String[] roles = rolesHeader.split(",");
      // 取用户拥有的最高权限角色的 QPS
      int maxQps = properties.getPerUser().getDefaultQps();
      for (String role : roles) {
        Integer roleQps = properties.getPerUser().getRoleLimits().get(role.trim());
        if (roleQps != null && roleQps > maxQps) {
          maxQps = roleQps;
        }
      }
      return maxQps;
    }
    return properties.getPerUser().getDefaultQps();
  }

  /**
   * P3-6: 返回 429 限流响应（带 RateLimit 响应头，使用 Lua 脚本返回的实际 reset 值）
   *
   * @param exchange 服务器 Web 交换上下文
   * @param dimension 限流维度
   * @param identity 限流标识
   * @param limit 限流配额
   * @param resetSeconds 重置时间（秒，由 Lua 脚本返回）
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectWithRateLimit(
      ServerWebExchange exchange, String dimension, String identity, int limit, int resetSeconds) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    // P3-6: 标准限流响应头，使用 Lua 脚本返回的实际 reset 值
    if (properties.getResponseHeaders().isEnabled()) {
      response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
      response.getHeaders().add("X-RateLimit-Remaining", "0");
      response.getHeaders().add("X-RateLimit-Reset", String.valueOf(resetSeconds));
      response.getHeaders().add("Retry-After", String.valueOf(resetSeconds));
    }

    // 记录限流指标
    if (gatewayMetrics != null) {
      gatewayMetrics.incrementRatelimitTriggered(
          dimension, exchange.getRequest().getURI().getPath());
    }

    BaseResponse<Void> body =
        BaseResponse.error(
            BaseResultCode.TOO_MANY_REQUESTS,
            "请求过于频繁，请稍后重试 (" + dimension + "=" + maskIdentity(identity) + ")");
    byte[] bytes = YdszJson.toJsonBytes(body);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);

    log.info(
        "[RateLimit] 限流触发: dimension={} identity={} path={} reset={}s",
        dimension,
        maskIdentity(identity),
        exchange.getRequest().getURI().getPath(),
        resetSeconds);
    return response.writeWith(Mono.just(buffer));
  }

  /** 提取客户端真实 IP（P0-3：复用 GatewayIpUtils 的可信代理链校验） */
  private String extractClientIp(ServerHttpRequest request) {
    return GatewayIpUtils.getClientIp(request);
  }

  /** 白名单路径判断（健康检查等不限流） */
  private boolean isWhitelistPath(String path) {
    return path != null
        && (path.startsWith("/actuator")
            || path.startsWith("/health")
            || path.equals("/auth/login")
            || path.equals("/auth/captcha")
            || path.equals("/auth/refresh"));
  }

  /** 身份标识脱敏（日志中不暴露完整 userId/IP） */
  private String maskIdentity(String identity) {
    if (identity == null || identity.length() <= 4) {
      return "***";
    }
    return identity.substring(0, 2) + "***" + identity.substring(identity.length() - 2);
  }

  /**
   * 过滤器顺序：在认证过滤器之后执行（需要 X-User-ID 头）
   *
   * <p>P1-9: 原为 +20，与 {@link com.njydsz.gateway.filter.GrayLoadBalancerRequestFilter} 冲突， 调整为
   * +30，确保灰度标识注入（+20）在限流之前完成。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.RATE_LIMIT.getOrder();
  }
}
