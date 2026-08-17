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
 * 网关路由配置（Nacos 动态路由为唯一入口 + Java 兜底路由）。
 *
 * <p>聚合路由定义的两种来源：
 *
 * <ul>
 *   <li>{@link NacosRouteDefinitionRepository}：Nacos 动态路由（<b>唯一配置入口</b>，默认启用，支持运行时刷新）
 *   <li>{@link RouteLocator}：Java 兜底路由（order=1000，仅在 Nacos 无路由配置时生效）
 * </ul>
 *
 * <h3>路由优先级</h3>
 *
 * <p>Nacos 路由为 {@code @Primary}，且 Java 兜底路由统一设置 order=1000（低于 Nacos 路由的默认 order=0），
 * 保证 Nacos 动态路由优先匹配；Nacos 中无路由配置（空或解析失败）时自动回退到 Java 兜底路由。
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
 *     "predicates": [ { "name": "Path", "args": { "pattern": "/api/v1/auth/**" } } ],
 *     "filters": [], "order": 0 }
 * ]
 * </pre>
 *
 * <p>详见模块内 {@code routes-nacos.yaml} 模板。禁止同时在 shared-configs 中引入
 * {@code ydsz-gateway-routes.yaml}，避免双路由源竞争。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "ydsz.gateway.dynamic-routes",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
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
   * <p>当 Nacos 配置中心无路由配置（空/解析失败）时提供基础路由能力。 统一设置 order=1000，
   * 确保 Nacos 动态路由（order=0）优先匹配，避免双路由源冲突。
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
        // ===== 基础服务（order=1000 兜底，Nacos 路由优先） =====
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
                    .customize(routeBuilder -> routeBuilder.order(1000))
                    .uri("lb://ydsz-userinfo"))
        // ===== 业务服务 =====
        .route(
            "ydsz-workflow",
            r -> r.path("/api/v1/workflow/**").customize(routeBuilder -> routeBuilder.order(1000)).uri("lb://ydsz-workflow"))
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
                    .customize(routeBuilder -> routeBuilder.order(1000))
                    .uri("lb://ydsz-system"))
        .route(
            "ydsz-message",
            r -> r.path("/api/v1/message/**").customize(routeBuilder -> routeBuilder.order(1000)).uri("lb://ydsz-message"))
        .route(
            "ydsz-cronjob",
            r -> r.path("/api/v1/cronjob/**").customize(routeBuilder -> routeBuilder.order(1000)).uri("lb://ydsz-cronjob"))
        .route(
            "ydsz-literule",
            r -> r.path("/api/v1/literule/**").customize(routeBuilder -> routeBuilder.order(1000)).uri("lb://ydsz-literule"))
        .route("ydsz-agent", r -> r.path("/api/v1/agent/**").customize(routeBuilder -> routeBuilder.order(1000)).uri("lb://ydsz-agent"))
        .route(
            "ydsz-nextwiki",
            r -> r.path("/api/v1/nextwiki/**").customize(routeBuilder -> routeBuilder.order(1000)).uri("lb://ydsz-nextwiki"))
        .build();
  }
}
