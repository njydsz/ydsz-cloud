package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.njydsz.common.web.filter.ResponseCompressionFilter;

/**
 * HTTP 响应压缩配置
 *
 * <p>基于 Spring Boot 内置的响应压缩功能，提供更合理的默认值和统一配置入口。
 * 支持 GZIP 压缩，减少网络传输量，提升性能。
 *
 * <p><b>配置示例：</b>
 * <pre>
 * ydsz:
 *   web:
 *     compression:
 *       enabled: true
 *       min-response-size: 2048
 *       mime-types:
 *         - application/json
 *         - application/xml
 *         - text/html
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ydsz.web.compression", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ResponseCompressionProperties.class)
public class ResponseCompressionConfiguration {

    private final ResponseCompressionProperties properties;

    public ResponseCompressionConfiguration(ResponseCompressionProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册响应压缩过滤器
     *
     * <p>过滤器顺序设置为 HIGHEST_PRECEDENCE + 100，确保在大多数过滤器之后执行，
     * 这样可以压缩经过其他过滤器处理后的最终响应内容。
     *
     * @return FilterRegistrationBean 实例
     */
    @Bean
    public FilterRegistrationBean<ResponseCompressionFilter> responseCompressionFilter() {
        FilterRegistrationBean<ResponseCompressionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ResponseCompressionFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setName("responseCompressionFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.setAsyncSupported(true);
        return registration;
    }
}
