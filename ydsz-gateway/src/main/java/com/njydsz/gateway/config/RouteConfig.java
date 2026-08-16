package com.njydsz.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 路由配置 — 兜底静态路由（Nacos 不可用时的降级方案）。
 *
 * <p>P0-3: 路由配置已迁移至 Nacos 动态配置（ydsz-gateway-routes.yaml）， 通过 {@code spring.cloud.gateway.routes}
 * 属性自动装配，支持动态刷新。
 *
 * <p>本类仅在 {@code noroutes} Profile 未激活时作为兜底方案， 当 Nacos 配置中心正常加载路由配置后，本 Bean 自动让步 （Spring Cloud
 * Gateway 优先使用属性路由，{@code RouteLocator} Bean 作为补充）。
 *
 * <h3>路由策略</h3>
 *
 * <ul>
 *   <li>所有业务模块 Controller 已包含 {@code /api/v1} 前缀
 *   <li>Gateway 直接转发完整路径，后端 Controller 原样匹配
 *   <li>前端统一在 baseURL 中添加 {@code /api/v1} 前缀
 *   <li>新增/修改路由时编辑 Nacos 配置文件 ydsz-gateway-routes.yaml
 *   <li><b>P2-5:</b> 路由 ID 和 URI 中的服务名须与 {@code FeignClientConstants}（ydsz-common-feign 模块）保持一致。
 *       Gateway 为 WebFlux 响应式栈，不依赖 common-feign 模块， 因此无法直接引用常量，修改时需手动同步。
 * </ul>
 *
 * <h3>Nacos 配置方式</h3>
 *
 * <pre>
 * # Nacos Data ID: ydsz-gateway-routes.yaml
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: ydsz-userinfo
 *           uri: lb://ydsz-userinfo
 *           predicates:
 *             - Path=/api/v1/auth/**,/api/v1/user/**,...
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Configuration
public class RouteConfig {

  /**
   * 兜底静态路由定位器。
   *
   * <p>当 Nacos 配置中心不可用时，提供基础路由能力。 Nacos 正常加载后，属性路由与本 Bean 共存（属性路由优先匹配）。
   *
   * <p>如需完全禁用静态路由（仅使用 Nacos 动态路由）， 启动时添加 JVM 参数 {@code -Dspring.profiles.active=noroutes}。
   *
   * @param builder 路由定位器构建器
   * @return 兜底路由定位器
   */
  @Bean
  @Profile("!noroutes")
  public RouteLocator fallbackRouteLocator(RouteLocatorBuilder builder) {
    return builder
        .routes()
        // ===== 基础服务 =====
        .route(
            "ydsz-userinfo",
            r ->
                r.path(
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
        .route("ydsz-workflow", r -> r.path("/api/v1/workflow/**").uri("lb://ydsz-workflow"))
        .route(
            "ydsz-system",
            r ->
                r.path(
                        "/api/v1/config/**",
                        "/api/v1/dict/**",
                        "/api/v1/app/**",
                        "/api/v1/variable/**",
                        "/api/v1/system/**",
                        "/api/v1/search/**")
                    .uri("lb://ydsz-system"))
        .route("ydsz-message", r -> r.path("/api/v1/message/**").uri("lb://ydsz-message"))
        .route("ydsz-cronjob", r -> r.path("/api/v1/cronjob/**").uri("lb://ydsz-cronjob"))
        .route("ydsz-literule", r -> r.path("/api/v1/literule/**").uri("lb://ydsz-literule"))
        .route("ydsz-agent", r -> r.path("/api/v1/agent/**").uri("lb://ydsz-agent"))
        .route("ydsz-nextwiki", r -> r.path("/api/v1/nextwiki/**").uri("lb://ydsz-nextwiki"))
        .build();
  }
}
