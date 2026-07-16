package com.njydsz.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由配置 (按需使用,建议将路由配置放至 Nacos)
 *
 * <p>所有路由已根据实际 Controller 路径修正,删除不存在的 finance/resource 服务。
 *
 * <h3>API 版本管理 (P1-8)</h3>
 * <p>所有路由同时支持带版本前缀 ({@code /api/v1/**}) 和不带版本前缀两种路径。
 * 版本前缀通过 Gateway 的 StripPrefix=1 过滤器剥离,后端 Controller 无需感知版本号。
 *
 * <ul>
 *   <li>当前版本: v1 (所有现有接口默认为 v1)</li>
 *   <li>后续新增 v2 接口时,在此追加 {@code /api/v2/**} 路由并指向新版本服务</li>
 *   <li>前端统一在 baseURL 中添加 {@code /api/v1} 前缀</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
public class RouteConfig {

    /**
     * 自定义路由定位器，配置各微服务的网关路由规则
     *
     * <p>每条路由同时匹配带版本前缀和不带前缀的路径:
     * <ul>
     *   <li>{@code /auth/**} → 直接转发 (兼容旧前端)</li>
     *   <li>{@code /api/v1/auth/**} → StripPrefix=1 后转发为 {@code /auth/**}</li>
     * </ul>
     *
     * @param builder 路由定位器构建器
     * @return 路由定位器
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // ===== 基础服务 =====
                // 用户信息中心服务
                .route("ydsz-userinfo", r -> r.path(
                                "/auth/**",
                                "/users/**",
                                "/departments/**",
                                "/employees/**",
                                "/menus/**",
                                "/permissions/**",
                                "/roles/**",
                                "/ranks/**",
                                "/dict/**",
                                "/employee-tags/**",
                                "/bench/**",
                                "/resource-pools/**",
                                "/resource-assignments/**",
                                "/feign/auth/**")
                        .uri("lb://ydsz-userinfo"))
                // 用户信息中心服务 (v1 API 版本前缀)
                .route("ydsz-userinfo-v1", r -> r.path(
                                "/api/v1/auth/**",
                                "/api/v1/users/**",
                                "/api/v1/departments/**",
                                "/api/v1/employees/**",
                                "/api/v1/menus/**",
                                "/api/v1/permissions/**",
                                "/api/v1/roles/**",
                                "/api/v1/ranks/**",
                                "/api/v1/dict/**",
                                "/api/v1/employee-tags/**",
                                "/api/v1/bench/**",
                                "/api/v1/resource-pools/**",
                                "/api/v1/resource-assignments/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-userinfo"))

                // ===== 业务服务 =====
                // 项目业务服务(2026-07-16 合并 sales/finance 后,所有 Controller 路径统一为 /api/project/**)
                // 兼容旧路径:直接转发到 ydsz-project,Controller 已统一前缀
                .route("ydsz-project", r -> r.path("/api/project/**")
                        .uri("lb://ydsz-project"))
                // 项目业务服务 (v1 API 版本前缀)
                // /api/v1/api/project/** -> stripPrefix(2) -> /api/project/**
                .route("ydsz-project-v1", r -> r.path("/api/v1/api/project/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-project"))

                // 工作流
                .route("ydsz-workflow", r -> r.path("/workflow/**")
                        .uri("lb://ydsz-workflow"))
                .route("ydsz-workflow-v1", r -> r.path("/api/v1/workflow/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-workflow"))

                // 系统基础服务
                .route("ydsz-system", r -> r.path(
                                "/message/**",
                                "/notifications/**",
                                "/ws/**",
                                "/configs/**",
                                "/config/**",
                                "/file/**",
                                "/audit/**",
                                "/operation-logs/**")
                        .uri("lb://ydsz-system"))
                .route("ydsz-system-v1", r -> r.path(
                                "/api/v1/message/**",
                                "/api/v1/notifications/**",
                                "/api/v1/ws/**",
                                "/api/v1/configs/**",
                                "/api/v1/config/**",
                                "/api/v1/file/**",
                                "/api/v1/audit/**",
                                "/api/v1/operation-logs/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-system"))

                // 定时任务
                .route("ydsz-cronjob", r -> r.path("/cronjob/**")
                        .uri("lb://ydsz-cronjob"))
                .route("ydsz-cronjob-v1", r -> r.path("/api/v1/cronjob/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-cronjob"))

                // AI Agent
                .route("ydsz-agent", r -> r.path("/ai/**", "/agent/**")
                        .uri("lb://ydsz-agent"))
                .route("ydsz-agent-v1", r -> r.path("/api/v1/ai/**", "/api/v1/agent/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-agent"))

                .build();
    }
}
