package com.remisoft.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.common.json.RemiJson;

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

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.gateway.config.GatewayConstants;
import com.remisoft.gateway.config.GatewayIpUtils;
import com.remisoft.gateway.config.GatewayMetrics;
import com.remisoft.gateway.config.RateLimitProperties;
import com.remisoft.common.core.code.BaseResultCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * P2-15: 精细化限流全局过滤器
 *
 * <p>基于 Redis + Lua 脚本实现的令牌桶限流，支持多维度：
 * <ul>
 *   <li>用户级限流（按 X-User-ID）</li>
 *   <li>IP 级限流（按客户端 IP）</li>
 *   <li>租户级限流（按 X-Tenant-Id）</li>
 * </ul>
 *
 * <h3>令牌桶算法</h3>
 * <p>使用 Redis Lua 脚本保证原子性：
 * <ol>
 *   <li>以固定速率向桶中添加令牌（replenishRate）</li>
 *   <li>桶容量有限（burstCapacity），超出则丢弃</li>
 *   <li>每次请求消耗 1 个令牌，桶空时拒绝</li>
 * </ol>
 *
 * <h3>限流维度优先级</h3>
 * <ol>
 *   <li>IP 级（最先检查，防止单 IP 暴力请求）</li>
 *   <li>用户级（按 userId 限流）</li>
 *   <li>租户级（按 tenantId 限流）</li>
 * </ol>
 * 任一维度触发限流即返回 429。
 *
 * <h3>响应头</h3>
 * <p>限流触发时返回标准响应头：
 * <ul>
 *   <li>{@code X-RateLimit-Limit}: 总配额</li>
 *   <li>{@code X-RateLimit-Remaining}: 剩余配额</li>
 *   <li>{@code X-RateLimit-Reset}: 重置时间（秒）</li>
 *   <li>{@code Retry-After}: 建议重试等待时间（秒）</li>
 * </ul>
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayMetrics gatewayMetrics;
    private final DiscoveryClient discoveryClient;

    /**
     * P0-3: Redis 连续失败计数器，超过阈值切换本地兜底
     */
    private static final int CIRCUIT_THRESHOLD = 5;
    private final AtomicInteger redisFailureCount = new AtomicInteger(0);

    /**
     * P0-3: 本地兜底限流状态（Redis 不可用时的降级）
     */
    private volatile long localBucketTokens = 200;
    private volatile long localBucketLastRefill = System.currentTimeMillis() / 1000;

    /**
     * P1-2: 网关实例数缓存（10 秒刷新，避免频繁调用 Nacos 服务发现）
     */
    private static final long INSTANCE_COUNT_CACHE_MS = 10_000;
    private volatile int cachedInstanceCount = 1;
    private volatile long instanceCountFetchedAt = 0;

    /**
     * 令牌桶 Lua 脚本
     *
     * 参数: KEYS[1]=redis_key, ARGV[1]=replenishRate, ARGV[2]=burstCapacity, ARGV[3]=timestamp_seconds, ARGV[4]=requested_tokens
     * 返回: {allowed(1/0), remaining_tokens, reset_seconds}
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local key = KEYS[1]
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
            return {allowed, remaining, reset}
            """;

    /**
     * P0-1: 预编译 Lua 脚本（移除 @SuppressWarnings）
     * <p>使用 List.class 而非 List<Long>.class（泛型擦除后等价），
     * 避免未经检查的强制类型转换。
     */
    private final RedisScript<List> tokenBucketScript = RedisScript.of(
            new ByteArrayResource(TOKEN_BUCKET_SCRIPT.getBytes(StandardCharsets.UTF_8)),
            List.class
    );

    /**
     * 精细化限流核心过滤器：多维度令牌桶限流（IP / 用户 / 租户）。
     *
     * <p>限流关闭或白名单路径直接放行 → 提取客户端 IP / 用户 / 租户标识 →
     * 三维度并行执行 Redis Lua 令牌桶检查，任一维度拒绝即返回 429。
     * Redis 连续失败超过阈值时切换本地兜底令牌桶（P0-3）。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
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
        String tenantId = request.getHeaders().getFirst(HeaderConstants.X_TENANT_ID);

        // P2-5: 三维度并行检查，避免嵌套 flatMap 回调地狱
        Mono<RateLimitResult> ipLimit = checkRateLimit("IP", clientIp,
                properties.getPerIp().isEnabled(), properties.getPerIp().getDefaultQps(),
                properties.getPerIp().getBurstCapacity(), "remi:ratelimit:ip:");

        Mono<RateLimitResult> userLimit = checkRateLimit("USER", userId,
                properties.getPerUser().isEnabled(), resolveUserQps(exchange),
                properties.getPerUser().getBurstCapacity(), "remi:ratelimit:user:");

        Mono<RateLimitResult> tenantLimit = Mono.just(new RateLimitResult(true, 0, 0));
        if (properties.getPerTenant().isEnabled() && tenantId != null) {
            tenantLimit = checkRateLimit("TENANT", tenantId,
                    properties.getPerTenant().isEnabled(),
                    properties.getPerTenant().getDefaultQps(),
                    properties.getPerTenant().getBurstCapacity(), "remi:ratelimit:tenant:");
        }

        // 并行执行三维度检查，任一失败即拒绝
        return Mono.zip(ipLimit, userLimit, tenantLimit)
                .flatMap(tuple -> {
                    RateLimitResult ip = tuple.getT1();
                    RateLimitResult user = tuple.getT2();
                    RateLimitResult tenant = tuple.getT3();

                    if (!ip.allowed()) {
                        return rejectWithRateLimit(exchange, "IP", clientIp,
                                properties.getPerIp().getDefaultQps(), ip.resetSeconds());
                    }
                    if (!user.allowed()) {
                        return rejectWithRateLimit(exchange, "USER", userId,
                                resolveUserQps(exchange), user.resetSeconds());
                    }
                    if (!tenant.allowed()) {
                        return rejectWithRateLimit(exchange, "TENANT", tenantId,
                                properties.getPerTenant().getDefaultQps(), tenant.resetSeconds());
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * P2-5: 统一限流检查方法（替代原有三个独立方法）
     *
     * @param dimension       限流维度（IP / USER / TENANT）
     * @param identity        限流标识
     * @param dimensionEnabled 该维度是否启用
     * @param replenishRate   每秒填充速率
     * @param burstCapacity   突发容量
     * @param keyPrefix       Redis 键前缀
     * @return 限流结果
     */
    private Mono<RateLimitResult> checkRateLimit(String dimension, String identity,
                                                 boolean dimensionEnabled,
                                                 int replenishRate, int burstCapacity,
                                                 String keyPrefix) {
        if (!dimensionEnabled || identity == null || identity.isEmpty()) {
            return Mono.just(new RateLimitResult(true, 0, 0));
        }

        // IP 白名单检查
        if ("IP".equals(dimension) && properties.getPerIp().getWhitelist() != null
                && properties.getPerIp().getWhitelist().contains(identity)) {
            return Mono.just(new RateLimitResult(true, 0, 0));
        }

        String key = keyPrefix + identity;
        return executeTokenBucket(key, replenishRate, burstCapacity);
    }

    /**
     * 执行令牌桶限流检查
     *
     * <p>P0-3: 当 Redis 连续失败超过阈值时切换到本地兜底模式。
     * P0-1: 安全类型转换避免 unchecked cast。
     * P3-6: 返回 RateLimitResult 携带实际 reset 值。
     *
     * @param key           Redis 键
     * @param replenishRate 每秒填充速率
     * @param burstCapacity 突发容量
     * @return 限流结果
     */
    private Mono<RateLimitResult> executeTokenBucket(String key, int replenishRate, int burstCapacity) {
        // P0-3: Redis 熔断检查 — 连续失败超过阈值时走本地兜底
        if (redisFailureCount.get() >= CIRCUIT_THRESHOLD) {
            log.warn("[RateLimit] Redis 连续失败 {} 次，切换到本地兜底限流模式", redisFailureCount.get());
            return Mono.just(localFallback(replenishRate, burstCapacity));
        }

        long now = System.currentTimeMillis() / 1000;
        List<String> keys = List.of(key);
        List<Object> args = Arrays.asList(
                String.valueOf(replenishRate),
                String.valueOf(burstCapacity),
                String.valueOf(now),
                "1"
        );

        return redisTemplate.execute(tokenBucketScript, keys, args)
                .next()
                .map(result -> {
                    if (result == null || result.isEmpty()) {
                        redisFailureCount.incrementAndGet();
                        return new RateLimitResult(true, 0, 0);
                    }
                    // P0-1: 安全类型转换
                    Long allowed = getLong(result, 0);
                    Long remaining = getLong(result, 1);
                    Long reset = getLong(result, 2);
                    boolean allow = allowed != null && allowed == 1L;
                    redisFailureCount.set(0);
                    return new RateLimitResult(allow,
                            remaining != null ? remaining.intValue() : 0,
                            reset != null ? reset.intValue() : 0);
                })
                .onErrorResume(e -> {
                    int count = redisFailureCount.incrementAndGet();
                    log.warn("[RateLimit] Redis 限流检查异常 (连续 {} 次)，降级到本地兜底: key={} err={}",
                            count, key, e.getMessage());
                    return Mono.just(localFallback(replenishRate, burstCapacity));
                })
                .defaultIfEmpty(new RateLimitResult(true, 0, 0));
    }

    /**
     * P0-1: 安全地从 List 中获取 Long 值
     */
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
     * P0-3: 本地兜底限流（Redis 不可用时的降级策略）
     *
     * <p>P1-2 分布式协调增强：本地兜底按网关实例数自适应分摊配额。
     * Redis 故障时无法共享计数（共享存储已不可用），行业标准做法是
     * 各实例按 {@code replenishRate / instanceCount} 分摊，使集群总限流
     * 效果接近配置值，避免"N 个实例 = N 倍配额"的稀释问题。
     */
    private RateLimitResult localFallback(int replenishRate, int burstCapacity) {
        // P1-2: 降级模式指标（Grafana 据此告警"限流降级中，请检查 Redis"）
        if (gatewayMetrics != null) {
            gatewayMetrics.incrementRatelimitFallback();
        }
        int effectiveRate = resolveFallbackRate(replenishRate);
        int effectiveCapacity = Math.max(1, burstCapacity / Math.max(1, getGatewayInstanceCount()));
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

        int reset = effectiveRate > 0
                ? (int) Math.ceil((double) (effectiveCapacity - tokens) / effectiveRate)
                : properties.getResponseHeaders().getRetryAfter();
        return new RateLimitResult(allowed, (int) tokens, reset);
    }

    /**
     * P1-2: 解析本地兜底的有效速率（按实例数分摊，下限 1）
     */
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
            List<?> instances = discoveryClient.getInstances("remi-gateway");
            count = instances == null ? 1 : instances.size();
        } catch (Exception e) {
            log.warn("[RateLimit] 获取网关实例数失败，按单机处理: {}", e.getMessage());
        }
        cachedInstanceCount = count;
        instanceCountFetchedAt = now;
        return Math.max(1, count);
    }

    /**
     * P3-6: 限流结果记录（携带 Lua 脚本返回的实际 reset 值）
     */
    private record RateLimitResult(boolean allowed, int remainingTokens, int resetSeconds) {
    }

    /**
     * 根据用户角色解析 QPS 限制
     */
    private int resolveUserQps(ServerWebExchange exchange) {
        String rolesHeader = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_USER_ROLES);
        if (rolesHeader != null && !rolesHeader.isEmpty() && properties.getPerUser().getRoleLimits() != null) {
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
     * @param exchange     服务器 Web 交换上下文
     * @param dimension    限流维度
     * @param identity     限流标识
     * @param limit        限流配额
     * @param resetSeconds  重置时间（秒，由 Lua 脚本返回）
     * @return 完成信号 Mono
     */
    private Mono<Void> rejectWithRateLimit(ServerWebExchange exchange, String dimension,
                                           String identity, int limit, int resetSeconds) {
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
            gatewayMetrics.incrementRatelimitTriggered(dimension,
                    exchange.getRequest().getURI().getPath());
        }

        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.RATE_LIMIT,
                "请求过于频繁，请稍后重试 (" + dimension + "=" + maskIdentity(identity) + ")");
        byte[] bytes = RemiJson.toJson(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        log.info("[RateLimit] 限流触发: dimension={} identity={} path={} reset={}s",
                dimension, maskIdentity(identity), exchange.getRequest().getURI().getPath(), resetSeconds);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 提取客户端真实 IP（P0-3：复用 GatewayIpUtils 的可信代理链校验）
     */
    private String extractClientIp(ServerHttpRequest request) {
        return GatewayIpUtils.getClientIp(request);
    }

    /**
     * 白名单路径判断（健康检查等不限流）
     */
    private boolean isWhitelistPath(String path) {
        return path != null && (
                path.startsWith("/actuator") ||
                path.startsWith("/health") ||
                path.equals("/auth/login") ||
                path.equals("/auth/captcha") ||
                path.equals("/auth/refresh")
        );
    }

    /**
     * 身份标识脱敏（日志中不暴露完整 userId/IP）
     */
    private String maskIdentity(String identity) {
        if (identity == null || identity.length() <= 4) {
            return "***";
        }
        return identity.substring(0, 2) + "***" + identity.substring(identity.length() - 2);
    }

    /**
     * 过滤器顺序：在认证过滤器之后执行（需要 X-User-ID 头）
     *
     * <p>P1-9: 原为 +20，与 {@link GrayLoadBalancerRequestFilter} 冲突，
     * 调整为 +30，确保灰度标识注入（+20）在限流之前完成。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
