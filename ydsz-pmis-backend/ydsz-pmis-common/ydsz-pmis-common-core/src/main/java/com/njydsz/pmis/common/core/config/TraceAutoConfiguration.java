package com.njydsz.pmis.common.core.config;

import java.util.UUID;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.core.trace.TraceIdSupplier;

/**
 * Trace 模块自动配置类
 *
 * <p>注册 {@link TraceIdSupplier} Bean，提供 TraceId 的生成策略。
 * 业务方可提供自定义 {@link TraceIdSupplier} Bean 覆盖默认实现（如基于 Snowflake、ULID 等算法）。</p>
 *
 * <p><b>线程安全性：</b>{@link TraceIdSupplier} 必须保证线程安全，因其会被多线程并发调用。</p>
 *
 * <p><b>自定义示例：</b></p>
 * <pre>{@code
 * @Bean
 * public TraceIdSupplier customTraceIdSupplier() {
 *     return () -> IdUtil.fastSimpleUUID();
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.core.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceAutoConfiguration {

    /**
     * TraceId 供应器
     * <p>默认使用 UUID（去除连字符）生成 TraceId，长度 32 位
     * <p>可通过提供自定义 {@link TraceIdSupplier} Bean 覆盖默认实现
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdSupplier.class)
    public TraceIdSupplier traceIdSupplier() {
        return () -> UUID.randomUUID().toString().replace("-", "");
    }
}
