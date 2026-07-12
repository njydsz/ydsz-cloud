package com.njydsz.pmis.common.base.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.util.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * MVC 基础配置（Web/App 共享）
 *
 * <p>子类提供具体的 {@link BaseCorsProperties} 和 {@link BaseTraceProperties} 实现，
 * 以及注册自己的拦截器和过滤器 Bean。
 *
 * <p>JSON 序列化统一使用 Jackson（大厂标准）。ObjectMapper 优先使用 Spring 容器中注入的实例，
 * 若不存在则使用 JsonUtils 的全局实例。Spring Boot 自动配置会基于该 ObjectMapper 创建 JSON 消息转换器。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @since 3.5.0
 */
public abstract class BaseMvcConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BaseMvcConfiguration.class);

    /**
     * CORS 跨域配置属性
     */
    private final BaseCorsProperties corsProperties;

    /**
     * 构造 MVC 基础配置
     *
     * @param corsProperties CORS 配置属性
     */
    protected BaseMvcConfiguration(BaseCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 获取 CORS 配置属性
     *
     * @return CORS 配置属性实例
     */
    protected BaseCorsProperties getCorsProperties() {
        return corsProperties;
    }

    /**
     * 注册 ObjectMapper Bean
     *
     * <p>优先使用 Spring 容器中已有的 ObjectMapper，若不存在则使用 JsonUtils 的全局实例。
     * Spring Boot 自动配置会基于此 ObjectMapper 创建 JSON 消息转换器，无需手动注册 HttpMessageConverters。
     *
     * @return ObjectMapper 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return JsonUtils.getMapper();
    }

    /**
     * 注册 CORS 过滤器
     *
     * <p>通过 {@link BaseCorsProperties#isEnabled()} 控制是否生效，
     * 配置由子类通过 {@code @ConfigurationProperties} 绑定具体前缀。
     *
     * @return CORS 过滤器注册器，禁用时返回 null
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        // 通过子类绑定的配置属性（remi.web.cors / remi.app.cors）的 enabled 字段控制，
        // 不再使用 @ConditionalOnProperty(prefix = "remi.cors")，避免前缀与子类配置不匹配
        if (!corsProperties.isEnabled()) {
            return null;
        }

        // P1-6: CORS 安全加固 — 启动时校验配置安全性，输出警告日志
        List<String> securityWarnings = corsProperties.validateSecurity();
        for (String warning : securityWarnings) {
            log.warn("[CORS Security] {}", warning);
        }

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowCredentials(corsProperties.isAllowCredentials());
        corsProperties.getAllowedOriginPatterns().forEach(corsConfig::addAllowedOriginPattern);
        corsProperties.getAllowedHeaders().forEach(corsConfig::addAllowedHeader);
        corsProperties.getAllowedMethods().forEach(corsConfig::addAllowedMethod);
        // 暴露响应头配置（原代码遗漏了此项）
        corsProperties.getExposedHeaders().forEach(corsConfig::addExposedHeader);
        corsConfig.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
        String pathPattern = corsProperties.getPathPattern();
        configSource.registerCorsConfiguration(pathPattern != null ? pathPattern : "/**", corsConfig);

        FilterRegistrationBean<CorsFilter> corsBean = new FilterRegistrationBean<>(new CorsFilter(configSource));
        corsBean.setName("corsFilter");
        corsBean.setOrder(corsProperties.getOrder());
        return corsBean;
    }
}
