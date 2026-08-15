package com.njydsz.gateway.plugin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * P2-1: 插件执行网关过滤器
 *
 * <p>在过滤器链中注入插件执行点，调用 {@link PluginManager} 执行已注册的插件。
 *
 * <h3>执行时机</h3>
 * <p>PRE_FILTER 类型插件在鉴权前执行，POST_FILTER 类型插件在路由后执行。
 *
 * <h3>顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 40}，在限流(+30)之后、审计日志(+35)之后。
 *
 * @since 1.0.0 (P2-1)
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PluginManager.class)
public class PluginGlobalFilter implements GlobalFilter, Ordered {

    private final PluginManager pluginManager;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (pluginManager.getPluginCount() == 0) {
            return chain.filter(exchange);
        }

        // 执行前置插件
        return pluginManager.executePlugins(GatewayPlugin.PluginType.PRE_FILTER, exchange)
                .then(chain.filter(exchange))
                .then(Mono.fromRunnable(() ->
                        // 执行后置插件（fire-and-forget）
                        pluginManager.executePlugins(GatewayPlugin.PluginType.POST_FILTER, exchange)
                                .onErrorResume(e -> {
                                    log.debug("[PluginFilter] 后置插件执行异常: {}", e.getMessage());
                                    return Mono.empty();
                                })
                                .subscribe()
                ));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 40;
    }
}
