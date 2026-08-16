package com.njydsz.common.base.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 限流自动配置。
 *
 * <p>当 classpath 中存在 Spring MVC 且配置了 {@code ydsz.base.ratelimit.enabled=true} 时激活。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
@ConditionalOnProperty(prefix = "ydsz.base.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {

    /**
     * 限流拦截器默认顺序。
     * <p>数值越小优先级越高，50 确保在安全过滤器之后、业务拦截器之前执行。
     */
    private static final int DEFAULT_INTERCEPTOR_ORDER = 50;

    /**
     * 默认限流器（本地实现）。
     *
     * <p>当 Redis 模块未引入时作为降级方案。
     *
     * @return RateLimiter 实例
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter rateLimiter() {
        return new InMemoryRateLimiter();
    }

    /**
     * 限流拦截器。
     *
     * @param rateLimiter 限流器
     * @return RateLimitInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitInterceptor.class)
    public RateLimitInterceptor rateLimitInterceptor(RateLimiter rateLimiter) {
        return new RateLimitInterceptor(rateLimiter);
    }

    /**
     * 注册限流拦截器到 Spring MVC。
     *
     * @param interceptorProvider 拦截器提供者
     * @return WebMvcConfigurer 实例
     */
    @Bean
    public WebMvcConfigurer rateLimitWebMvcConfigurer(ObjectProvider<RateLimitInterceptor> interceptorProvider) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                RateLimitInterceptor interceptor = interceptorProvider.getIfAvailable();
                if (interceptor != null) {
                    registry.addInterceptor(interceptor)
                            .addPathPatterns("/**")
                            .order(DEFAULT_INTERCEPTOR_ORDER);
                }
            }
        };
    }
}
