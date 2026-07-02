package com.njydsz.pmis.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由配置 (按需使用,建议将路由配置放至 Nacos)
 *
 * <p>所有路由已根据实际 Controller 路径修正,删除不存在的 finance/resource 服务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class RouteConfig {

    /**
     * 自定义路由定位器，配置各微服务的网关路由规则
     *
     * @param builder 路由定位器构建器
     * @return 路由定位器
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // ===== 基础服务 =====
                // 认证服务
                .route("ydsz-pmis-auth", r -> r.path("/api/v1/auth/**")
                        .uri("lb://ydsz-pmis-auth"))

                // 用户服务 (含 RBAC / 部门 / 人员 / 职级 / 字典 / 资源池 / Bench / 员工标签)
                .route("ydsz-pmis-user", r -> r.path(
                                "/api/v1/users/**",
                                "/api/v1/departments/**",
                                "/api/v1/employees/**",
                                "/api/v1/menus/**",
                                "/api/v1/permissions/**",
                                "/api/v1/roles/**",
                                "/api/v1/job-levels/**",
                                "/api/v1/dict/**",
                                "/api/v1/employee-tags/**",
                                "/api/v1/bench/**",
                                "/api/v1/resource-pools/**",
                                "/api/v1/resource-assignments/**",
                                "/api/v1/feign/auth/**")
                        .uri("lb://ydsz-pmis-user"))

                // ===== 业务服务 =====
                // 项目服务 (商机/立项/合同/变更/合同模板)
                .route("ydsz-pmis-project", r -> r.path("/api/v1/project/**")
                        .uri("lb://ydsz-pmis-project"))

                // 执行服务 (WBS/EVM/成本/收入/风险/工时/发票/付款/客户信用/资源/Dashboard/Report/费率/交付/收尾/利润)
                .route("ydsz-pmis-execution", r -> r.path(
                                "/api/v1/execution/**",
                                "/api/v1/invoices/**",
                                "/api/v1/payments/**",
                                "/api/v1/timesheets/**",
                                "/api/v1/resources/**",
                                "/api/v1/customers/**",
                                "/api/v1/reports/**",
                                "/api/v1/dashboard/**")
                        .uri("lb://ydsz-pmis-execution"))

                // 工作流
                .route("ydsz-pmis-workflow", r -> r.path("/api/v1/workflow/**")
                        .uri("lb://ydsz-pmis-workflow"))

                // 消息模板 / 发送
                .route("ydsz-pmis-message", r -> r.path("/api/v1/message/**")
                        .uri("lb://ydsz-pmis-message"))

                // 通知中心
                .route("ydsz-pmis-notification", r -> r.path("/api/v1/notifications/**")
                        .uri("lb://ydsz-pmis-notification"))

                // 通知中心 WebSocket（P0-2 实时推送，STOMP/SockJS 端点 /ws）
                .route("ydsz-pmis-notification-ws", r -> r.path("/ws/**")
                        .uri("lb://ydsz-pmis-notification"))

                // 配置中心
                .route("ydsz-pmis-config", r -> r.path("/api/v1/configs/**", "/api/v1/config/**")
                        .uri("lb://ydsz-pmis-config"))

                // 调度中心
                .route("ydsz-pmis-scheduler", r -> r.path("/api/v1/job/**", "/api/v1/scheduler/**")
                        .uri("lb://ydsz-pmis-scheduler"))

                // 文件服务
                .route("ydsz-pmis-file", r -> r.path("/api/v1/file/**")
                        .uri("lb://ydsz-pmis-file"))

                // 审计日志
                .route("ydsz-pmis-audit", r -> r.path("/api/v1/audit/**", "/api/v1/operation-logs/**")
                        .uri("lb://ydsz-pmis-audit"))

                // AI Agent
                .route("ydsz-pmis-agent", r -> r.path("/api/v1/ai/**", "/api/v1/agent/**")
                        .uri("lb://ydsz-pmis-agent"))

                .build();
    }
}
