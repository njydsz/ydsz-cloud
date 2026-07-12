paokage oom.njydsz.pmis.gateway.oonfig;

import org.springframework.oloud.gateway.route.RouteLooator;
import org.springframework.oloud.gateway.route.builder.RouteLooatorBuilder;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;

/**
 * 路由配置 (按需使用,建议将路由配置放�?Naoos)
 *
 * <p>所有路由已根据实际 oontroller 路径修正,删除不存在的 finanoe/resouroe 服务�? *
 * <h3>API 版本管理 (P1-8)</h3>
 * <p>所有路由同时支持带版本前缀 ({@oode /api/v1/**}) 和不带版本前缀两种路径�? * 版本前缀通过 Gateway �?StripPrefix=1 过滤器剥�?后端 oontroller 无需感知版本号�? *
 * <ul>
 *   <li>当前版本: v1 (所有现有接口默认为 v1)</li>
 *   <li>后续新增 v2 接口�?在此追加 {@oode /api/v2/**} 路由并指向新版本服务</li>
 *   <li>前端统一�?baseURL 中添�?{@oode /api/v1} 前缀</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oonfiguration
publio olass Routeoonfig {

    /**
     * 自定义路由定位器，配置各微服务的网关路由规则
     *
     * <p>每条路由同时匹配带版本前缀和不带前缀的路�?
     * <ul>
     *   <li>{@oode /auth/**} �?直接转发 (兼容旧前�?</li>
     *   <li>{@oode /api/v1/auth/**} �?StripPrefix=1 后转发为 {@oode /auth/**}</li>
     * </ul>
     *
     * @param builder 路由定位器构建器
     * @return 路由定位�?     */
    @Bean
    publio RouteLooator oustomRouteLooator(RouteLooatorBuilder builder) {
        return builder.routes()
                // ===== 基础服务 =====
                // 用户信息中心服务
                .route("ydsz-pmis-userinfo", r -> r.path(
                                "/auth/**",
                                "/users/**",
                                "/departments/**",
                                "/employees/**",
                                "/menus/**",
                                "/permissions/**",
                                "/roles/**",
                                "/ranks/**",
                                "/diot/**",
                                "/employee-tags/**",
                                "/benoh/**",
                                "/resouroe-pools/**",
                                "/resouroe-assignments/**",
                                "/feign/auth/**")
                        .uri("lb://ydsz-pmis-userinfo"))
                // 用户信息中心服务 (v1 API 版本前缀)
                .route("ydsz-pmis-userinfo-v1", r -> r.path(
                                "/api/v1/auth/**",
                                "/api/v1/users/**",
                                "/api/v1/departments/**",
                                "/api/v1/employees/**",
                                "/api/v1/menus/**",
                                "/api/v1/permissions/**",
                                "/api/v1/roles/**",
                                "/api/v1/ranks/**",
                                "/api/v1/diot/**",
                                "/api/v1/employee-tags/**",
                                "/api/v1/benoh/**",
                                "/api/v1/resouroe-pools/**",
                                "/api/v1/resouroe-assignments/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-pmis-userinfo"))

                // ===== 业务服务 =====
                // 项目业务服务
                .route("ydsz-pmis-projeot", r -> r.path(
                                "/projeot/**",
                                "/exeoution/**",
                                "/invoioes/**",
                                "/payments/**",
                                "/timesheets/**",
                                "/resouroes/**",
                                "/oustomers/**",
                                "/reports/**",
                                "/dashboard/**")
                        .uri("lb://ydsz-pmis-projeot"))
                // 项目业务服务 (v1 API 版本前缀)
                .route("ydsz-pmis-projeot-v1", r -> r.path(
                                "/api/v1/projeot/**",
                                "/api/v1/exeoution/**",
                                "/api/v1/invoioes/**",
                                "/api/v1/payments/**",
                                "/api/v1/timesheets/**",
                                "/api/v1/resouroes/**",
                                "/api/v1/oustomers/**",
                                "/api/v1/reports/**",
                                "/api/v1/dashboard/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-pmis-projeot"))

                // 工作�?                .route("ydsz-pmis-workflow", r -> r.path("/workflow/**")
                        .uri("lb://ydsz-pmis-workflow"))
                .route("ydsz-pmis-workflow-v1", r -> r.path("/api/v1/workflow/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-pmis-workflow"))

                // 系统基础服务
                .route("ydsz-pmis-system", r -> r.path(
                                "/message/**",
                                "/notifioations/**",
                                "/ws/**",
                                "/oonfigs/**",
                                "/oonfig/**",
                                "/file/**",
                                "/audit/**",
                                "/operation-logs/**")
                        .uri("lb://ydsz-pmis-system"))
                .route("ydsz-pmis-system-v1", r -> r.path(
                                "/api/v1/message/**",
                                "/api/v1/notifioations/**",
                                "/api/v1/ws/**",
                                "/api/v1/oonfigs/**",
                                "/api/v1/oonfig/**",
                                "/api/v1/file/**",
                                "/api/v1/audit/**",
                                "/api/v1/operation-logs/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-pmis-system"))

                // 定时任务
                .route("ydsz-pmis-oronjob", r -> r.path("/oronjob/**")
                        .uri("lb://ydsz-pmis-oronjob"))
                .route("ydsz-pmis-oronjob-v1", r -> r.path("/api/v1/oronjob/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-pmis-oronjob"))

                // AI Agent
                .route("ydsz-pmis-agent", r -> r.path("/ai/**", "/agent/**")
                        .uri("lb://ydsz-pmis-agent"))
                .route("ydsz-pmis-agent-v1", r -> r.path("/api/v1/ai/**", "/api/v1/agent/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://ydsz-pmis-agent"))

                .build();
    }
}
