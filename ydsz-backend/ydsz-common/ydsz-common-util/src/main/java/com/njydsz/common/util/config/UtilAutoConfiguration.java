package com.njydsz.common.util.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.util.http.OkHttpProperties;
import com.njydsz.common.util.http.OkHttpUtils;
import com.njydsz.common.util.id.SnowflakeProperties;
import com.njydsz.common.util.retry.RetrySupport;
import com.njydsz.common.util.spring.SpringContextHolder;

import okhttp3.ConnectionPool;
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
     * <p>SpringContextHolder 本身已标注 @Component，此处额外注册为 Bean
     * 以确保即使未被组件扫描覆盖也能正常工作。
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
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeout(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(
                        properties.getMaxIdleConnections(),
                        properties.getKeepAliveDuration(),
                        TimeUnit.MINUTES))
                .build();
        OkHttpUtils.setSpringManagedClient(okHttpClient);
        return okHttpClient;
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
