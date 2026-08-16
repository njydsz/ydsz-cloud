package com.njydsz.common.idempotent;

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
 * 幂等性自动配置。
 *
 * <p>当 classpath 中存在 Spring MVC 且配置了 {@code ydsz.idempotent.enabled=true} 时激活。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
@ConditionalOnProperty(prefix = "ydsz.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentAutoConfiguration {

    /**
     * 幂等性拦截器默认顺序。
     * <p>数值越小优先级越高，100 确保在限流拦截器之后执行。
     */
    private static final int DEFAULT_INTERCEPTOR_ORDER = 100;

    /**
     * 默认幂等键存储（本地内存实现）。
     *
     * <p>当 Redis 模块未引入时作为降级方案。
     *
     * @return IdempotentStore 实例
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore idempotentStore() {
        return new InMemoryIdempotentStore();
    }

    /**
     * 幂等性拦截器。
     *
     * @param idempotentStore 幂等键存储
     * @return IdempotentInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentInterceptor.class)
    public IdempotentInterceptor idempotentInterceptor(IdempotentStore idempotentStore) {
        return new IdempotentInterceptor(idempotentStore);
    }

    /**
     * 注册幂等性拦截器到 Spring MVC。
     *
     * @param interceptorProvider 拦截器提供者
     * @return WebMvcConfigurer 实例
     */
    @Bean
    public WebMvcConfigurer idempotentWebMvcConfigurer(ObjectProvider<IdempotentInterceptor> interceptorProvider) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                IdempotentInterceptor interceptor = interceptorProvider.getIfAvailable();
                if (interceptor != null) {
                    registry.addInterceptor(interceptor)
                            .addPathPatterns("/**")
                            .order(DEFAULT_INTERCEPTOR_ORDER);
                }
            }
        };
    }
}
