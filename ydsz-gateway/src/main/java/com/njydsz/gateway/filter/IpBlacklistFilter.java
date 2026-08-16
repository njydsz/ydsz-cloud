package com.njydsz.gateway.filter;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import reactor.core.publisher.Mono;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;

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
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "ip-blacklist", havingValue = "true", matchIfMissing = true)
public class IpBlacklistFilter implements GlobalFilter, Ordered {

    /** Redis IP 黑名单键前缀 */
    private static final String IP_BLACKLIST_PREFIX = "ydsz:ip:blacklist:";

    /** L1 本地缓存 TTL（秒） */
    private static final long LOCAL_CACHE_TTL_SECONDS = 10;

    /** L1 本地缓存最大容量 */
    private static final long LOCAL_CACHE_MAX_SIZE = 50_000;

    /** L1 本地缓存：IP → 是否在黑名单中 */
    private final Cache<String, Boolean> localCache = YdszCache.<String, Boolean>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(LOCAL_CACHE_MAX_SIZE)
            .build();

    /** Redis 响应式模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * P0-3 修复：Redis 故障时的降级策略。
     * <ul>
     *   <li>{@code fail-open}（默认）：Redis 异常时放行，保证可用性</li>
     *   <li>{@code fail-closed}：Redis 异常时拒绝，保证安全性（生产环境推荐）</li>
     * </ul>
     */
    @Value("${ydsz.gateway.ip-blacklist.fail-mode:fail-open}")
    private String failMode;

    /**
     * IP 黑名单拦截过滤器：基于本地 + Redis 两级缓存拒绝恶意 IP。
     *
     * <p>无法获取客户端 IP 时放行；先查 L1 本地缓存（TTL 10s，命中即拒绝或放行），
     * 未命中再查 Redis 黑名单键并回填本地缓存；Redis 异常时降级放行。
     * 黑名单优先于白名单（顺序 +3 < 白名单 +5）。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 放行或拒绝（403）的完成信号 Mono
     */
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
            // P2-11: 黑名单命中 → sentry 告警收敛（P2 安全事件）
            SentryObservation.alert(AlertEvent.builder()
                    .name("gateway.ip_blacklist.hit")
                    .severity(AlertSeverity.P2)
                    .summary("IP 黑名单命中（L1 缓存）")
                    .description("恶意 IP 请求被网关拦截")
                    .category("security")
                    .labels(Map.of("ip", clientIp,
                            "path", request.getURI().getPath(),
                            "cache_level", "L1"))
                    .build());
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
                        // P2-11: 黑名单命中 → sentry 告警收敛（P2 安全事件）
                        SentryObservation.alert(AlertEvent.builder()
                                .name("gateway.ip_blacklist.hit")
                                .severity(AlertSeverity.P2)
                                .summary("IP 黑名单命中（L2 Redis）")
                                .description("恶意 IP 请求被网关拦截")
                                .category("security")
                                .labels(Map.of("ip", clientIp,
                                        "path", request.getURI().getPath(),
                                        "cache_level", "L2"))
                                .build());
                        log.warn("[IpBlacklist] L2 命中黑名单 ip={} path={}", clientIp, request.getURI().getPath());
                        return forbidden(exchange, clientIp);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    // P0-3 修复：根据 fail-mode 决定 Redis 异常时的降级策略
                    if ("fail-closed".equalsIgnoreCase(failMode)) {
                        log.warn("[IpBlacklist] Redis 查询异常，fail-closed 拒绝 ip={} err={}", clientIp, e.getMessage());
                        return forbidden(exchange, clientIp);
                    }
                    log.warn("[IpBlacklist] Redis 查询异常，fail-open 降级放行 ip={} err={}", clientIp, e.getMessage());
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
        String traceId = TraceIdGenerator.generateSortableTraceId();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.FORBIDDEN, "error.IP_BLACKLISTED");
        body.assignTraceId(traceId);
        byte[] bytes = YdszJson.toJsonBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器执行顺序：{@code HIGHEST_PRECEDENCE + 3}。
     *
     * <p>先于 IP 白名单（+5）执行，确保恶意 IP 即便在白名单中也优先被拒绝。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return GatewayFilterOrder.IP_BLACKLIST.getOrder();
    }
}
