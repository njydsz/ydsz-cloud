package com.njydsz.common.util.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.util.id.SnowflakeHealthIndicator;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.id.SnowflakeProperties;
import com.njydsz.common.util.id.WorkerIdAllocator;
import com.njydsz.common.util.id.WorkerIdAllocatorChain;
import com.njydsz.common.util.spring.SpringContextHolder;

/**
 * 通用工具类自动配置。
 *
 * <p>注册项目级工具 Bean：
 * <ul>
 *   <li>{@link SpringContextHolder} — ApplicationContext 静态持有者</li>
 *   <li>{@link WorkerIdAllocatorChain} — WorkerId 分配策略链（PodOrdinal → IpHash → FilePersisted）</li>
 *   <li>{@link SnowflakeHealthIndicator} — Snowflake ID 生成器健康检查（仅 actuator 在 classpath 时注册）</li>
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
     * WorkerId 分配策略链 —— PodOrdinal → IpHash → FilePersisted。
     *
     * <p>业务方可声明自定义 {@link WorkerIdAllocator} Bean，通过 {@link WorkerIdAllocatorChain#prepend} 插入更高优先级策略。
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkerIdAllocatorChain workerIdAllocatorChain() {
        return WorkerIdAllocatorChain.defaults();
    }

    /**
     * Snowflake ID 生成器健康检查 Bean
     *
     * <p>仅当 classpath 上存在 {@link org.springframework.boot.health.contributor.HealthIndicator}
     * （即引入 spring-boot-actuator）时才加载，避免缺少 actuator 依赖时
     * 因 {@link SnowflakeHealthIndicator} 实现的接口类不存在而触发 {@code NoClassDefFoundError}。
     *
     * <p>使用内部静态 @Configuration 类 + {@code @ConditionalOnClass} 是 Spring Boot
     * 标准做法：通过 ASM 字节码分析评估条件，避免在条件不满足时触发相关类的加载。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthIndicatorConfiguration {

        /**
         * 注册 SnowflakeHealthIndicator Bean
         *
         * <p>检查 Snowflake ID 生成器的健康状态（时钟回拨、workerId 有效性、ID 生成能力）。
         * 仅在 {@code ydsz.util.snowflake.enabled=true}（或缺省，matchIfMissing=true）时注册，
         * 避免在 Snowflake 被显式禁用时仍强制初始化该组件。
         *
         * @return SnowflakeHealthIndicator 实例
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", matchIfMissing = true)
        public SnowflakeHealthIndicator snowflakeHealthIndicator(ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider) {
            return new SnowflakeHealthIndicator(idGeneratorProvider);
        }
    }

}
