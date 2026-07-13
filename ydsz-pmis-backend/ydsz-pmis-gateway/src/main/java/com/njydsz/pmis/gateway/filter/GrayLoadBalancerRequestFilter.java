package com.njydsz.pmis.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.pmis.gateway.loadbalancer.GrayLoadBalancer;

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
 * </ol>
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
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
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
