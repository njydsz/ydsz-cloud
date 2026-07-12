package com.njydsz.pmis.common.base.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.util.JsonUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 基础配置（Web/App 共享）
 *
 * <p>子类提供具体的 {@link BaseCorsProperties} 和 {@link BaseTraceProperties} 实现，
 * 以及注册自己的拦截器和过滤器 Bean。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseMvcConfiguration implements WebMvcConfigurer {

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
     * 注册 HTTP 消息转换器
     *
     * @param objectMapperProvider ObjectMapper 提供器
     * @return HttpMessageConverters 实例
     */
    @Bean
    public HttpMessageConverters httpMessageConverters(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(JsonUtils::getObjectMapper);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);
        return new HttpMessageConverters(converter);
    }

    /**
     * 注册 CORS 过滤器
     *
     * @return CORS 过滤器注册器，禁用时返回 null
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        if (!corsProperties.isEnabled()) {
            return null;
        }

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowCredentials(corsProperties.isAllowCredentials());
        corsProperties.getAllowedOriginPatterns().forEach(corsConfig::addAllowedOriginPattern);
        corsProperties.getAllowedHeaders().forEach(corsConfig::addAllowedHeader);
        corsProperties.getAllowedMethods().forEach(corsConfig::addAllowedMethod);
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
