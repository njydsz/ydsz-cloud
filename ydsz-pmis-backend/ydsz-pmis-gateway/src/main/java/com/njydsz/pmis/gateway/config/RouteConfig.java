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
                // 身份认证管理服务（合并 user + auth）：认证 / RBAC / 部门 / 人员 / 职级 / 字典 / 资源池 / Bench / 员工标签
                .route("ydsz-pmis-iam", r -> r.path(
                                "/api/v1/auth/**",
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
                        .uri("lb://ydsz-pmis-iam"))

                // ===== 业务服务 =====
                // 项目业务服务（合并 project + execution）：商机/立项/合同/变更/WBS/EVM/成本/收入/风险/工时/发票/付款/客户信用/资源/Dashboard/Report/费率/交付/收尾/利润
                .route("ydsz-pmis-project", r -> r.path(
                                "/api/v1/project/**",
                                "/api/v1/execution/**",
                                "/api/v1/invoices/**",
                                "/api/v1/payments/**",
                                "/api/v1/timesheets/**",
                                "/api/v1/resources/**",
                                "/api/v1/customers/**",
                                "/api/v1/reports/**",
                                "/api/v1/dashboard/**")
                        .uri("lb://ydsz-pmis-project"))

                // 工作流
                .route("ydsz-pmis-workflow", r -> r.path("/api/v1/workflow/**")
                        .uri("lb://ydsz-pmis-workflow"))

                // 系统基础服务（合并 file + config + audit + notification + message）
                .route("ydsz-pmis-system", r -> r.path(
                                "/api/v1/message/**",
                                "/api/v1/notifications/**",
                                "/ws/**",
                                "/api/v1/configs/**",
                                "/api/v1/config/**",
                                "/api/v1/file/**",
                                "/api/v1/audit/**",
                                "/api/v1/operation-logs/**")
                        .uri("lb://ydsz-pmis-system"))

                // 调度中心
                .route("ydsz-pmis-scheduler", r -> r.path("/api/v1/job/**", "/api/v1/scheduler/**")
                        .uri("lb://ydsz-pmis-scheduler"))

                // AI Agent
                .route("ydsz-pmis-agent", r -> r.path("/api/v1/ai/**", "/api/v1/agent/**")
                        .uri("lb://ydsz-pmis-agent"))

                .build();
    }
}
