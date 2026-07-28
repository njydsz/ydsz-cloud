package com.njydsz.common.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.njydsz.common.exception.observability.TraceContextFilter;

/**
 * Trace 过滤器配置。
 *
 * <p>注册 {@code TraceFilter}：从请求头读取或生成 TraceId / SpanId，写入 MDC 与响应头。
 *
 * <p>与 Logback 的 {@code %X{traceId}} 模式配合，实现全链路日志串联。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
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
