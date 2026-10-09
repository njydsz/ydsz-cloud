package com.njydsz.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 网关路由配置（Nacos 动态路由为唯一入口）。
 *
 * <p>Nacos 动态路由为唯一配置入口，默认启用，支持运行时刷新。
 *
 * <h3>配置项</h3>
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     dynamic-routes:
 *       enabled: true               # Nacos 动态路由总开关（默认 true）
 *       data-id: gateway-routes.json # Nacos 中路由配置的 DataId（JSON 数组格式）
 * </pre>
 *
 * <p><b>路由配置格式（DataId: gateway-routes.json，Group: 当前 profile）：</b>
 *
 * <pre>
 * [
 *   { "id": "ydsz-userinfo", "uri": "lb://ydsz-userinfo",
 *     "predicates": [ { "name": "Path", "args": { "pattern": "/api/auth/**" } } ],
 *     "filters": [], "order": 0 }
 * ]
 * </pre>
 *
 * <h3>StripPrefix 过滤器</h3>
 *
 * <p>按 YDIZ-API-001 规范，{@code /api} 前缀为网关层统一路由命名空间，业务层 Controller 的
 * {@code @RequestMapping} 禁止包含 {@code /api} 段。所有路由均需配置
 * {@code StripPrefix=1} 过滤器将 {@code /api} 剥离后再转发至后端服务。</p>
 *
 * <pre>
 * {
 *   "id": "ydsz-agent",
 *   "uri": "lb://ydsz-agent",
 *   "predicates": [{ "name": "Path", "args": { "pattern": "/api/agent/**" } }],
 *   "filters": [{ "name": "StripPrefix", "args": { "parts": "1" } }],
 *   "order": 0
 * }
 * </pre>
 *
 * <p>详见模块内 {@code routes-nacos.yaml} 模板。
 *
 * @author ydsz
 * @since 26.09.24
 */
// @Configuration — 本地开发环境无 Nacos，路由改为在 application.yml 中以 YAML 静态定义
// @ConditionalOnProperty 注解同步禁用
@Slf4j
public class GatewayRouteConfig {

  // 本地开发环境不使用 Nacos 动态路由，路由定义见 application.yml 中的 spring.cloud.gateway.routes
}
