package com.njydsz.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.loadbalancer.GrayLoadBalancer;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 灰度路由请求过滤器
 *
 * <p>在网关路由前注入灰度标识,供 {@link GrayLoadBalancer} 按灰度规则分发流量。
 *
 * <h3>灰度标识解析优先级(从高到低)</h3>
 * <ol>
 *   <li>请求头 {@code X-Gray-Tag}(值: {@code gray} / {@code stable})</li>
 *   <li>查询参数 {@code gray=true}(命中则灰度,{@code gray=false} 则稳定)</li>
 *   <li>路径模式 {@code /canary/**}(自动走灰度)</li>
 *   <li>P1-3: 比例灰度 — 当用户身份信息存在时，按 {@code gray-ratio-percent} 自动计算灰度归属</li>
 * </ol>
 *
 * <h3>用户粘性（一致性哈希）</h3>
 * <p>P1-3: 比例灰度模式下，基于 userId（认证用户）或 traceId（未认证）计算哈希，
 * 确保同一用户始终路由到灰度或稳定实例组，避免用户在不同刷新间"跳跃"。
 *
 * <h3>标识写入位置</h3>
 * <ul>
 *   <li>exchange attribute {@code X-Gray-Tag}(供 LoadBalancer 通过 RequestData 读取)</li>
 *   <li>请求头 {@code X-Gray-Tag}(确保下游服务可读取,且不被 AuthGlobalFilter 剥离)</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} + 20,晚于 {@link AuthGlobalFilter}(+10),
 * 确保 AuthFilter 完成鉴权后再注入灰度标识,避免白名单请求干扰。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "gray-loadbalancer", havingValue = "true", matchIfMissing = true)
public class GrayLoadBalancerRequestFilter implements GlobalFilter, Ordered {

    /** 灰度标识值:灰度 */
    private static final String GRAY_TAG_GRAY = "gray";
    /** 灰度标识值:稳定 */
    private static final String GRAY_TAG_STABLE = "stable";

    /** 查询参数名:gray */
    private static final String QUERY_PARAM_GRAY = "gray";

    /** 灰度路径前缀:匹配此路径自动走灰度 */
    private static final String CANARY_PATH_PREFIX = "/canary/";

    /**
     * P1-3: 比例灰度阈值（百分比）。
     *
     * <p>默认 0（禁用比例灰度，仅通过 X-Gray-Tag 显式灰度）。
     * 配置为 10 表示 10% 的流量按比例自动路由到灰度实例。
     * 基于 userId（已认证）或 traceId（未认证）的一致性哈希，保证用户粘性。
     */
    @Value("${ydsz.gateway.gray.ratio-percent:0}")
    private int grayRatioPercent;

    /**
     * 过滤逻辑:解析灰度标识 → 写入 exchange attribute 与请求头 → 转发
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String grayTag = resolveGrayTag(request);

        // 写入 exchange attribute,供 GrayLoadBalancer 通过 RequestData.getAttributes() 读取
        if (grayTag != null) {
            exchange.getAttributes().put(GrayLoadBalancer.GRAY_TAG_HEADER, grayTag);
        }

        // 若请求头缺失 X-Gray-Tag 但已解析出灰度标识,则补写请求头
        // 确保下游服务可读取,且 ReactiveLoadBalancerClientFilter 构造 RequestData 时能携带
        if (grayTag != null
                && request.getHeaders().getFirst(GrayLoadBalancer.GRAY_TAG_HEADER) == null) {
            ServerHttpRequest mutated = request.mutate()
                    .header(GrayLoadBalancer.GRAY_TAG_HEADER, grayTag)
                    .build();
            if (log.isDebugEnabled()) {
                log.debug("[GrayFilter] 路径 {} 注入灰度标识 {} (header 已补写)",
                        request.getURI().getPath(), grayTag);
            }
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        if (log.isDebugEnabled() && grayTag != null) {
            log.debug("[GrayFilter] 路径 {} 灰度标识 {} (header 已存在)",
                    request.getURI().getPath(), grayTag);
        }
        return chain.filter(exchange);
    }

    /**
     * 解析灰度标识
     *
     * <p>解析顺序:请求头 → 查询参数 → 路径模式
     *
     * @param request 服务器 HTTP 请求
     * @return 灰度标识({@code gray} / {@code stable} / {@code null}=未指定)
     */
    private String resolveGrayTag(ServerHttpRequest request) {
        // 1. 优先读取请求头 X-Gray-Tag
        String headerTag = request.getHeaders().getFirst(GrayLoadBalancer.GRAY_TAG_HEADER);
        if (headerTag != null && !headerTag.isEmpty()) {
            return headerTag;
        }

        // 2. 检查查询参数 gray=true / gray=false
        String grayParam = request.getQueryParams().getFirst(QUERY_PARAM_GRAY);
        if ("true".equalsIgnoreCase(grayParam)) {
            return GRAY_TAG_GRAY;
        }
        if ("false".equalsIgnoreCase(grayParam)) {
            return GRAY_TAG_STABLE;
        }

        // 3. 检查路径模式 /canary/** 自动走灰度
        String path = request.getURI().getPath();
        if (path != null && path.startsWith(CANARY_PATH_PREFIX)) {
            return GRAY_TAG_GRAY;
        }

        // 4. P1-3: 比例灰度 — 基于 userId 或 traceId 的一致性哈希，保证用户粘性
        if (grayRatioPercent > 0) {
            return resolveRatioGrayTag(request);
        }

        return null;
    }

    /**
     * P1-3: 基于比例的用户粘性灰度路由。
     *
     * <p>使用 userId（已认证用户）或 traceId（未认证请求）对 100 取模，
     * 若结果小于 grayRatioPercent 则路由到灰度实例。同一用户/traceId 的哈希值稳定，
     * 保证用户不会在多次请求间在灰度和稳定实例组之间"跳跃"。
     *
     * <p>灰色地带：未被灰度命中的用户始终走稳定实例，
     * 灰度实例故障时灰度命中的用户也会受影响（通过 GrayLoadBalancer 降级兜底）。
     *
     * @param request HTTP 请求
     * @return 灰度标识
     */
    private String resolveRatioGrayTag(ServerHttpRequest request) {
        // 优先使用已认证的用户 ID（最稳定的标识）
        String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
        if (userId != null && !userId.isEmpty()) {
            int hash = Math.abs(userId.hashCode()) % 100;
            if (hash < grayRatioPercent) {
                if (log.isDebugEnabled()) {
                    log.debug("[GrayFilter] 比例灰度命中(用户) userId={} hash={} ratio={}%",
                            userId, hash, grayRatioPercent);
                }
                return GRAY_TAG_GRAY;
            }
            return GRAY_TAG_STABLE;
        }

        // 回退到 traceId（未认证请求的用户粘性）
        String traceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            int hash = Math.abs(traceId.hashCode()) % 100;
            if (hash < grayRatioPercent) {
                if (log.isDebugEnabled()) {
                    log.debug("[GrayFilter] 比例灰度命中(trace) traceId={} hash={} ratio={}%",
                            traceId, hash, grayRatioPercent);
                }
                return GRAY_TAG_GRAY;
            }
        }

        return null;
    }

    /**
     * 过滤器顺序:AuthGlobalFilter(+10)之后
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
