package com.njydsz.pmis.common.core.config;

import java.util.UUID;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.core.trace.SnowflakeTraceIdSupplier;
import com.njydsz.pmis.common.core.trace.TraceIdSupplier;

/**
 * Trace 模块自动配置类
 *
 * <p>注册 {@link TraceIdSupplier} Bean，提供 TraceId 的生成策略。
 * 支持 {@code uuid}（默认，无序）和 {@code snowflake}（有序，可排序日志）两种策略，
 * 通过 {@code ydsz.core.trace.id-type} 配置项切换。</p>
 *
 * <p>业务方可提供自定义 {@link TraceIdSupplier} Bean 覆盖默认实现。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.core.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceAutoConfiguration {

    /**
     * UUID TraceId 供应器（默认策略）
     *
     * <p>使用 UUID（去除连字符）生成 TraceId，长度 32 位。
     * 当 {@code ydsz.core.trace.id-type=uuid} 或未配置时生效。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdSupplier.class)
    @ConditionalOnProperty(prefix = "ydsz.core.trace", name = "id-type",
            havingValue = "uuid", matchIfMissing = true)
    public TraceIdSupplier uuidTraceIdSupplier() {
        return () -> UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Snowflake TraceId 供应器（有序策略）
     *
     * <p>基于 Snowflake 算法生成 16 位十六进制 TraceId，按时间有序，
     * 可直接按 traceId 排序还原请求时序。
     * 当 {@code ydsz.core.trace.id-type=snowflake} 时生效。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdSupplier.class)
    @ConditionalOnProperty(prefix = "ydsz.core.trace", name = "id-type", havingValue = "snowflake")
    public TraceIdSupplier snowflakeTraceIdSupplier() {
        return new SnowflakeTraceIdSupplier();
    }
}
