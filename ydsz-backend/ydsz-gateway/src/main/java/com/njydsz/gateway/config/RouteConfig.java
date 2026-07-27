package com.njydsz.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由配置 (按需使用,建议将路由配置放至 Nacos)
 *
 * <p>P0-1: 全量标准化 — 所有 Controller 路径已统一为 {@code /api/v1/{module}/...}，
 * Gateway 直连转发，不再使用 StripPrefix 剥离前缀。
 *
 * <h3>路由策略</h3>
 * <ul>
 *   <li>所有业务模块 Controller 已包含 {@code /api/v1} 前缀</li>
 *   <li>Gateway 直接转发完整路径，后端 Controller 原样匹配</li>
 *   <li>前端统一在 baseURL 中添加 {@code /api/v1} 前缀</li>
 *   <li>后续新增 v2 接口时,在此追加 {@code /api/v2/**} 路由</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
public class RouteConfig {

    /**
     * 自定义路由定位器，配置各微服务的网关路由规则
     *
     * <p>所有路由路径与后端 Controller {@code @RequestMapping} 路径完全一致，
     * Gateway 不做路径重写，直接转发。
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
                                "/api/v1/auth/**",
                                "/api/v1/user/**",
                                "/api/v1/company/**",
                                "/api/v1/dept/**",
                                "/api/v1/menu/**",
                                "/api/v1/post/**",
                                "/api/v1/role/**",
                                "/api/v1/language/**",
                                "/api/v1/oauth2/**",
                                "/api/v1/userinfo/**",
                                "/api/internal/**",
                                "/feign/**")
                        .uri("lb://ydsz-userinfo"))

                // ===== 业务服务 =====
                // 项目业务服务
                .route("ydsz-project", r -> r.path(
                                "/api/v1/project/**",
                                "/api/v1/project/project/**")
                        .uri("lb://ydsz-project"))

                // 工作流服务
                .route("ydsz-workflow", r -> r.path(
                                "/api/v1/workflow/**")
                        .uri("lb://ydsz-workflow"))

                // 系统基础服务
                .route("ydsz-system", r -> r.path(
                                "/api/v1/config/**",
                                "/api/v1/dict/**",
                                "/api/v1/app/**",
                                "/api/v1/variable/**",
                                "/api/v1/system/**",
                                "/api/v1/search/**")
                        .uri("lb://ydsz-system"))

                // 消息中心服务
                .route("ydsz-message", r -> r.path(
                                "/api/v1/message/**")
                        .uri("lb://ydsz-message"))

                // 定时任务服务
                .route("ydsz-cronjob", r -> r.path(
                                "/api/v1/cronjob/**")
                        .uri("lb://ydsz-cronjob"))

                // 规则引擎服务
                .route("ydsz-literule", r -> r.path(
                                "/api/v1/literule/**")
                        .uri("lb://ydsz-literule"))

                // AI Agent 服务
                .route("ydsz-agent", r -> r.path(
                                "/api/v1/agent/**")
                        .uri("lb://ydsz-agent"))

                // 网盘知识库服务
                .route("ydsz-nextwiki", r -> r.path(
                                "/api/v1/nextwiki/**")
                        .uri("lb://ydsz-nextwiki"))

                .build();
    }
}
