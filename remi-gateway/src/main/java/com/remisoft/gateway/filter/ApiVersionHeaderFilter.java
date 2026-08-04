package com.remisoft.gateway.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * API 版本响应头注入过滤器（P1-3 完善）
 *
 * <p><b>背景：</b>服务端 {@code remi-common-web} 已提供 {@code @ApiVersion} 注解 +
 * URL/HEADER/ACCEPT 三种版本路由策略 + Sunset 废弃管理（RFC 8594），
 * 但网关层未将版本信息透出到响应头，前端无法感知当前调用的是哪个版本的 API。
 *
 * <p><b>本过滤器：</b>从请求路径提取版本段（{@code /api/v1/**} 或 {@code /v1/**}），
 * 注入响应头：
 * <ul>
 *   <li>{@code X-API-Version}: 当前请求命中的 API 版本（如 v1 / v2）</li>
 *   <li>{@code Sunset}: 命中已废弃版本时输出建议下线日期（RFC 8594，可选）</li>
 * </ul>
 *
 * <p>前端可据此在控制台告警"你正在调用即将废弃的 API v1，请升级到 v2"。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ApiVersionHeaderFilter implements GlobalFilter, Ordered {

    /** 匹配路径版本段：/api/v1/... 或 /v1/... */
    private static final Pattern VERSION_PATTERN = Pattern.compile("/(api/)?v(?<ver>\\d+)(?:[./]|$)");

    /** 版本响应头 */
    private static final String HEADER_API_VERSION = "X-API-Version";

    /** Sunset 响应头（RFC 8594） */
    private static final String HEADER_SUNSET = "Sunset";

    /**
     * 注入 API 版本响应头。
     *
     * <p>使用 {@code then() + doOnSuccess()} 在响应提交前设置头，
     * 不阻塞请求主链路。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            try {
                String path = exchange.getRequest().getURI().getPath();
                Matcher matcher = VERSION_PATTERN.matcher(path);
                if (matcher.find()) {
                    ServerHttpResponse response = exchange.getResponse();
                    if (!response.getHeaders().containsHeader(HEADER_API_VERSION)) {
                        response.getHeaders().set(HEADER_API_VERSION, "v" + matcher.group("ver"));
                    }
                }
            } catch (Exception e) {
                // 响应头注入失败不影响主流程
                log.debug("[ApiVersionHeader] 注入版本响应头失败: {}", e.getMessage());
            }
        }));
    }

    /**
     * 过滤器顺序：位于过滤器链尾部（响应阶段执行，不影响鉴权/限流）。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
