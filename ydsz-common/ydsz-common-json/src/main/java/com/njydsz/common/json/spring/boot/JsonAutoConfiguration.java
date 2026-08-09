package com.njydsz.common.json.spring.boot;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.autotype.AutoTypeWhitelistScanner;
import com.njydsz.common.json.cache.BeanSerializerCache;
import com.njydsz.common.json.health.JsonHealthIndicator;
import com.njydsz.common.json.internal.DualJsonDetector;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.module.JsonModule;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.json.spring.JsonModuleRegistrar;
import com.njydsz.common.json.spring.JsonProperties;
import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * Ydsz JSON 自动配置。
 *
 * <p>注册全局 {@code YdszJson} Bean（自研 JSON 引擎，非 Jackson 封装），支持 Long 转 String、日期格式化、
 * 脱敏字段、未知字段忽略、BigDecimal 精度等统一序列化策略。
 *
 * <p><b>与 Spring Boot Jackson 的关系（默认单引擎策略）：</b>
 * 本配置通过 {@code @AutoConfigureBefore} 声明在 {@code JacksonAutoConfiguration} 之前加载，
 * 并通过 {@code @ConditionalOnMissingBean} 占位 HTTP 消息转换器，使业务 REST 接口走 YdszJson。
 * 同时，{@link JacksonExclusionEnvironmentPostProcessor} 默认将
 * {@code JacksonAutoConfiguration} 加入 {@code spring.autoconfigure.exclude}，
 * 使 Spring 容器不再注册 {@code ObjectMapper} Bean，实现全仓库统一使用 YdszJson。
 *
 * <p>如需恢复 Spring Boot 默认的 Jackson 共存行为，可在配置文件中设置
 * {@code ydsz.json.disable-jackson-auto-configuration=false}，此时 Spring Boot
 * 仍会注册 {@code ObjectMapper} Bean 供 Actuator 部分端点等 Spring 内部组件使用，
 * 构成"可控并存"的双引擎格局。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties(JsonProperties.class)
@ConditionalOnClass(JsonConfig.class)
@AutoConfigureBefore(name = {
    "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
})
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
     * 命名策略转换器，使 {@code ydsz.json.naming-strategy=SNAKE_CASE} 等 YAML 配置
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
     * JSON 模块健康检查指示器。
     *
     * <p>当 Spring Boot Actuator Health 在类路径时注册，暴露 {@code /actuator/health/json} 端点，
     * 报告 Ydsz JSON 引擎的配置状态（命名策略、安全模式、严格模式、Jackson 排除状态等）。
     *
     * @param properties JSON 配置属性
     * @return JSON 健康检查指示器
     * @since 1.2.0
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean(JsonHealthIndicator.class)
    public JsonHealthIndicator jsonHealthIndicator(JsonProperties properties) {
        return new JsonHealthIndicator(properties, JsonConfig.getInstance());
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
        public void init() {
            // 使用 Builder 模式构建配置（推荐方式，构建后不可变，通过 install() 安装到全局单例）
            JsonConfig.CircularReferenceStrategy strategy;
            try {
                strategy = JsonConfig.CircularReferenceStrategy.valueOf(
                        properties.getCircularReferenceStrategy());
            } catch (IllegalArgumentException e) {
                strategy = JsonConfig.CircularReferenceStrategy.REF;
            }
            JsonConfig newConfig = JsonConfig.builder()
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
                    .strictMode(properties.isStrictMode())
                    .build();
            // 安装为全局不可变配置实例，后续修改必须走 install(newConfig)
            JsonConfig.install(newConfig);

            // 安全模式设置
            AutoTypeChecker.setSafeMode(properties.isSafeMode());

            // 启动时扫描 @JsonClass 注解类，注册到 AutoTypeChecker 白名单
            // 替代原运行时反射加载方式，避免 Class.forName 的副作用
            if (properties.getWhitelistPackages() != null
                    && !properties.getWhitelistPackages().isEmpty()) {
                AutoTypeWhitelistScanner.scanAndRegister(
                        properties.getWhitelistPackages().toArray(new String[0]));
            }

            // 注册 Spring Factory 模块
            JsonModuleRegistrar registrar = new JsonModuleRegistrar(springModules);
            registrar.register();

            // P2-FIX: 注册配置变更监听器，当命名策略/日期格式/枚举序列化方式等影响字段输出的配置变更时，
            // 自动清空 BeanSerializerCache 中已烘焙的字段名缓存，使配置热更新真正生效。
            // 背景：README 注意事项第 7 点明确指出"命名策略在字段元数据加载时缓存，后续切换对已缓存类无效"，
            // 本修复通过 ConfigChangeListener 机制消除该隐性陷阱。
            JsonConfig.addChangeListener((oldConfig, nextConfig, newVersion) -> {
                if (oldConfig == null) {
                    return;
                }
                boolean needClear = false;
                if (oldConfig.getNamingStrategy() != nextConfig.getNamingStrategy()) {
                    needClear = true;
                }
                if (!java.util.Objects.equals(oldConfig.getDateFormat(), nextConfig.getDateFormat())) {
                    needClear = true;
                }
                if (oldConfig.isSerializeEnumUsingOrdinal() != nextConfig.isSerializeEnumUsingOrdinal()) {
                    needClear = true;
                }
                if (needClear) {
                    BeanSerializerCache.clear();
                }
            });
            // 严格模式检测（JSON 双体系一致性校验）
            // 在 disableJacksonAutoConfiguration=true 时，若 strict-mode 启用，检测是否存在
            // Jackson 注解混用，发现冲突则抛出 DualJsonConflictException 阻止启动
            if (properties.isDisableJacksonAutoConfiguration() && properties.isStrictMode()) {
                DualJsonDetector.scanAndReport(properties.getWhitelistPackages(), true);
            } else if (properties.isStrictMode()) {
                // Jackson 自动配置未禁用 + strict-mode 启用 = 松弛模式（仅输出告警）
                DualJsonDetector.scanAndReport(properties.getWhitelistPackages(), false);
            }
        }
    }
}
