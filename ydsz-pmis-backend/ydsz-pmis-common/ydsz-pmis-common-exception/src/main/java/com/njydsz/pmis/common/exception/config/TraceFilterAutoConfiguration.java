package com.njydsz.pmis.common.exception.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.njydsz.pmis.common.exception.observability.TraceContextFilter;

/**
 * TraceId 过滤器自动配置
 *
 * <p>通过 {@code ydsz.exception.trace-enabled=true}（默认启用）控制是否注册。
 * 过滤器顺序设为最高优先级，确保 traceId 在所有业务过滤器之前注入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnProperty(prefix = "ydsz.exception", name = "trace-enabled", havingValue = "true", matchIfMissing = true)
public class TraceFilterAutoConfiguration {

    /**
     * 注册 TraceId 注入过滤器
     */
    @Bean
    public FilterRegistrationBean<TraceContextFilter> traceContextFilter() {
        FilterRegistrationBean<TraceContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceContextFilter());
        registration.addUrlPatterns("/*");
        registration.setName("traceContextFilter");
        // 最高优先级：在所有业务过滤器之前
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
