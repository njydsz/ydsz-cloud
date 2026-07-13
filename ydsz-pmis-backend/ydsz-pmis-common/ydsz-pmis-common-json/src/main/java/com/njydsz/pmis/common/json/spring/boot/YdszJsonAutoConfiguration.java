package com.njydsz.pmis.common.json.spring.boot;

import com.njydsz.pmis.common.json.config.YdszJsonConfig;
import com.njydsz.pmis.common.json.spring.YdszJsonHttpMessageConverter;
import com.njydsz.pmis.common.json.spring.YdszJsonModuleRegistrar;
import com.njydsz.pmis.common.json.spring.YdszJsonProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;

import java.util.List;

/**
 * YdszJson Spring Boot 自动配置入口。
 *
 * <p>当 classpath 存在 {@link YdszJsonConfig} 且 {@code pmis.json.enabled=true} 时自动生效。
 * 自动注册以下组件：
 * <ul>
 *   <li>{@link YdszJsonHttpMessageConverter} — HTTP 消息转换器</li>
 *   <li>{@link YdszJsonModuleRegistrar} — 模块注册器（自动发现 YdszJsonModule Bean）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@AutoConfiguration
@EnableConfigurationProperties(YdszJsonProperties.class)
@ConditionalOnClass(YdszJsonConfig.class)
@ConditionalOnProperty(prefix = "pmis.json", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YdszJsonAutoConfiguration {

    /**
     * YdszJson 核心配置（初始化全局配置 + 模块注册）。
     *
     * @param properties YdszJson 配置属性
     * @param springModules 所有实现 SpringFactory 接口的 YdszJsonModule（可为空）
     * @return YdszJson 配置 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public YdszJsonConfigBean ydszJsonConfigBean(YdszJsonProperties properties,
                                                  List<com.njydsz.pmis.common.json.module.YdszJsonModule> springModules) {
        return new YdszJsonConfigBean(properties, springModules);
    }

    /**
     * HTTP 消息转换器（注册到 Spring MVC）。
     *
     * @return YdszJson HTTP 消息转换器
     */
    @Bean
    @ConditionalOnMissingBean(YdszJsonHttpMessageConverter.class)
    @ConditionalOnClass(name = "org.springframework.http.converter.HttpMessageConverter")
    public YdszJsonHttpMessageConverter ydszJsonHttpMessageConverter() {
        return new YdszJsonHttpMessageConverter();
    }

    /**
     * YdszJson 配置 Bean（替代 @PostConstruct 初始化逻辑）。
     */
    public static class YdszJsonConfigBean {

        private final YdszJsonProperties properties;
        private final List<com.njydsz.pmis.common.json.module.YdszJsonModule> springModules;

        public YdszJsonConfigBean(YdszJsonProperties properties,
                                   List<com.njydsz.pmis.common.json.module.YdszJsonModule> springModules) {
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
            config.apply();

            // 注册 Spring Factory 模块
            YdszJsonModuleRegistrar registrar = new YdszJsonModuleRegistrar(springModules);
            registrar.register();
        }
    }
}
