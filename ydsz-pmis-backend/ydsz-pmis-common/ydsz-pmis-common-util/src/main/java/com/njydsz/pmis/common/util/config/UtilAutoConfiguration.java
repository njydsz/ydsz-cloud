package com.njydsz.pmis.common.util.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.util.http.OkHttpProperties;
import com.njydsz.pmis.common.util.http.OkHttpUtils;
import com.njydsz.pmis.common.util.id.SnowflakeProperties;
import com.njydsz.pmis.common.util.spring.SpringContextHolder;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

/**
 * Util 模块自动配置类
 *
 * <p>通过 Spring Boot AutoConfiguration 机制，自动装配工具类所需的 Bean，
 * 包括 SpringContextHolder、OkHttpClient 等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
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
     * OkHttp 资源清理 Bean
     */
    public static class OkHttpCleanupBean implements DisposableBean {
        @Override
        public void destroy() {
            OkHttpUtils.close();
        }
    }
}
