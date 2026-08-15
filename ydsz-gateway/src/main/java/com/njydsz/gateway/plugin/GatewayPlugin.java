package com.njydsz.gateway.plugin;

import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * P2-1: 网关插件接口
 *
 * <p>插件机制允许在不重启网关的情况下动态扩展请求处理逻辑。
 *
 * <h3>插件类型</h3>
 * <ul>
 *   <li>PRE_FILTER — 在鉴权前执行（如请求清洗、IP 伪装检测）</li>
 *   <li>POST_FILTER — 在路由后执行（如响应改写、数据脱敏）</li>
 *   <li>ERROR_HANDLER — 错误处理插件（如自定义降级逻辑）</li>
 * </ul>
 *
 * <h3>热加载</h3>
 * <p>实现类可以是 Groovy 脚本，通过 {@link PluginManager} 监控脚本文件变更并自动重载。
 *
 * <p>示例 Groovy 插件（plugins/GroovyHeaderPlugin.groovy）：
 * <pre>
 * import com.njydsz.gateway.plugin.GatewayPlugin
 * import org.springframework.web.server.ServerWebExchange
 * import reactor.core.publisher.Mono
 *
 * class GroovyHeaderPlugin implements GatewayPlugin {
 *     String getName() { "GroovyHeaderPlugin" }
 *     PluginType getType() { PluginType.PRE_FILTER }
 *     int getOrder() { 50 }
 *
 *     Mono&lt;Void&gt; execute(ServerWebExchange exchange, Void context) {
 *         exchange.getResponse().getHeaders().add("X-Groovy-Injected", "true")
 *         return Mono.empty()
 *     }
 * }
 * </pre>
 *
 * @since 1.0.0 (P2-1)
 * @author ydsz-team
 */
public interface GatewayPlugin {

    /**
     * 插件名称（唯一标识）
     *
     * @return 插件名称
     */
    String getName();

    /**
     * 插件类型
     *
     * @return 插件类型枚举
     */
    PluginType getType();

    /**
     * 执行顺序（Order 值，{@code HIGHEST_PRECEDENCE + N}）
     *
     * @return 顺序值
     */
    int getOrder();

    /**
     * 判断插件是否启用
     *
     * @return true 如果插件启用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 插件执行逻辑
     *
     * @param exchange 服务器 Web 交换上下文
     * @param context  执行上下文（可为 null）
     * @return 完成信号
     */
    Mono<Void> execute(ServerWebExchange exchange, Void context);

    /**
     * 插件类型枚举
     */
    enum PluginType {
        /** 前置处理（鉴权前） */
        PRE_FILTER,
        /** 后置处理（路由后） */
        POST_FILTER,
        /** 错误处理 */
        ERROR_HANDLER
    }
}
