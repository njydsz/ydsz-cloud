package com.njydsz.pmis.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.gateway.config.GatewayConstants;
import com.njydsz.pmis.gateway.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;

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
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;

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

    /** 预编译 Lua 脚本 */
    @SuppressWarnings("unchecked")
    private final RedisScript<List<Long>> tokenBucketScript = RedisScript.of(
            new ByteArrayResource(TOKEN_BUCKET_SCRIPT.getBytes(StandardCharsets.UTF_8)),
            (Class<List<Long>>) (Class<?>) List.class
    );

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
        String tenantId = request.getHeaders().getFirst("X-Tenant-Id");

        // 依次检查各维度限流
        return checkIpRateLimit(exchange, clientIp)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return rejectWithRateLimit(exchange, "IP", clientIp, properties.getPerIp().getDefaultQps());
                    }
                    return checkUserRateLimit(exchange, userId)
                            .flatMap(userAllowed -> {
                                if (!userAllowed) {
                                    return rejectWithRateLimit(exchange, "USER", userId, properties.getPerUser().getDefaultQps());
                                }
                                if (properties.getPerTenant().isEnabled() && tenantId != null) {
                                    return checkTenantRateLimit(exchange, tenantId)
                                            .flatMap(tenantAllowed -> {
                                                if (!tenantAllowed) {
                                                    return rejectWithRateLimit(exchange, "TENANT", tenantId, properties.getPerTenant().getDefaultQps());
                                                }
                                                return chain.filter(exchange);
                                            });
                                }
                                return chain.filter(exchange);
                            });
                });
    }

    /**
     * IP 级限流检查
     */
    private Mono<Boolean> checkIpRateLimit(ServerWebExchange exchange, String clientIp) {
        if (!properties.getPerIp().isEnabled() || clientIp == null) {
            return Mono.just(true);
        }

        // IP 白名单
        if (properties.getPerIp().getWhitelist() != null
                && properties.getPerIp().getWhitelist().contains(clientIp)) {
            return Mono.just(true);
        }

        String key = "pmis:ratelimit:ip:" + clientIp;
        return executeTokenBucket(key, properties.getPerIp().getDefaultQps(),
                properties.getPerIp().getBurstCapacity());
    }

    /**
     * 用户级限流检查
     */
    private Mono<Boolean> checkUserRateLimit(ServerWebExchange exchange, String userId) {
        if (!properties.getPerUser().isEnabled() || userId == null || userId.isEmpty()) {
            return Mono.just(true);
        }

        int qps = resolveUserQps(exchange);
        String key = "pmis:ratelimit:user:" + userId;
        return executeTokenBucket(key, qps, properties.getPerUser().getBurstCapacity());
    }

    /**
     * 租户级限流检查
     */
    private Mono<Boolean> checkTenantRateLimit(ServerWebExchange exchange, String tenantId) {
        if (!properties.getPerTenant().isEnabled() || tenantId == null) {
            return Mono.just(true);
        }

        String key = "pmis:ratelimit:tenant:" + tenantId;
        return executeTokenBucket(key, properties.getPerTenant().getDefaultQps(),
                properties.getPerTenant().getBurstCapacity());
    }

    /**
     * 执行令牌桶限流检查
     *
     * @param key           Redis 键
     * @param replenishRate 每秒填充速率
     * @param burstCapacity 突发容量
     * @return true=允许；false=限流
     */
    private Mono<Boolean> executeTokenBucket(String key, int replenishRate, int burstCapacity) {
        long now = System.currentTimeMillis() / 1000;
        List<String> keys = List.of(key);
        List<Object> args = Arrays.asList(
                String.valueOf(replenishRate),
                String.valueOf(burstCapacity),
                String.valueOf(now),
                "1"  // 每次请求消耗 1 个令牌
        );

        return redisTemplate.execute(tokenBucketScript, keys, args)
                .next()
                .map(result -> {
                    if (result == null || result.size() < 1) {
                        return true; // Redis 异常时降级放行
                    }
                    Long allowed = result.get(0);
                    return allowed != null && allowed == 1L;
                })
                .onErrorResume(e -> {
                    log.warn("[RateLimit] Redis 限流检查异常，降级放行: key={} err={}", key, e.getMessage());
                    return Mono.just(true);
                })
                .defaultIfEmpty(true);
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
     * 返回 429 限流响应（带 RateLimit 响应头）
     */
    private Mono<Void> rejectWithRateLimit(ServerWebExchange exchange, String dimension, String identity, int limit) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // P2-15: 标准限流响应头
        if (properties.getResponseHeaders().isEnabled()) {
            response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
            response.getHeaders().add("X-RateLimit-Remaining", "0");
            response.getHeaders().add("X-RateLimit-Reset", String.valueOf(properties.getResponseHeaders().getRetryAfter()));
            response.getHeaders().add("Retry-After", String.valueOf(properties.getResponseHeaders().getRetryAfter()));
        }

        BaseResponse<Void> body = BaseResponse.failed("429", "请求过于频繁，请稍后重试 (" + dimension + "=" + maskIdentity(identity) + ")");
        byte[] bytes = JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        log.info("[RateLimit] 限流触发: dimension={} identity={} path={}",
                dimension, maskIdentity(identity), exchange.getRequest().getURI().getPath());
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 提取客户端真实 IP（穿透代理）
     */
    private String extractClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
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
