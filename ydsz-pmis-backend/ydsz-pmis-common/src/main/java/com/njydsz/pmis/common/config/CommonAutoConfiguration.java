package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.chaos.ChaosAutoConfiguration;
import com.njydsz.pmis.common.featureflag.FeatureFlagAutoConfiguration;
import com.njydsz.pmis.common.interceptor.AuthInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 公共模块自动配置
 *
 * <p>供其他微服务通过 {@code @SpringBootApplication(scanBasePackages)} 引入。
 * Aspects/DataScopeAspect/IdempotentAspect/OperationLogAspect/RateLimiterAspect 等
 * 标注了 {@code @Aspect @Component} 的类，由 Spring 通过 {@link ComponentScan} 自动注入，
 * 不在此处再显式 @Bean 重复声明，以避免构造器签名冲突。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ComponentScan("com.njydsz.pmis.common")
@Import({FeatureFlagAutoConfiguration.class, ChaosAutoConfiguration.class, SentinelAutoConfiguration.class})
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
    public OpenApiConfig openApiConfig() {
        return new OpenApiConfig();
    }
}
