package com.njydsz.gateway.filter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.njydsz.common.safe.ratelimit.algorithm.RateLimiterFactory;
import com.njydsz.common.safe.ratelimit.cluster.RedisClusterRateLimiter;
import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.RateLimitProperties;
import com.njydsz.gateway.exception.GatewayErrorWriter;

/**
 * 限流全局过滤器。
 *
 * <p>基于 ydsz-common-safe 的 {@link RedisClusterRateLimiter} 实现令牌桶限流，支持 IP 和用户两个维度：
 *
 * <ul>
 *   <li>IP 级限流：防止单 IP 暴力请求
 *   <li>用户级限流：按用户 ID 限流
 * </ul>
 *
 * <p>内部委托 common-safe {@link RedisClusterRateLimiter#tryAcquire} 完成令牌桶判定，
 * 移除内联 Lua 脚本，算法实现收敛至 common-safe 模块。
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
 * <p>Redis 不可用时按 {@code fallbackOnError} 配置决定策略（默认 PASS=直接放行），保证可用性。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass({MeterRegistry.class, StringRedisTemplate.class})
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "rate-limit",
    havingValue = "true",
    matchIfMissing = true)
public class RateLimitFilter implements GlobalFilter, Ordered {

  /** 限流 Lua 脚本内联已移除，改用 common-safe {@link RedisClusterRateLimiter}。 */

  private final RateLimitProperties properties;
  private final StringRedisTemplate redisTemplate;
  private final GatewayMetrics gatewayMetrics;

  /** Redis 连续失败计数器（超过阈值时限流降级放行）。 */
  private static final int CIRCUIT_THRESHOLD = 5;

  /** Redis 不可用时的降级策略：PASS=放行（默认）。 */
  @Value("${ydsz.gateway.ratelimit.fallback-on-error:PASS}")
  private String fallbackOnError;

  /** Redis 集群限流器 key 前缀。 */
  @Value("${ydsz.gateway.ratelimit.key-prefix:ydsz:ratelimit:}")
  private String keyPrefix;

  /** 阻塞调用调度策略（boundedElastic，适配 common-safe 同步 Redis 调用）。 */
  private final Scheduler blockingScheduler = Schedulers.newBoundedElastic(
      50, 1000, "rate-limit-redis", 60, true);

  private final AtomicInteger redisFailureCount = new AtomicInteger(0);

  /** 集群限流器（IP 维度 + 用户维度共用，按 resource 区分 key）。 */
  private volatile RedisClusterRateLimiter clusterLimiter;

  /**
   * 获取或懒初始化集群限流器。
   *
   * <p>单例模式，配置变更需重启生效。
   *
   * @return 集群限流器实例
   */
  private RedisClusterRateLimiter getClusterLimiter() {
    if (clusterLimiter == null) {
      synchronized (this) {
        if (clusterLimiter == null) {
          clusterLimiter = new RedisClusterRateLimiter(
              redisTemplate,
              keyPrefix,
              fallbackOnError);
        }
      }
    }
    return clusterLimiter;
  }

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

    return executeRateLimit(exchange, clientIp, userId, ipWhitelisted)
        .flatMap(
            result -> {
              if (result == null || (result.ipAllowed && result.userAllowed)) {
                return chain.filter(exchange);
              }
              // 按优先级检查各维度限流：IP → USER
              if (!result.ipAllowed) {
                return rejectWithRateLimit(
                    exchange,
                    "IP",
                    clientIp,
                    properties.getPerIp().getDefaultQps(),
                    result.ipRemaining);
              }
              if (!result.userAllowed) {
                return rejectWithRateLimit(
                    exchange,
                    "USER",
                    userId,
                    properties.getPerUser().getDefaultQps(),
                    result.userRemaining);
              }
              return chain.filter(exchange);
            });
  }

  /**
   * 执行 IP + 用户二维度令牌桶限流。
   *
   * <p>通过 common-safe {@link RedisClusterRateLimiter} 的 {@link RateLimiterFactory} 机制
   * 创建临时规则并执行限流判定。阻塞 Redis 调用包装于 boundedElastic Scheduler 中，
   * 避免阻塞 Netty 事件循环。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param clientIp 客户端 IP
   * @param userId 用户 ID
   * @param ipWhitelisted IP 是否在白名单中
   * @return 限流结果 Mono
   */
  private Mono<GatewayRateLimitResult> executeRateLimit(
      ServerWebExchange exchange,
      String clientIp,
      String userId,
      boolean ipWhitelisted) {

    // Redis 熔断检查
    if (redisFailureCount.get() >= CIRCUIT_THRESHOLD) {
      log.warn("[RateLimit] Redis 连续失败 {} 次，限流降级放行", redisFailureCount.get());
      return Mono.just(allAllowedResult());
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

    // 将阻塞调用包装在 boundedElastic Scheduler 上执行
    return Mono.fromCallable(() -> doAcquire(ipEnabled, userEnabled, clientIp, userId))
        .subscribeOn(blockingScheduler)
        .onErrorResume(
            e -> {
              int count = redisFailureCount.incrementAndGet();
              log.warn("[RateLimit] Redis 限流检查异常 (连续 {} 次)，降级放行: path={} err={}",
                  count, exchange.getRequest().getURI().getPath(), e.getMessage());
              return Mono.just(allAllowedResult());
            })
        .defaultIfEmpty(allAllowedResult());
  }

  /**
   * 实际执行限流获取（在 blockingScheduler 上同步执行）。
   *
   * <p>分别构建 IP 维度和用户维度的 {@link RateLimitRule} 与 {@link RateLimitContext}，
   * 然后调用 {@link RedisClusterRateLimiter#tryAcquire} 获取判定结果。
   *
   * @param ipEnabled IP 维度是否启用
   * @param userEnabled 用户维度是否启用
   * @param clientIp 客户端 IP
   * @param userId 用户 ID
   * @return 限流结果
   */
  private GatewayRateLimitResult doAcquire(
      boolean ipEnabled, boolean userEnabled, String clientIp, String userId) {

    RedisClusterRateLimiter limiter = getClusterLimiter();

    boolean ipAllowed = true;
    int ipRemaining = 0;
    boolean userAllowed = true;
    int userRemaining = 0;

    // IP 维度限流
    if (ipEnabled) {
      RateLimitRule ipRule = buildIpRule(clientIp);
      RateLimitContext ipCtx = RateLimitContext.builder()
          .resource("ip:" + clientIp)
          .build();
      try {
        RateLimitDecision decision = limiter.tryAcquire(ipRule, ipCtx);
        ipAllowed = decision.getResult() == RateLimitResult.PASS;
        ipRemaining = decision.getRemaining() != null
            ? decision.getRemaining().intValue() : 0;
        redisFailureCount.set(0);
      } catch (Exception e) {
        redisFailureCount.incrementAndGet();
        ipAllowed = true;
        log.warn("[RateLimit] IP 维度限流异常，降级放行: ip={}", clientIp, e);
      }
    }

    // 用户维度限流
    if (userEnabled && ipAllowed) {
      RateLimitRule userRule = buildUserRule(userId);
      RateLimitContext userCtx = RateLimitContext.builder()
          .resource("user:" + userId)
          .build();
      try {
        RateLimitDecision decision = limiter.tryAcquire(userRule, userCtx);
        userAllowed = decision.getResult() == RateLimitResult.PASS;
        userRemaining = decision.getRemaining() != null
            ? decision.getRemaining().intValue() : 0;
        redisFailureCount.set(0);
      } catch (Exception e) {
        redisFailureCount.incrementAndGet();
        userAllowed = true;
        log.warn("[RateLimit] 用户维度限流异常，降级放行: userId={}", userId, e);
      }
    }

    return new GatewayRateLimitResult(ipAllowed, ipRemaining, userAllowed, userRemaining);
  }

  /**
   * 构建 IP 维度的限流规则。
   *
   * @param clientIp 客户端 IP
   * @return IP 维度限流规则
   */
  private RateLimitRule buildIpRule(String clientIp) {
    int qps = properties.getPerIp().getDefaultQps();
    int burstCapacity = properties.getPerIp().getBurstCapacity();
    return RateLimitRule.builder()
        .resource("ratelimit:ip:" + clientIp)
        .dimension(RateLimitDimension.IP)
        .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
        .mode(RateLimitMode.CLUSTER)
        .threshold(BigDecimal.valueOf(qps))
        .window(Duration.ofSeconds(1))
        .burstCapacity(burstCapacity)
        .build();
  }

  /**
   * 构建用户维度的限流规则。
   *
   * @param userId 用户 ID
   * @return 用户维度限流规则
   */
  private RateLimitRule buildUserRule(String userId) {
    int qps = properties.getPerUser().getDefaultQps();
    int burstCapacity = properties.getPerUser().getBurstCapacity();
    return RateLimitRule.builder()
        .resource("ratelimit:user:" + userId)
        .dimension(RateLimitDimension.USER)
        .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
        .mode(RateLimitMode.CLUSTER)
        .threshold(BigDecimal.valueOf(qps))
        .window(Duration.ofSeconds(1))
        .burstCapacity(burstCapacity)
        .build();
  }

  /**
   * 限流结果封装：IP + 用户两个维度的令牌桶判定结果。
   *
   * @param ipAllowed IP 维度是否放行
   * @param ipRemaining IP 维度剩余令牌数
   * @param userAllowed 用户维度是否放行
   * @param userRemaining 用户维度剩余令牌数
   */
  private record GatewayRateLimitResult(
      boolean ipAllowed,
      int ipRemaining,
      boolean userAllowed,
      int userRemaining) {}

  /** 全部维度放行的限流结果（未启用维度与异常降级时使用） */
  private GatewayRateLimitResult allAllowedResult() {
    gatewayMetrics.incrementRatelimitFallback();
    return new GatewayRateLimitResult(true, 0, true, 0);
  }

  /**
   * 返回 429 限流响应。
   *
   * <p>通过 {@link GatewayErrorWriter} 写出统一错误响应。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param dimension 限流维度
   * @param identity 限流标识
   * @param limit 限流配额
   * @param remainingSeconds 重置时间（秒）
   * @return 完成信号 Mono
   */
  private Mono<Void> rejectWithRateLimit(
      ServerWebExchange exchange,
      String dimension,
      String identity,
      int limit,
      int remainingSeconds) {
    // 限流响应头（X-RateLimit-* / Retry-After / 绝对时间戳）
    if (properties.getResponseHeaders().isEnabled()) {
      ServerHttpResponse response = exchange.getResponse();
      response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
      response.getHeaders().add("X-RateLimit-Remaining", "0");
      response.getHeaders().add("X-RateLimit-Reset", String.valueOf(remainingSeconds));
      // Retry-After 同时提供相对秒数和绝对时间戳（RFC 9110 / ISO 8601）
      response.getHeaders().add("Retry-After", String.valueOf(remainingSeconds));
      Instant resetAt = Instant.now().plus(remainingSeconds, ChronoUnit.SECONDS);
      response.getHeaders().add("X-RateLimit-Reset-Time", resetAt.toString());
    }

    gatewayMetrics.incrementRatelimitTriggered(dimension, exchange.getRequest().getURI().getPath());

    GatewayErrorCode errorCode = resolveRateLimitErrorCode(dimension);
    log.info("[RateLimit] 限流触发: dimension={} identity={} path={}",
        dimension, maskIdentity(identity), exchange.getRequest().getURI().getPath());

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
