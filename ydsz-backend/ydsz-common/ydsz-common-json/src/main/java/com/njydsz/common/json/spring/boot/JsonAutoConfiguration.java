package com.njydsz.common.json.spring.boot;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.autotype.AutoTypeWhitelistScanner;
import com.njydsz.common.json.config.JsonConfig;
import com.njydsz.common.json.health.JsonHealthIndicator;
import com.njydsz.common.json.metric.JsonCacheMetrics;
import com.njydsz.common.json.metric.JsonMetrics;
import com.njydsz.common.json.module.JsonModule;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.json.spring.JsonModuleRegistrar;
import com.njydsz.common.json.spring.JsonWarmupRunner;
import com.njydsz.common.json.spring.JsonProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Ydsz JSON 自动配置。
 *
 * <p>注册全局 {@code YdszJson} Bean（自研 JSON 引擎，非 Jackson 封装），支持 Long 转 String、日期格式化、
 *
 * <p>脱敏字段、未知字段忽略、BigDecimal 精度等统一序列化策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties(JsonProperties.class)
@ConditionalOnClass(JsonConfig.class)
@ConditionalOnProperty(prefix = "ydsz.json", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JsonAutoConfiguration {

    /**
     * YdszJson 核心配置（初始化全局配置 + 模块注册）。
     *
     * @param properties YdszJson 配置属性
     * @param springModules 所有实现 SpringFactory 接口的 JsonModule（可为空）
     * @return YdszJson 配置 Bean
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
     * @return YdszJson HTTP 消息转换器
     */
@Bean
@ConditionalOnMissingBean(JsonHttpMessageConverter.class)
@ConditionalOnClass(name = "org.springframework.http.converter.HttpMessageConverter")
public JsonHttpMessageConverter ydszJsonHttpMessageConverter(JsonProperties properties) {
JsonHttpMessageConverter converter = new JsonHttpMessageConverter();
converter.setStreamingEnabled(properties.isStreamingEnabled());
converter.setMaxRequestBodySize(properties.getMaxRequestBodySize());
return converter;
}

    /**
     * YdszJson 指标监控（Micrometer），并绑定到 YdszJson 引擎。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public JsonMetrics ydszJsonMetrics(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        JsonMetrics metrics = new JsonMetrics(meterRegistryProvider.getIfAvailable());
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
     * ASM 预热 Runner — 应用启动后异步预热高频序列化 Bean 的 ASM 字节码。
     *
     * @param properties YdszJson 配置属性
     * @return 预热 Runner
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonWarmupRunner ydszJsonWarmupRunner(JsonProperties properties) {
        return new JsonWarmupRunner(properties);
    }

    /**
     * YdszJson 配置 Bean。
     *
     * <p>使用 {@code @PostConstruct} 在依赖注入完成后初始化全局配置，
     * 确保所有 {@code JsonModule} Bean 已就绪后再注册到 {@link JsonModuleRegistrar}。</p>
     */
    public static class JsonConfigBean {

        private final JsonProperties properties;
        private final List<JsonModule> springModules;

        public JsonConfigBean(JsonProperties properties,
                                   List<JsonModule> springModules) {
            this.properties = properties;
            this.springModules = springModules;
        }

        /**
         * 在 Bean 依赖注入完成后初始化全局 YdszJson 配置。
         *
         * <p>使用 {@code @PostConstruct} 而非构造函数初始化的优势：
         * <ul>
         *   <li>确保 {@code springModules} 依赖注入完成后再注册模块</li>
         *   <li>避免构造函数中调用可被重写的方法（构造函数陷阱）</li>
         *   <li>更好的可测试性：可在测试中构造 Bean 而不触发初始化</li>
         * </ul>
         */
        @PostConstruct
        @SuppressWarnings("deprecation")
        public void init() {
            // 使用 Builder 模式构建配置（推荐方式，避免 setter 链式调用）
            // 兼容期仍使用 getInstance() 单例，后续版本将改为 setInstance(builder().build())
            JsonConfig.CircularReferenceStrategy strategy;
            try {
                strategy = JsonConfig.CircularReferenceStrategy.valueOf(
                        properties.getCircularReferenceStrategy());
            } catch (IllegalArgumentException e) {
                strategy = JsonConfig.CircularReferenceStrategy.REF;
            }
            JsonConfig config = JsonConfig.builder()
                    .dateFormat(properties.getDateFormat())
                    .namingStrategy(properties.getNamingStrategy())
                    .writeNulls(properties.isWriteNulls())
                    .prettyPrint(properties.isPrettyPrint())
                    .serializeEnumUsingOrdinal(properties.isSerializeEnumUsingOrdinal())
                    .circularReferenceStrategy(strategy)
                    .maxJsonSize(properties.getMaxJsonSize())
                    .maxDepth(properties.getMaxDepth())
                    .useBigDecimal(properties.isUseBigDecimal())
                    .build();
            config.apply();

            // 安全模式设置
            AutoTypeChecker.setSafeMode(properties.isSafeMode());

            // 启动时扫描 @YdszJsonClass 注解类，注册到 AutoTypeChecker 白名单
            // 替代原运行时反射加载方式，避免 Class.forName 的副作用
            if (properties.getWhitelistPackages() != null
                    && !properties.getWhitelistPackages().isEmpty()) {
                AutoTypeWhitelistScanner.scanAndRegister(
                        properties.getWhitelistPackages().toArray(new String[0]));
            }

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
