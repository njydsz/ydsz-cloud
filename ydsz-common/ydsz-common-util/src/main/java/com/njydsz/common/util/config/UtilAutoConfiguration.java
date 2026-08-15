package com.njydsz.common.util.config;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.common.util.http.ServletRequestUtils;
import com.njydsz.common.util.http.TrustedProxyConfiguration;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.util.id.SnowflakeHealthIndicator;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.id.SnowflakeProperties;
import com.njydsz.common.util.id.WorkerIdAllocator;
import com.njydsz.common.util.id.WorkerIdAllocatorChain;

/**
 * 通用工具类自动配置。
 *
 * <p>统一通过 {@code AutoConfiguration.imports} 注册（不依赖业务侧组件扫描），
 * 确保引入依赖即可装配 Snowflake ID 生成器等基础能力，避免因业务主类未扫描
 * {@code com.njydsz.common} 包而导致的静默降级。
 *
 * <p>注册的 Bean：
 * <ul>
 *   <li>{@link SnowflakeIdGenerator} — 分布式 ID 生成器（原 SnowflakeIdBean 并入）</li>
 *   <li>{@link WorkerIdAllocatorChain} — WorkerId 分配策略链（PodOrdinal → IpHash）</li>
 *   <li>{@link SnowflakeHealthIndicator} — Snowflake 健康检查（仅 actuator 在 classpath 时注册）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SnowflakeProperties.class)
public class UtilAutoConfiguration {

    private final ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider;
    private final ObjectProvider<TrustedProxyConfiguration> trustedProxyConfigProvider;

    /**
     * 构造方法注入（优于字段注入，遵循云顶编码规范 5.3 节）。
     *
     * @param idGeneratorProvider         Snowflake ID 生成器提供者
     * @param trustedProxyConfigProvider  可信代理配置提供者
     */
    public UtilAutoConfiguration(ObjectProvider<SnowflakeIdGenerator> idGeneratorProvider,
                                  ObjectProvider<TrustedProxyConfiguration> trustedProxyConfigProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
        this.trustedProxyConfigProvider = trustedProxyConfigProvider;
    }

    /**
     * 注册静态工具类的 Supplier，替代 SpringContextHolder 查找。
     */
    @PostConstruct
    public void registerStaticToolSuppliers() {
        IdGenerator.setGeneratorSupplier(idGeneratorProvider::getIfAvailable);
        ServletRequestUtils.setTrustedProxyConfigSupplier(trustedProxyConfigProvider::getIfAvailable);
    }

    /**
     * Snowflake ID 生成器 Bean（原 SnowflakeIdBean 并入，保证无需组件扫描即可装配）。
     *
     * @param properties Snowflake 配置属性
     * @param allocator  WorkerId 分配策略链
     * @return SnowflakeIdGenerator 实例
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.util.snowflake", name = "enabled", matchIfMissing = true)
    public SnowflakeIdGenerator snowflakeIdGenerator(SnowflakeProperties properties,
                                                      WorkerIdAllocator allocator) {
        int sequenceBits = properties.getSequenceBits() != null
                ? properties.getSequenceBits()
                : SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS;
        return new SnowflakeIdGenerator(properties, allocator, sequenceBits);
    }

    /**
     * WorkerId 分配策略链 —— PodOrdinal → IpHash。
     *
     * <p>业务方可声明自定义 {@link WorkerIdAllocator} Bean，通过 {@link WorkerIdAllocatorChain#prepend} 插入更高优先级策略。
     *
     * @return WorkerIdAllocatorChain 实例
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
         * @param idGeneratorProvider Snowflake ID 生成器提供者
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
