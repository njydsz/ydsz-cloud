package com.njydsz.gateway.filter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.njydsz.common.json.YdszJson;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.IpWhitelistProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * IP 白名单全局过滤器（P2-8 安全加固）
 *
 * <p>核心职责:
 * <ol>
 *   <li>从 Nacos 配置动态加载 IP 白名单（支持 CIDR）</li>
 *   <li>校验客户端真实 IP（优先从 X-Forwarded-For / X-Real-IP 获取）</li>
 *   <li>白名单为空时默认放行（不启用白名单功能）</li>
 *   <li>支持 CIDR 表示法（如 192.168.1.0/24）</li>
 *   <li>支持单个 IP 精确匹配</li>
 * </ol>
 *
 * <p>执行顺序先于 {@link AuthGlobalFilter}，在认证前即拒绝非法 IP，
 * 避免无效请求消耗 JWT 解析与 Redis 查询资源。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "ip-whitelist", havingValue = "true", matchIfMissing = true)
public class IpWhitelistFilter implements GlobalFilter, Ordered {

    /** 配置项分隔符：白名单字符串中多个条目以逗号或换行分隔 */
    private static final String WHITELIST_SEPARATOR = "[,\\n]";

    private final IpWhitelistProperties properties;

    /**
     * P2-4: 缓存解析后的白名单集合（避免每次请求都 split + stream + collect）
     * <p>使用 AtomicReference 保证线程安全的缓存切换。
     * 当 @RefreshScope 刷新 properties 时，下次请求会重新解析。
     */
    private final AtomicReference<Set<String>> cachedWhitelist = new AtomicReference<>(Set.of());

    /**
     * P2-4: 上一次解析的白名单原始字符串（用于检测配置是否变更）
     */
    private volatile String lastRawWhitelist = null;

    /**
     * 核心过滤逻辑：开关校验 → 白名单解析 → 跳过路径 → IP 校验 → 拒绝/放行
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1) 开关关闭：直接放行
        if (!properties.isIpWhitelistEnabled()) {
            return chain.filter(exchange);
        }

        // 2) P2-4: 获取缓存的白名单集合（仅在配置变更时重新解析）
        Set<String> whitelist = getOrParseWhitelist(properties.getIpWhitelist());
        // 白名单为空：视为未配置，放行所有（不启用白名单功能）
        if (whitelist.isEmpty()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 3) 跳过路径前缀匹配（健康检查、登录等公开端点不校验 IP）
        if (isSkipPath(path)) {
            return chain.filter(exchange);
        }

        // 4) 解析客户端真实 IP
        String clientIp = GatewayIpUtils.getClientIp(request);
        if (clientIp.isEmpty()) {
            // 无法获取客户端 IP（如 UNIX domain socket），保守放行交由后续过滤器处理
            log.warn("[IpWhitelist] 无法解析客户端 IP，路径={}, 放行交由后续过滤器", path);
            return chain.filter(exchange);
        }

        // 5) 命中白名单则放行
        if (GatewayIpUtils.isAllowed(clientIp, whitelist)) {
            return chain.filter(exchange);
        }

        // 6) 非白名单 IP：返回 403
        log.warn("[IpWhitelist] 拒绝非白名单 IP 访问 ip={}, path={}", clientIp, path);
        return forbidden(exchange);
    }

    /**
     * 过滤器执行顺序：在 {@link AuthGlobalFilter} 之前执行（更小的 order 值）
     *
     * <p>AuthGlobalFilter 的 order 为 {@code GatewayFilterOrder.AUTH}，
     * 本过滤器设为 {@code GatewayFilterOrder.IP_WHITELIST}，确保认证前完成 IP 拦截。
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return GatewayFilterOrder.IP_WHITELIST.getOrder();
    }

    /**
     * P2-4: 获取缓存的白名单集合（仅在配置变更时重新解析）
     *
     * <p>当配置字符串与上次相同时直接返回缓存，避免每次请求都执行 split + stream + collect。
     * 当 @RefreshScope 刷新 properties 后，配置字符串会变化，触发重新解析。
     *
     * @param raw 原始配置字符串
     * @return 白名单条目集合
     */
    private Set<String> getOrParseWhitelist(String raw) {
        // 配置未变更，直接返回缓存
        if (raw != null ? raw.equals(lastRawWhitelist) : lastRawWhitelist == null) {
            return cachedWhitelist.get();
        }
        // 配置变更，重新解析
        Set<String> parsed = parseWhitelist(raw);
        cachedWhitelist.set(parsed);
        lastRawWhitelist = raw;
        return parsed;
    }

    /**
     * 解析白名单配置字符串为集合
     *
     * <p>支持逗号与换行分隔，自动去除空白条目与前后空格。
     *
     * @param raw 原始配置字符串
     * @return 白名单条目集合（LinkedHashSet 保序，便于调试）
     */
    private Set<String> parseWhitelist(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(WHITELIST_SEPARATOR))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 判断请求路径是否命中跳过路径前缀
     *
     * @param path 请求路径
     * @return true 表示该路径不校验 IP
     */
    private boolean isSkipPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String skip : properties.getIpWhitelistSkipPaths()) {
            if (skip != null && !skip.isBlank() && path.startsWith(skip.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回 403 禁止访问响应
     *
     * <p>响应体格式:
     * <pre>
     * {"code":403,"message":"error.IP_FORBIDDEN","traceId":"xxx","timestamp":...}
     * </pre>
     *
     * @param exchange 服务器 Web 交换上下文
     * @return 完成信号 Mono
     */
    private Mono<Void> forbidden(ServerWebExchange exchange) {
        // 复用 TracerUtils 生成链路追踪 ID，便于日志关联
        String traceId = TraceIdGenerator.generateSortableTraceId();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.FORBIDDEN, "error.IP_FORBIDDEN");
        body.assignTraceId(traceId);
        byte[] bytes = YdszJson.toJsonBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
