package com.njydsz.common.core.featureflag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
/**
 * 功能开关自动配置。
 *
 * <p>注册功能开关（Feature Flag）相关 Bean：开关注册表、灰度评估器、A/B 分桶器。
 *
 * <p>支持基于 Nacos 配置中心的远程开关实时生效，无需重启服务。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.feature-flag", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagAutoConfiguration.class);

    /**
     * 默认特性开关服务（无 Nacos 客户端或 Nacos 未启用时使用）
     *
     * @param properties 特性开关配置属性
     * @param environment Spring Environment（用于 Nacos server-addr 回退解析）
     * @return 默认实现实例
     */
    @Bean
    @ConditionalOnMissingBean(FeatureFlagService.class)
    public DefaultFeatureFlagService defaultFeatureFlagService(FeatureFlagProperties properties,
                                                               Environment environment) {
        log.info("[FeatureFlag] 注册 DefaultFeatureFlagService（内存模式）");
        return new DefaultFeatureFlagService(properties, environment);
    }

    /**
     * Nacos 动态特性开关服务
     *
     * <p>激活条件：
     * <ul>
     *   <li>{@code ydsz.feature-flag.nacos.enabled=true}</li>
     *   <li>classpath 上存在 {@code com.alibaba.nacos.api.config.ConfigService}</li>
     *   <li>容器中尚无其它 {@link FeatureFlagService} Bean</li>
     * </ul>
     *
     * @param properties 特性开关配置属性
     * @param environment Spring Environment
     * @return Nacos 实现实例
     */
    @Bean
    @ConditionalOnClass(name = "com.alibaba.nacos.api.config.ConfigService")
    @ConditionalOnProperty(prefix = "ydsz.feature-flag.nacos", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(FeatureFlagService.class)
    public NacosFeatureFlagService nacosFeatureFlagService(FeatureFlagProperties properties,
                                                           Environment environment) {
        NacosFeatureFlagService service = new NacosFeatureFlagService(properties, environment);
        log.info("[FeatureFlag] 注册 NacosFeatureFlagService（Nacos 动态配置源）");
        return service;
    }

    /**
     * NacosFeatureFlagService 生命周期回调 — 初始化 Nacos 连接
     *
     * <p>通过单独的 Bean 定义而非 {@code @PostConstruct} 方式，确保仅在
     * {@link NacosFeatureFlagService} 实际注册时才触发初始化。
     */
    @Bean
    public NacosLifecycleHook nacosLifecycleHook() {
        return new NacosLifecycleHook();
    }

    /**
     * NacosFeatureFlagService 生命周期钩子
     *
     * <p>使用 {@link ConditionalOnBean} 守卫的内部类，仅在
     * {@link NacosFeatureFlagService} 注册时生效。
     */
    public static class NacosLifecycleHook {
        private static final Logger hookLog = LoggerFactory.getLogger(NacosLifecycleHook.class);

        @Autowired(required = false)
        private NacosFeatureFlagService nacosService;

        @PostConstruct
        public void init() {
            if (nacosService != null) {
                nacosService.init();
            }
        }

        @PreDestroy
        public void destroy() {
            if (nacosService != null) {
                nacosService.destroy();
            }
        }
    }
}
