package com.njydsz.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayIpUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * IP 黑名单全局过滤器（P2-11）
 *
 * <p>基于 Redis + ydsz-common-cache 二级缓存的动态 IP 黑名单。
 *
 * <h3>两级缓存架构</h3>
 * <ol>
 *   <li>L1: ydsz-common-cache 本地缓存（TTL=10s）— 拦截 99% 的恶意 IP 请求，无网络开销</li>
 *   <li>L2: Redis 远程缓存 — 多实例共享黑名单，运维或安全系统动态写入</li>
 * </ol>
 *
 * <h3>Redis 键设计</h3>
 * <pre>
 *   ydsz:ip:blacklist:{ip}  → 1   (TTL: 可配置，默认 24h)
 * </pre>
 *
 * <h3>黑名单写入方式</h3>
 * <ul>
 *   <li>安全系统自动写入（检测到暴力破解 / CC 攻击）</li>
 *   <li>运维通过 Redis CLI 手动写入：{@code SET ydsz:ip:blacklist:1.2.3.4 1 EX 86400}</li>
 *   <li>未来可通过管理后台 API 写入</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 3}，在 {@link IpWhitelistFilter}(+5) 之前执行，
 * 黑名单优先于白名单检查（恶意 IP 即使在白名单中也应被拒绝）。
 *
 * @since 2.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpBlacklistFilter implements GlobalFilter, Ordered {

    /** Redis IP 黑名单键前缀 */
    private static final String IP_BLACKLIST_PREFIX = "ydsz:ip:blacklist:";

    /** L1 本地缓存 TTL（秒） */
    private static final long LOCAL_CACHE_TTL_SECONDS = 10;

    /** L1 本地缓存最大容量 */
    private static final long LOCAL_CACHE_MAX_SIZE = 50_000;

    /** L1 本地缓存：IP → 是否在黑名单中 */
    private final Cache<String, Boolean> localCache = YdszCache.<String, Boolean>newBuilder()
            .type(CacheType.TTL)
            .expireAfterWrite(LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(LOCAL_CACHE_MAX_SIZE)
            .build();

    /** Redis 响应式模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = GatewayIpUtils.getClientIp(request);

        // 无法获取 IP 则放行
        if (clientIp.isEmpty()) {
            return chain.filter(exchange);
        }

        // L1: 先查本地缓存
        Boolean cached = localCache.getIfPresent(clientIp);
        if (Boolean.TRUE.equals(cached)) {
            log.warn("[IpBlacklist] L1 命中黑名单 ip={} path={}", clientIp, request.getURI().getPath());
            return forbidden(exchange, clientIp);
        }
        if (cached != null) {
            // cached == false，确认不在黑名单
            return chain.filter(exchange);
        }

        // L2: 查 Redis
        return redisTemplate.hasKey(IP_BLACKLIST_PREFIX + clientIp)
                .defaultIfEmpty(false)
                .flatMap(blacklisted -> {
                    // 写入 L1 缓存
                    localCache.put(clientIp, blacklisted);

                    if (Boolean.TRUE.equals(blacklisted)) {
                        log.warn("[IpBlacklist] L2 命中黑名单 ip={} path={}", clientIp, request.getURI().getPath());
                        return forbidden(exchange, clientIp);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.warn("[IpBlacklist] Redis 查询异常，降级放行 ip={} err={}", clientIp, e.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 返回 403 禁止访问响应
     *
     * @param exchange 服务器 Web 交换上下文
     * @param clientIp 客户端 IP
     * @return 完成信号 Mono
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String clientIp) {
        String traceId = TraceIdGenerator.generate();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        BaseResponse<Void> body = BaseResponse.failed("403", "error.IP_BLACKLISTED");
        body.setTraceId(traceId);
        byte[] bytes = YdszJson.toJson(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
