package com.njydsz.pmis.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.alibaba.cloud.nacos.NacosConfigManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Nacos 动态路由配置（P1-6 + P2-12 增强）
 *
 * <p>当 {@code pmis.gateway.dynamic-routes.enabled=true} 时，
 * 注册 {@link NacosRouteDefinitionRepository} 为首选路由定义源，
 * 替代 Spring Cloud Gateway 默认的属性路由加载。
 *
 * <p>Java 代码路由（{@link RouteConfig}）作为兜底：
 * 当 Nacos 中无路由配置时，自动回退到 Java 路由。
 *
 * <h3>P2-12 增强项</h3>
 * <p>Nacos 配置变更自动触发路由刷新，无需重启网关。
 *
 * <h3>配置项</h3>
 * <pre>
 * pmis:
 *   gateway:
 *     dynamic-routes:
 *       enabled: true          # 是否启用 Nacos 动态路由
 *       data-id: gateway-routes.json  # Nacos 中路由配置的 DataId
 * </pre>
 *
 * @since 2.2.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "pmis.gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class NacosRouteConfig {

    /**
     * 注册 Nacos 动态路由仓库
     *
     * <p>标记为 {@code @Primary}，覆盖 Spring Cloud Gateway 默认的
     * {@code PropertiesRouteDefinitionRepository}。
     *
     * @param nacosConfigManager Nacos 配置管理器
     * @param dataId             路由配置 DataId
     * @param group              Nacos 配置 Group（取当前环境 profile）
     * @param eventPublisher     Spring 事件发布器（P2-12 用于触发路由刷新）
     * @return Nacos 路由定义仓库
     */
    @Bean
    @Primary
    public RouteDefinitionRepository nacosRouteDefinitionRepository(
            NacosConfigManager nacosConfigManager,
            @Value("${pmis.gateway.dynamic-routes.data-id:gateway-routes.json}") String dataId,
            @Value("${spring.profiles.active:dev}") String group,
            ApplicationEventPublisher eventPublisher) {
        log.info("[NacosRouteConfig] 动态路由已启用, dataId={}, group={}", dataId, group);
        return new NacosRouteDefinitionRepository(nacosConfigManager, dataId, group, true, eventPublisher);
    }
}
