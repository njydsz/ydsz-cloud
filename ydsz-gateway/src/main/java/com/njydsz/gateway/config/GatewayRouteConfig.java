package com.njydsz.gateway.config;

import java.util.concurrent.Executor;

import com.alibaba.cloud.nacos.NacosConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 网关路由配置（Nacos 动态路由为唯一入口）。
 *
 * <p>Nacos 动态路由为唯一配置入口，默认启用，支持运行时刷新。
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
 * <p>详见模块内 {@code routes-nacos.yaml} 模板。
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
}
