package com.njydsz.pmis.common.json.spring.boot;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.json.autotype.AutoTypeChecker;
import com.njydsz.pmis.common.json.config.JsonConfig;
import com.njydsz.pmis.common.json.health.JsonHealthIndicator;
import com.njydsz.pmis.common.json.metric.JsonMetrics;
import com.njydsz.pmis.common.json.module.JsonModule;
import com.njydsz.pmis.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.pmis.common.json.spring.JsonModuleRegistrar;
import com.njydsz.pmis.common.json.spring.JsonProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Json Spring Boot 自动配置入口。
 *
 * <p>当 classpath 存在 {@link JsonConfig} 且 {@code pmis.json.enabled=true} 时自动生效。
 * 自动注册以下组件：
 * <ul>
 *   <li>{@link JsonHttpMessageConverter} — HTTP 消息转换器</li>
 *   <li>{@link JsonModuleRegistrar} — 模块注册器（自动发现 JsonModule Bean）</li>
 * </ul>
 *
 * @since 1.3.0
 */
@AutoConfiguration
@EnableConfigurationProperties(JsonProperties.class)
@ConditionalOnClass(JsonConfig.class)
@ConditionalOnProperty(prefix = "pmis.json", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JsonAutoConfiguration {

    /**
     * Json 核心配置（初始化全局配置 + 模块注册）。
     *
     * @param properties Json 配置属性
     * @param springModules 所有实现 SpringFactory 接口的 JsonModule（可为空）
     * @return Json 配置 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonConfigBean ydszJsonConfigBean(JsonProperties properties,
                                                  List<JsonModule> springModules) {
        return new JsonConfigBean(properties, springModules);
    }

    /**
     * HTTP 消息转换器（注册到 Spring MVC）。
     *
     * @return Json HTTP 消息转换器
     */
    @Bean
    @ConditionalOnMissingBean(JsonHttpMessageConverter.class)
    @ConditionalOnClass(name = "org.springframework.http.converter.HttpMessageConverter")
    public JsonHttpMessageConverter ydszJsonHttpMessageConverter() {
        return new JsonHttpMessageConverter();
    }

    /**
     * Json 指标监控（Micrometer），并绑定到 Json 引擎。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public JsonMetrics ydszJsonMetrics(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        JsonMetrics metrics = new JsonMetrics(meterRegistryProvider.getIfAvailable());
        Json.setMetricsCallback(metrics);
        return metrics;
    }

    /**
     * Json 健康检查指标。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public JsonHealthIndicator ydszJsonHealthIndicator() {
        return new JsonHealthIndicator();
    }

    /**
     * Json 配置 Bean（替代 @PostConstruct 初始化逻辑）。
     */
    public static class JsonConfigBean {

        private final JsonProperties properties;
        private final List<JsonModule> springModules;

        public JsonConfigBean(JsonProperties properties,
                                   List<JsonModule> springModules) {
            this.properties = properties;
            this.springModules = springModules;
            init();
        }

        private void init() {
            JsonConfig config = JsonConfig.getInstance();
            config.setDateFormat(properties.getDateFormat());
            config.setNamingStrategy(properties.getNamingStrategy());
            config.setWriteNulls(properties.isWriteNulls());
            config.setPrettyPrint(properties.isPrettyPrint());
            config.setSerializeEnumUsingOrdinal(properties.isSerializeEnumUsingOrdinal());
            try {
                config.setCircularReferenceStrategy(
                        JsonConfig.CircularReferenceStrategy.valueOf(
                                properties.getCircularReferenceStrategy()));
            } catch (IllegalArgumentException e) {
                config.setCircularReferenceStrategy(JsonConfig.CircularReferenceStrategy.REF);
            }
            config.setMaxJsonSize(properties.getMaxJsonSize());
            config.setMaxDepth(properties.getMaxDepth());
            config.apply();

            // 安全模式设置
            AutoTypeChecker.setSafeMode(properties.isSafeMode());

            // 注册 Spring Factory 模块
            JsonModuleRegistrar registrar = new JsonModuleRegistrar(springModules);
            registrar.register();
        }
    }
}
