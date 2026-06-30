package com.njydsz.pmis.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由配置（按需使用，建议将路由配置放至 Nacos）
 *
 * <p>实际生产环境推荐在 Nacos 中维护路由规则，便于动态调整。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 认证服务
                .route("ydsz-pmis-auth", r -> r.path("/api/v1/auth/**")
                        .uri("lb://ydsz-pmis-auth"))
                // 用户服务
                .route("ydsz-pmis-user", r -> r.path("/api/v1/users/**", "/api/v1/departments/**", "/api/v1/employees/**", "/api/v1/menus/**")
                        .uri("lb://ydsz-pmis-user"))
                // 项目服务
                .route("ydsz-pmis-project", r -> r.path("/api/v1/projects/**", "/api/v1/opportunities/**", "/api/v1/contracts/**")
                        .uri("lb://ydsz-pmis-project"))
                // 财务服务
                .route("ydsz-pmis-finance", r -> r.path("/api/v1/finance/**", "/api/v1/invoices/**", "/api/v1/payments/**")
                        .uri("lb://ydsz-pmis-finance"))
                // 资源服务
                .route("ydsz-pmis-resource", r -> r.path("/api/v1/resources/**", "/api/v1/timesheets/**", "/api/v1/bench/**")
                        .uri("lb://ydsz-pmis-resource"))
                // 工作流
                .route("ydsz-pmis-workflow", r -> r.path("/api/v1/workflows/**")
                        .uri("lb://ydsz-pmis-workflow"))
                // 报表
                .route("ydsz-pmis-report", r -> r.path("/api/v1/reports/**", "/api/v1/dashboard/**")
                        .uri("lb://ydsz-pmis-report"))
                // AI
                .route("ydsz-pmis-agent", r -> r.path("/api/v1/ai/**")
                        .uri("lb://ydsz-pmis-agent"))
                // 通知
                .route("ydsz-pmis-notification", r -> r.path("/api/v1/notifications/**")
                        .uri("lb://ydsz-pmis-notification"))
                .build();
    }
}
