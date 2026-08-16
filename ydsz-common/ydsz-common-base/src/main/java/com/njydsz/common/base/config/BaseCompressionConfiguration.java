package com.njydsz.common.base.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.njydsz.common.base.filter.BaseCompressionFilter;

/**
 * HTTP 响应压缩配置（Web/App 共享）。
 *
 * <p>基于 GZIP 的响应压缩，减少网络传输量。
 * 通过 {@code ydsz.base.compression} 前缀配置。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   base:
 *     compression:
 *       enabled: true
 *       min-response-size: 2048
 *       mime-types:
 *         - application/json
 *         - application/xml
 *         - text/html
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ydsz.base.compression", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BaseCompressionProperties.class)
public class BaseCompressionConfiguration {

    /**
     * 注册响应压缩过滤器。
     *
     * @param properties 压缩配置属性
     * @return FilterRegistrationBean 实例
     */
    @Bean
    public FilterRegistrationBean<BaseCompressionFilter> baseCompressionFilter(
            BaseCompressionProperties properties) {
        FilterRegistrationBean<BaseCompressionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BaseCompressionFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setName("baseCompressionFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.setAsyncSupported(true);
        return registration;
    }
}
