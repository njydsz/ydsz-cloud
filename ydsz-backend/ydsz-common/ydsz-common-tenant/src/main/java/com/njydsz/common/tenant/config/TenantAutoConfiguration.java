package com.njydsz.common.tenant.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.tenant.SystemTenantContextRunner;
import com.njydsz.common.tenant.async.TenantContextTaskDecorator;
import com.njydsz.common.tenant.datasource.TenantDataSourceFilter;
import com.njydsz.common.tenant.datasource.TenantDataSourceRouter;
import com.njydsz.common.tenant.feign.TenantContextFeignInterceptor;
import com.njydsz.common.tenant.interceptor.TenantInterceptorProvider;
import com.njydsz.common.tenant.web.TenantContextWebFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 多租户自动装配。
 *
 * <p>条件：{@code ydsz.tenant.enabled=true}（默认 false，不启用）。
 *
 * <p>装配内容：
 * <ul>
 *   <li>{@link TenantInterceptorProvider} — SPI 注册 SQL 拦截器到 MybatisPlusInterceptor 链</li>
 *   <li>{@link TenantContextWebFilter} — Web 入口上下文设置 + MDC 日志注入（FilterRegistrationBean order=HIGHEST+100）</li>
 *   <li>{@link TenantContextFeignInterceptor} — Feign 跨服务透传（common-feign 在 classpath 时）</li>
 *   <li>{@link TenantContextTaskDecorator} — 异步传播（common-thread 在 classpath 时）</li>
 *   <li>{@link TenantDataSourceRouter} — ISOLATE_DB 数据源路由（mode=ISOLATE_DB 时）</li>
 *   <li>{@link TenantDataSourceFilter} — ISOLATE_DB Web 过滤器（mode=ISOLATE_DB 时）</li>
 * </ul>
 *
 * <p>不引入 {@code common-tenant} 依赖或设为 false 时，
 * 无任何租户逻辑，{@code MpBaseEntity.tenantId} 字段被忽略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.tenant", name = "enabled", matchIfMissing = false)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

    /**
     * SPI 拦截器提供者：注册 TenantIsolationInterceptor 到 MybatisPlusInterceptor 链。
     *
     * @param properties 租户配置
     * @return 拦截器提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantInterceptorProvider tenantInterceptorProvider(TenantProperties properties) {
        log.info("多租户隔离已启用: mode={}, tenantColumn={}, superTenantId={}, systemTenantId={}",
                properties.getMode(),
                properties.getTenantColumn(),
                properties.getSuperTenantId(),
                properties.getSystemTenantId());
        SystemTenantContextRunner.init(properties.getSystemTenantId());
        return new TenantInterceptorProvider(properties);
    }

    /**
     * Web 入口过滤器：从 JWT 解析租户上下文 + MDC 日志注入。
     *
     * <p>使用 {@link FilterRegistrationBean} 包装，显式指定 order 为
     * {@code Ordered.HIGHEST_PRECEDENCE + 100}，确保在认证 Filter 之后、
     * 业务 Filter 之前执行。
     *
     * @param properties 租户配置
     * @return Filter 注册 Bean
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilterRegistration(
            TenantProperties properties) {
        FilterRegistrationBean<TenantContextWebFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantContextWebFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.addUrlPatterns("/*");
        registration.setName("tenantContextWebFilter");
        return registration;
    }

    /**
     * Feign 跨服务透传拦截器（可选，common-feign 在 classpath 时）。
     *
     * @return Feign 拦截器
     */
    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnMissingBean
    public TenantContextFeignInterceptor tenantContextFeignInterceptor() {
        log.info("多租户 Feign 跨服务透传已启用");
        return new TenantContextFeignInterceptor();
    }

    /**
     * 异步传播任务装饰器（可选，common-thread 在 classpath 时）。
     *
     * @param properties 租户配置
     * @return 任务装饰器
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.core.task.TaskDecorator")
    @ConditionalOnMissingBean
    public TenantContextTaskDecorator tenantContextTaskDecorator(TenantProperties properties) {
        log.info("多租户异步上下文传播已启用");
        return new TenantContextTaskDecorator(properties);
    }

    /**
     * ISOLATE_DB 模式数据源路由器（可选，mode=ISOLATE_DB 时）。
     *
     * @param routingDataSource 动态数据源
     * @param properties       租户配置
     * @return 数据源路由器
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.tenant", name = "mode",
                          havingValue = "ISOLATE_DB")
    @ConditionalOnClass(name = "com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource")
    @ConditionalOnMissingBean
    public TenantDataSourceRouter tenantDataSourceRouter(
            DynamicRoutingDataSource routingDataSource,
            TenantProperties properties) {
        log.info("多租户 ISOLATE_DB 模式已启用，数据源路由器已注册");
        return new TenantDataSourceRouter(routingDataSource, properties);
    }

    /**
     * ISOLATE_DB 模式 Web 过滤器（可选，mode=ISOLATE_DB + web 应用时）。
     *
     * @param router     数据源路由器
     * @param properties 租户配置
     * @return Filter 注册 Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.tenant", name = "mode",
                          havingValue = "ISOLATE_DB")
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TenantDataSourceFilter> tenantDataSourceFilterRegistration(
            TenantDataSourceRouter router,
            TenantProperties properties) {
        FilterRegistrationBean<TenantDataSourceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantDataSourceFilter(router, properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 90);
        registration.addUrlPatterns("/*");
        registration.setName("tenantDataSourceFilter");
        return registration;
    }
}
