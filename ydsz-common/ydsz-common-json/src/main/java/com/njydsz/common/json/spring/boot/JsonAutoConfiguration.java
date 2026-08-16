package com.njydsz.common.json.spring.boot;

import java.util.List;
import java.util.Objects;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import com.njydsz.common.json.cache.BeanSerializerCache;
import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.module.JsonModule;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.json.spring.JsonModuleRegistrar;
import com.njydsz.common.json.spring.JsonProperties;

/**
 * Ydsz JSON 自动配置。
 *
 * <p>注册全局 {@code YdszJson} Bean（自研 JSON 引擎，非 Jackson 封装），支持 Long 转 String、日期格式化、
 * 脱敏字段、未知字段忽略、BigDecimal 精度等统一序列化策略。
 *
 * <p><b>与 Spring Boot Jackson 的关系（默认共存策略，A-3 修复）：</b>
 * 本配置通过 {@code @AutoConfigureBefore} 声明在 {@code JacksonAutoConfiguration} 之前加载，
 * 并通过 {@code @ConditionalOnMissingBean} 占位 HTTP 消息转换器，使业务 REST 接口走 YdszJson。
 * 默认<b>不</b>排除 {@code JacksonAutoConfiguration}——Spring 容器仍注册 {@code ObjectMapper}
 * Bean，供 Actuator / springdoc-openapi 等内部组件使用，构成"可控并存"的双引擎格局。
 *
 * <p>如需强隔离（全仓库唯一 JSON 底座，容器不注册 {@code ObjectMapper} Bean），
 * 可在配置文件中显式设置 {@code ydsz.json.disable-jackson-auto-configuration=true}，
 * 此时 {@link JacksonExclusionEnvironmentPostProcessor} 会将 {@code JacksonAutoConfiguration}
 * 加入 {@code spring.autoconfigure.exclude}。</p>
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
     * 自动预热 Runner（P1-E2）。
     *
     * <p>仅在 {@code ydsz.json.warmup-enabled=true} 时注册，应用启动阶段自动扫描
     * Spring 容器中的 Controller Bean，提取 {@code @RequestBody}/{@code @ResponseBody} 类型
     * 并调用 {@link com.njydsz.common.json.YdszJson#warmup(Class...)} 触发缓存构建。</p>
     *
     * @param applicationContext Spring 应用上下文
     * @return 预热 Runner
     * @since 1.2.1
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.json", name = "warmup-enabled", havingValue = "true")
    public JsonWarmupRunner jsonWarmupRunner(ApplicationContext applicationContext) {
        return new JsonWarmupRunner(applicationContext);
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

        /** JMX MBean（配置运维视图），在 @PostConstruct 阶段注册 */
        private JsonConfigViewer configViewer;

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
                .build();
            // 安装为全局不可变配置实例，后续修改必须走 install(newConfig)
            JsonConfig.install(newConfig);

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
                if (!Objects.equals(oldConfig.getDateFormat(), nextConfig.getDateFormat())) {
                    needClear = true;
                }
                if (oldConfig.isSerializeEnumUsingOrdinal() != nextConfig.isSerializeEnumUsingOrdinal()) {
                    needClear = true;
                }
                if (needClear) {
                    BeanSerializerCache.clear();
                }
            });

            // 注册 JMX MBean（配置运维视图），暴露配置版本号、缓存大小等指标
            configViewer = new JsonConfigViewer();
            configViewer.register();
        }

        /**
         * 容器关闭时清理全局静态缓存与当前线程的 ThreadLocal。
         *
         * <p>防止 Spring 容器重启（如热部署、测试多次启动）时，
         * 全局静态缓存中的旧元数据与 ThreadLocal 残留影响新容器实例。</p>
         *
         * @since 1.2.1
         */
        @PreDestroy
        public void destroy() {
            // 注销 JMX MBean
            if (configViewer != null) {
                configViewer.unregister();
            }
            BeanSerializerCache.clear();
            SerializerCache.clear();
            com.njydsz.common.json.reader.BeanReader.clearCache();
            com.njydsz.common.json.provider.PolymorphicTypeResolver.clearCache();
            com.njydsz.common.json.provider.SerializationProvider.clearThreadLocals();
        }
    }
}
