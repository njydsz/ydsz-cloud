package com.remisoft.common.json.spring.boot;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.autotype.AutoTypeWhitelistScanner;
import com.remisoft.common.json.internal.JsonConfig;
import com.remisoft.common.json.health.JsonHealthIndicator;
import com.remisoft.common.json.metric.JsonCacheMetrics;
import com.remisoft.common.json.metric.JsonMetrics;
import com.remisoft.common.json.module.JsonModule;
import com.remisoft.common.json.spring.JsonHttpMessageConverter;
import com.remisoft.common.json.spring.JsonModuleRegistrar;
import com.remisoft.common.json.spring.JsonWarmupRunner;
import com.remisoft.common.json.spring.JsonProperties;
import com.remisoft.common.json.naming.PropertyNamingStrategy;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Remi JSON 自动配置。
 *
 * <p>注册全局 {@code RemiJson} Bean（自研 JSON 引擎，非 Jackson 封装），支持 Long 转 String、日期格式化、
 *
 * <p>脱敏字段、未知字段忽略、BigDecimal 精度等统一序列化策略。
 *
 * <p><b>与 Spring Boot Jackson 的关系（双引擎格局）：</b>
 * 本配置通过 {@code @AutoConfigureBefore} 声明在 {@code JacksonAutoConfiguration} 之前加载，
 * 并通过 {@code @ConditionalOnMissingBean} 占位 HTTP 消息转换器，使业务 REST 接口走 RemiJson。
 * 但 Spring Boot 默认仍会注册 {@code ObjectMapper} Bean，供 Actuator 部分端点、
 * Spring Data Redis 默认序列化器等 Spring 内部组件使用，构成"可控并存"的双引擎格局。
 *
 * <p>如需彻底统一为单引擎，可设置 {@code remi.json.disable-jackson-auto-configuration=true}，
 * 由 {@link JacksonExclusionEnvironmentPostProcessor} 在启动早期将
 * {@code JacksonAutoConfiguration} 加入 {@code spring.autoconfigure.exclude}。
 * <b>注意：</b>禁用后需评估 Spring 生态内部依赖 {@code ObjectMapper} 的能力是否受影响。
 *
 * @author remi-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties(JsonProperties.class)
