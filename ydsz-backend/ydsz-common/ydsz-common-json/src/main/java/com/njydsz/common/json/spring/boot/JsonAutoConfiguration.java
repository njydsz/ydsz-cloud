package com.njydsz.common.json.spring.boot;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.health.JsonHealthIndicator;
import com.njydsz.common.json.metric.JsonCacheMetrics;
import com.njydsz.common.json.metric.YdszJsonMetrics;
import com.njydsz.common.json.module.YdszJsonModule;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.json.spring.JsonModuleRegistrar;
import com.njydsz.common.json.spring.YdszJsonProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * YdszJson Spring Boot 自动配置入口。
 *
 * <p>当 classpath 存在 {@link YdszJsonConfig} 且 {@code ydsz.json.enabled=true} 时自动生效。
 * 自动注册以下组件：
 * <ul>
 *   <li>{@link JsonHttpMessageConverter} — HTTP 消息转换器</li>
 *   <li>{@link JsonModuleRegistrar} — 模块注册器（自动发现 YdszJsonModule Bean）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(YdszJsonProperties.class)
@ConditionalOnClass(YdszJsonConfig.class)
@ConditionalOnProperty(prefix = "ydsz.json", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JsonAutoConfiguration {

    /**
     * YdszJson 核心配置（初始化全局配置 + 模块注册）。
     *
     * @param properties YdszJson 配置属性
     * @param springModules 所有实现 SpringFactory 接口的 YdszJsonModule（可为空）
     * @return YdszJson 配置 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonConfigBean ydszJsonConfigBean(YdszJsonProperties properties,
                                                  List<YdszJsonModule> springModules) {
        return new JsonConfigBean(properties, springModules);
    }

    /**
     * HTTP 消息转换器（注册到 Spring MVC）。
     *
     * @return YdszJson HTTP 消息转换器
     */
@Bean
@ConditionalOnMissingBean(JsonHttpMessageConverter.class)
@ConditionalOnClass(name = "org.springframework.http.converter.HttpMessageConverter")
public JsonHttpMessageConverter ydszJsonHttpMessageConverter(YdszJsonProperties properties) {
JsonHttpMessageConverter converter = new JsonHttpMessageConverter();
converter.setMaxRequestBodySize(properties.getMaxJsonSize());
return converter;
}

    /**
     * YdszJson 指标监控（Micrometer），并绑定到 YdszJson 引擎。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public YdszJsonMetrics ydszJsonMetrics(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        YdszJsonMetrics metrics = new YdszJsonMetrics(meterRegistryProvider.getIfAvailable());
        YdszJson.setMetricsCallback(metrics);

        // 绑定缓存统计指标到 MeterRegistry
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            JsonCacheMetrics.bindTo(registry);
        }

        return metrics;
    }

    /**
     * YdszJson 健康检查指标。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public JsonHealthIndicator ydszJsonHealthIndicator() {
        return new JsonHealthIndicator();
    }

    /**
     * YdszJson 配置 Bean（替代 @PostConstruct 初始化逻辑）。
     */
    public static class JsonConfigBean {

        private final YdszJsonProperties properties;
        private final List<YdszJsonModule> springModules;

        public JsonConfigBean(YdszJsonProperties properties,
                                   List<YdszJsonModule> springModules) {
            this.properties = properties;
            this.springModules = springModules;
            init();
        }

        private void init() {
            YdszJsonConfig config = YdszJsonConfig.getInstance();
            config.setDateFormat(properties.getDateFormat());
            config.setNamingStrategy(properties.getNamingStrategy());
            config.setWriteNulls(properties.isWriteNulls());
            config.setPrettyPrint(properties.isPrettyPrint());
            config.setSerializeEnumUsingOrdinal(properties.isSerializeEnumUsingOrdinal());
            try {
                config.setCircularReferenceStrategy(
                        YdszJsonConfig.CircularReferenceStrategy.valueOf(
                                properties.getCircularReferenceStrategy()));
            } catch (IllegalArgumentException e) {
                config.setCircularReferenceStrategy(YdszJsonConfig.CircularReferenceStrategy.REF);
            }
            config.setMaxJsonSize(properties.getMaxJsonSize());
            config.setMaxDepth(properties.getMaxDepth());
            config.setUseBigDecimal(properties.isUseBigDecimal());
            config.apply();

            // 安全模式设置
            AutoTypeChecker.setSafeMode(properties.isSafeMode());

            // 监控设置
            if (properties.isMonitoringEnabled()) {
                System.setProperty("ydsz.json.monitoring", "true");
            }

            // 注册 Spring Factory 模块
            JsonModuleRegistrar registrar = new JsonModuleRegistrar(springModules);
            registrar.register();
        }
    }
}
