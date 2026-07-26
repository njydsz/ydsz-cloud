package com.njydsz.common.jdbc.datasource;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.annotation.DS;
import com.njydsz.common.jdbc.config.TenantIsolationProperties;
import com.njydsz.common.jdbc.interceptor.TenantDataSourceRouter;

/**
 * 动态数据源自动配置
 *
 * <p>启用后，支持通过 {@link DS} 注解动态切换数据源。
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   jdbc:
 *     dynamic-datasource:
 *       enabled: true
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.jdbc.dynamic-datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
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

    /**
     * 注册租户数据源路由器
     *
     * <p>在 ISOLATE_DB 模式下，根据当前租户 ID 动态切换到对应的数据源。
     * 仅当 DynamicRoutingDataSource 和 TenantIsolationProperties 存在时才注册。
     *
     * @param routingDataSource 动态路由数据源
     * @param properties        租户隔离配置属性
     * @return TenantDataSourceRouter 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({DynamicRoutingDataSource.class, TenantIsolationProperties.class})
    public TenantDataSourceRouter tenantDataSourceRouter(DynamicRoutingDataSource routingDataSource,
                                                          TenantIsolationProperties properties) {
        return new TenantDataSourceRouter(routingDataSource, properties);
    }
}