@ConditionalOnClass(JsonConfig.class)
@AutoConfigureBefore(name = {
    "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
})
@ConditionalOnProperty(prefix = "remi.json", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JsonAutoConfiguration {

    /**
     * RemiJson 核心配置（初始化全局配置 + 模块注册）。
     *
     * @param properties RemiJson 配置属性
     * @param springModules 所有实现 SpringFactory 接口的 JsonModule（可为空）
     * @return RemiJson 配置 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonConfigBean remiJsonConfigBean(JsonProperties properties,
                                                  List<JsonModule> springModules) {
        return new JsonConfigBean(properties, springModules);
    }

    /**
     * HTTP 消息转换器（注册到 Spring MVC）。
     *
     * @return RemiJson HTTP 消息转换器
     */
    @Bean
    @ConditionalOnMissingBean(JsonHttpMessageConverter.class)
    @ConditionalOnClass(name = "org.springframework.http.converter.HttpMessageConverter")
    public JsonHttpMessageConverter remiJsonHttpMessageConverter(JsonProperties properties) {
        JsonHttpMessageConverter converter = new JsonHttpMessageConverter();
        converter.setStreamingEnabled(properties.isStreamingEnabled());
        converter.setMaxRequestBodySize(properties.getMaxRequestBodySize());
        return converter;
    }

    /**
     * RemiJson 指标监控（Micrometer），并绑定到 RemiJson 引擎。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnProperty(prefix = "remi.json", name = "monitoring-enabled", havingValue = "true", matchIfMissing = true)
    public JsonMetrics remiJsonMetrics(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        JsonMetrics metrics = new JsonMetrics(meterRegistryProvider.getIfAvailable());
        RemiJson.setMetricsCallback(metrics);

        // 绑定缓存统计指标到 MeterRegistry
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            JsonCacheMetrics.bindTo(registry);
        }

        return metrics;
    }

    /**
     * RemiJson 健康检查指标。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public JsonHealthIndicator remiJsonHealthIndicator() {
        return new JsonHealthIndicator();
    }

    /**
     * ASM 预热 Runner — 应用启动后异步预热高频序列化 Bean 的 ASM 字节码。
     *
     * @param properties RemiJson 配置属性
     * @return 预热 Runner
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonWarmupRunner remiJsonWarmupRunner(JsonProperties properties) {
        return new JsonWarmupRunner(properties);
    }

    /**
     * 命名策略转换器，使 {@code remi.json.naming-strategy=SNAKE_CASE} 等 YAML 配置
     * 能绑定到 {@link PropertyNamingStrategy} 接口常量。
     *
     * @return Converter 实例
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean(name = "namingStrategyConverter")
    public Converter<String, PropertyNamingStrategy> namingStrategyConverter() {
        return source -> {
            if (source == null || source.isBlank()) {
                return PropertyNamingStrategy.LOWER_CAMEL_CASE;
            }
            return switch (source.trim().toUpperCase()) {
                case "SNAKE_CASE", "LOWER_UNDERSCORE" -> PropertyNamingStrategy.SNAKE_CASE;
                case "KEBAB_CASE", "LOWER_HYPHEN" -> PropertyNamingStrategy.KEBAB_CASE;
                case "LOWER_CAMEL_CASE" -> PropertyNamingStrategy.LOWER_CAMEL_CASE;
                case "UPPER_CAMEL_CASE" -> PropertyNamingStrategy.UPPER_CAMEL_CASE;
                case "LOWER_CASE" -> PropertyNamingStrategy.LOWER_CASE;
                default -> {
                    // 兼容旧文档中已删除的命名策略常量
                    if ("LOWER_UNDERSCORE".equals(source.trim().toUpperCase())) {
                        yield PropertyNamingStrategy.SNAKE_CASE;
                    }
                    yield PropertyNamingStrategy.LOWER_CAMEL_CASE;
                }
            };
        };
    }

    /**
     * RemiJson 配置 Bean。
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
         * 在 Bean 依赖注入完成后初始化全局 RemiJson 配置。
         *
         * <p>使用 {@code @PostConstruct} 而非构造函数初始化的优势：
         * <ul>
         *   <li>确保 {@code springModules} 依赖注入完成后再注册模块</li>
         *   <li>避免构造函数中调用可被重写的方法（构造函数陷阱）</li>
         *   <li>更好的可测试性：可在测试中构造 Bean 而不触发初始化</li>
         * </ul>
         */
        @PostConstruct
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
                    .maxGenericDepth(properties.getMaxGenericDepth())
                    .useBigDecimal(properties.isUseBigDecimal())
                    .wrapRootValue(properties.isWrapRootValue())
                    .failOnError(properties.isFailOnError())
                    .build();
            config.apply();

            // 安全模式设置
            AutoTypeChecker.setSafeMode(properties.isSafeMode());

            // 启动时扫描 @JsonClass 注解类，注册到 AutoTypeChecker 白名单
            // 替代原运行时反射加载方式，避免 Class.forName 的副作用
            if (properties.getWhitelistPackages() != null
                    && !properties.getWhitelistPackages().isEmpty()) {
                AutoTypeWhitelistScanner.scanAndRegister(
                        properties.getWhitelistPackages().toArray(new String[0]));
            }

            // 监控开关由 remiJsonMetrics Bean 的 @ConditionalOnProperty(monitoring-enabled) 控制，
            // 不再设置无人读取的 remi.json.monitoring system property。

            // 注册 Spring Factory 模块
            JsonModuleRegistrar registrar = new JsonModuleRegistrar(springModules);
            registrar.register();
        }
    }
}
