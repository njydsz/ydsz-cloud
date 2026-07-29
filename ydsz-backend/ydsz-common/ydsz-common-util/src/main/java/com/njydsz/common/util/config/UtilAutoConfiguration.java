package com.njydsz.common.util.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.util.health.UtilHealthIndicator;
import com.njydsz.common.util.http.HttpClientFactory;
import com.njydsz.common.util.http.OkHttpProperties;
import com.njydsz.common.util.http.OkHttpUtils;
import com.njydsz.common.util.id.SnowflakeHealthIndicator;
import com.njydsz.common.util.id.SnowflakeProperties;
import com.njydsz.common.util.retry.RetrySupport;
import com.njydsz.common.util.spring.SpringContextHolder;

import okhttp3.OkHttpClient;

/**
 * 通用工具类自动配置。
 *
 * <p>注册项目级工具 Bean：雪花 ID 生成器、Tracer、加密工具、Bean 拷贝器、断言工具。
 *
 * <p>所有工具 Bean 均为无状态、线程安全，可直接注入使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties({OkHttpProperties.class, SnowflakeProperties.class})
public class UtilAutoConfiguration {

    /**
     * 注册 SpringContextHolder Bean
     *
     * <p>SpringContextHolder 已移除 {@code @Component} 注解，统一在此处以 {@code @Bean} 注册，
     * 避免组件扫描与 AutoConfiguration 双重注册冲突。
     *
     * @return SpringContextHolder 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    /**
     * 注册 OkHttpClient Bean
     *
     * <p>基于 OkHttpProperties 配置创建 OkHttpClient 实例，
     * 支持连接池复用和超时配置。创建后自动注册到 OkHttpUtils 静态工具类。
     *
     * @param properties OkHttp 配置属性
     * @return OkHttpClient 实例
     */
    @Bean
    @ConditionalOnMissingBean(OkHttpClient.class)
    public OkHttpClient okHttpClient(OkHttpProperties properties) {
        OkHttpClient okHttpClient = HttpClientFactory.create(properties, null);
        OkHttpUtils.setSpringManagedClient(okHttpClient);
        return okHttpClient;
    }

    /**
     * 注册 SnowflakeHealthIndicator Bean
     *
     * <p>检查 Snowflake ID 生成器的健康状态（时钟回拨、workerId 有效性、ID 生成能力）。
     * 不使用 @Component 注解，统一在 AutoConfiguration 中注册。
     *
     * @return SnowflakeHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeHealthIndicator snowflakeHealthIndicator() {
        return new SnowflakeHealthIndicator();
    }

    /**
     * 注册 UtilHealthIndicator Bean
     *
     * <p>工具模块健康检查（SnowflakeUtils 状态、JVM 内存指标等），
     * 实现 Spring HealthIndicator 接口，通过 /actuator/health 端点暴露。
     *
     * @return UtilHealthIndicator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public UtilHealthIndicator utilHealthIndicator() {
        return new UtilHealthIndicator();
    }

    /**
     * 提供清理 Bean，用于应用关闭时清理 OkHttpUtils 资源
     */
    @Bean
    public OkHttpCleanupBean okHttpCleanupBean() {
        return new OkHttpCleanupBean();
    }

    /**
     * 提供清理 Bean，用于应用关闭时关闭 RetrySupport 异步线程池
     */
    @Bean
    public RetryCleanupBean retryCleanupBean() {
        return new RetryCleanupBean();
    }

    /**
     * OkHttp 资源清理 Bean
     */
    public static class OkHttpCleanupBean implements DisposableBean {
        @Override
        public void destroy() {
            OkHttpUtils.close();
        }
    }

    /**
     * RetrySupport 资源清理 Bean
     */
    public static class RetryCleanupBean implements DisposableBean {
        @Override
        public void destroy() {
            RetrySupport.shutdown();
        }
    }
}
