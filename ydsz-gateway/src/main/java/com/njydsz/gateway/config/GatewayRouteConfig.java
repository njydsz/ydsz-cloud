package com.njydsz.gateway.config;

import java.util.concurrent.Executor;

import com.alibaba.cloud.nacos.NacosConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 网关路由配置（Nacos 动态路由 + Java 兜底路由）。
 *
 * <p>聚合路由定义的两种来源：
 *
 * <ul>
 *   <li>{@link NacosRouteDefinitionRepository}：Nacos 动态路由（优先，支持运行时刷新）
 *   <li>{@link RouteLocator}：Java 兜底路由（Nacos 不可用时的降级方案）
 * </ul>
 *
 * <h3>路由优先级</h3>
 *
 * <p>Nacos 路由为 {@code @Primary}，覆盖 Spring Cloud Gateway 默认的属性路由加载。 Java 代码路由作为兜底：当 Nacos 中无路由配置时，自动回退到 Java 路由。
 *
 * <h3>配置项</h3>
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     dynamic-routes:
 *       enabled: true          # 是否启用 Nacos 动态路由
 *       data-id: gateway-routes.json  # Nacos 中路由配置的 DataId
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "ydsz.gateway.dynamic-routes",
    name = "enabled",
    havingValue = "true")
public class GatewayRouteConfig {

  /**
   * 注册 Nacos 动态路由仓库。
   *
   * <p>标记为 {@code @Primary}，覆盖 Spring Cloud Gateway 默认的 {@code
   * PropertiesRouteDefinitionRepository}。
   *
   * @param nacosConfigManager Nacos 配置管理器
   * @param dataId 路由配置 DataId
   * @param group Nacos 配置 Group（取当前环境 profile）
   * @param eventPublisher Spring 事件发布器（用于触发路由刷新）
   * @param applicationContext Spring 应用上下文（用于查找托管线程池）
   * @return Nacos 路由定义仓库
   */
  @Bean
  @Primary
  public RouteDefinitionRepository nacosRouteDefinitionRepository(
      NacosConfigManager nacosConfigManager,
      @Value("${ydsz.gateway.dynamic-routes.data-id:gateway-routes.json}") String dataId,
      @Value("${spring.profiles.active:dev}") String group,
      ApplicationEventPublisher eventPublisher,
      ApplicationContext applicationContext) {
    log.info("[GatewayRouteConfig] Nacos 动态路由已启用, dataId={}, group={}", dataId, group);
    // P1-1: 优先使用 ydsz-common-thread 托管线程池（ydsz.thread.pools.nacosRouteListener），
    //       未配置时传入 null，由 Nacos 客户端使用默认线程执行配置变更回调，避免自建线程池（规范 15.4.1）。
    Executor routeListenerExecutor = null;
    try {
      routeListenerExecutor =
          applicationContext.getBean("nacosRouteListenerExecutor", ThreadPoolTaskExecutor.class);
    } catch (NoSuchBeanDefinitionException e) {
      log.warn("[GatewayRouteConfig] 未配置托管线程池 nacosRouteListenerExecutor，"
          + " Nacos 配置变更回调将使用客户端默认线程");
    }
    return new NacosRouteDefinitionRepository(
        nacosConfigManager, dataId, group, true, eventPublisher, routeListenerExecutor);
  }

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
