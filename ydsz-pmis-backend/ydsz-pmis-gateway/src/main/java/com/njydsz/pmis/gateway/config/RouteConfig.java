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
                // 用户信息中心服务（合并 user + auth）：认证 / RBAC / 部门 / 人员 / 职级 / 字典 / 资源池 / Bench / 员工标签
                .route("ydsz-pmis-userinfo", r -> r.path(
                                "/auth/**",
                                "/users/**",
                                "/departments/**",
                                "/employees/**",
                                "/menus/**",
                                "/permissions/**",
                                "/roles/**",
                                "/job-levels/**",
                                "/dict/**",
                                "/employee-tags/**",
                                "/bench/**",
                                "/resource-pools/**",
                                "/resource-assignments/**",
                                "/feign/auth/**")
                        .uri("lb://ydsz-pmis-userinfo"))

                // ===== 业务服务 =====
                // 项目业务服务（合并 project + execution）：商机/立项/合同/变更/WBS/EVM/成本/收入/风险/工时/发票/付款/客户信用/资源/Dashboard/Report/费率/交付/收尾/利润
                .route("ydsz-pmis-project", r -> r.path(
                                "/project/**",
                                "/execution/**",
                                "/invoices/**",
                                "/payments/**",
                                "/timesheets/**",
                                "/resources/**",
                                "/customers/**",
                                "/reports/**",
                                "/dashboard/**")
                        .uri("lb://ydsz-pmis-project"))

                // 工作流
                .route("ydsz-pmis-workflow", r -> r.path("/workflow/**")
                        .uri("lb://ydsz-pmis-workflow"))

                // 系统基础服务（合并 file + config + audit + notification + message）
                .route("ydsz-pmis-system", r -> r.path(
                                "/message/**",
                                "/notifications/**",
                                "/ws/**",
                                "/configs/**",
                                "/config/**",
                                "/file/**",
                                "/audit/**",
                                "/operation-logs/**")
                        .uri("lb://ydsz-pmis-system"))

                // 定时任务
                .route("ydsz-pmis-cronjob", r -> r.path("/cronjob/**")
                        .uri("lb://ydsz-pmis-cronjob"))

                // AI Agent
                .route("ydsz-pmis-agent", r -> r.path("/ai/**", "/agent/**")
                        .uri("lb://ydsz-pmis-agent"))

                .build();
    }
}
