package com.njydsz.common.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

import com.njydsz.common.config.health.ConfigEncryptHealthIndicator;
import com.njydsz.common.config.hotreload.ConfigChangeBridge;
import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 配置增强自动配置
 *
 * <p>作为 Jasypt 的增强层，提供以下能力：
 * <ul>
 *   <li><b>配置变更桥接</b>：监听 Spring Cloud 的 {@code RefreshEvent} /
 *       {@code EnvironmentChangeEvent}，自动 diff 属性变更并通知
 *       {@link ConfigChangeListener}（仅当 Spring Cloud Context 在 classpath 时激活）</li>
 *   <li><b>加密健康检查</b>：{@link ConfigEncryptHealthIndicator} 暴露 Jasypt
 *       加密器状态到 Actuator /health 端点</li>
 * </ul>
 *
 * <p><b>注意</b>：配置加解密本身由 {@code jasypt-spring-boot-starter} 全局处理，
 * 本模块不再自行实现加密逻辑。
 *
 * <h3>配置项</h3>
 * <pre>{@code
 * ydsz:
 *   config:
 *     change-monitor:
 *       enabled: true
 *       snapshot-old-values: true
 *     health:
 *       enabled: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ConfigProperties.class)
public class ConfigAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfigAutoConfiguration.class);

    /**
     * 配置变更桥接器
     *
     * <p>仅在 Spring Cloud Context 存在时激活。监听 RefreshEvent /
     * EnvironmentChangeEvent，自动 diff 属性变更并通知 {@link ConfigChangeListener}。
     *
     * @param environment         Spring 环境
     * @param publisher           事件发布器
     * @param changeMonitorProps  变更监控配置
     * @param listenersProvider   所有 ConfigChangeListener Bean
     * @return ConfigChangeBridge 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
    @ConditionalOnProperty(prefix = "ydsz.config.change-monitor", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ConfigChangeBridge configChangeBridge(
            ConfigurableEnvironment environment,
            ApplicationEventPublisher publisher,
            ConfigProperties configProperties,
            ObjectProvider<List<ConfigChangeListener>> listenersProvider) {

        List<ConfigChangeListener> listeners = listenersProvider.getIfAvailable(List::of);
        log.info("[Config] 配置变更桥接已启用，监听器数量: {}", listeners.size());

        return new ConfigChangeBridge(
                environment,
                publisher,
                configProperties.getChangeMonitor(),
                new CopyOnWriteArrayList<>(listeners)
        );
    }

    /**
     * 配置加密健康指标
     *
     * <p>检查 Jasypt 主密码是否配置、ENC() 属性是否存在，暴露到 Actuator /health 端点。
     *
     * @param environment Spring 环境
     * @return ConfigEncryptHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnProperty(prefix = "ydsz.config.health", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ConfigEncryptHealthIndicator configEncryptHealthIndicator(
            ConfigurableEnvironment environment) {
        log.info("[Config] 配置加密健康检查已启用");
        return new ConfigEncryptHealthIndicator(environment);
    }
}
