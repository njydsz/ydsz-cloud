package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.aspect.IdempotentAspect;
import com.njydsz.pmis.common.aspect.OperationLogAspect;
import com.njydsz.pmis.common.aspect.PermissionAspect;
import com.njydsz.pmis.common.aspect.RateLimiterAspect;
import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 公共模块自动配置
 *
 * <p>供其他微服务通过 {@code @SpringBootApplication(scanBasePackages)} 引入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ComponentScan("com.njydsz.pmis.common")
@EnableAsync
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditFieldFiller auditFieldFiller() {
        return new AuditFieldFiller();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfig webMvcConfig(AuthInterceptor authInterceptor) {
        return new WebMvcConfig(authInterceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(org.springframework.context.ApplicationEventPublisher publisher) {
        return new OperationLogAspect(publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterAspect rateLimiterAspect(StringRedisTemplate redisTemplate) {
        return new RateLimiterAspect(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate) {
        return new IdempotentAspect(redisTemplate);
    }
}
