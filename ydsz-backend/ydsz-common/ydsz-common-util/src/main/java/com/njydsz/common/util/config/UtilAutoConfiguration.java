package com.njydsz.common.util.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.util.health.UtilHealthIndicator;
import com.njydsz.common.util.id.SnowflakeHealthIndicator;
import com.njydsz.common.util.id.SnowflakeProperties;
import com.njydsz.common.util.spring.SpringContextHolder;

/**
 * 通用工具类自动配置。
 *
 * <p>注册项目级工具 Bean：
 * <ul>
 *   <li>{@link SpringContextHolder} — ApplicationContext 静态持有者</li>
 *   <li>{@link SnowflakeHealthIndicator} — Snowflake ID 生成器健康检查</li>
 *   <li>{@link UtilHealthIndicator} — 工具模块健康检查（Snowflake 状态、JVM 内存指标）</li>
 * </ul>
 *
 * <p>所有工具 Bean 均为无状态、线程安全，可直接注入使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties({SnowflakeProperties.class})
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

}
