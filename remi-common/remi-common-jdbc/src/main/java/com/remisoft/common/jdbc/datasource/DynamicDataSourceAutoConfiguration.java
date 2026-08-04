package com.remisoft.common.jdbc.datasource;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.remisoft.common.jdbc.annotation.DS;

/**
 * 动态数据源自动配置
 *
 * <p>启用后，支持通过 {@link DS} 注解动态切换数据源。
 *
 * <p>配置示例：
 * <pre>
 * remi:
 *   jdbc:
 *     dynamic-datasource:
 *       enabled: true
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.jdbc.dynamic-datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicDataSourceAutoConfiguration {

    /**
     * 注册动态路由数据源
     *
     * @return DynamicRoutingDataSource 实例
     */
    @Bean
    @ConditionalOnMissingBean(DynamicRoutingDataSource.class)
    public DynamicRoutingDataSource dynamicRoutingDataSource() {
        return new DynamicRoutingDataSource();
    }

    /**
     * 注册 @DS 注解拦截器
     *
     * @return DsAnnotationInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(DsAnnotationInterceptor.class)
    public DsAnnotationInterceptor dsAnnotationInterceptor() {
        return new DsAnnotationInterceptor();
    }

    /**
     * 注册 @DS 注解切面
     *
     * @param interceptor 拦截器
     * @return Advisor 实例
     */
    @Bean
    @ConditionalOnMissingBean(name = "dsAnnotationAdvisor")
    public Advisor dsAnnotationAdvisor(DsAnnotationInterceptor interceptor) {
        AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(DS.class, DS.class, true);
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }

    // 注意：租户数据源路由（TenantDataSourceRouter）已迁移到 common-tenant 模块
    // 由 TenantAutoConfiguration 在 remi.tenant.mode=ISOLATE_DB 时自动注册
}
