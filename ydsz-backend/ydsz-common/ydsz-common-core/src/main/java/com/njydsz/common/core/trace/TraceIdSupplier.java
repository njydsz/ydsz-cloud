package com.njydsz.common.core.trace;

/**
 * TraceId 供应器
 *
 * <p>SPI 接口，提供 TraceId 的生成策略。默认实现 {@link TraceIdGenerator#DEFAULT_SUPPLIER}
 * 使用 UUID（去除连字符）。业务方可提供自定义实现覆盖默认策略，
 * 例如基于雪花算法（{@link SnowflakeTraceIdSupplier}）等。
 *
 * <p><b>接入方式：</b>
 * <ol>
 *   <li>实现本接口，提供 {@link #generate()} 方法</li>
 *   <li>将实现类注册为 Spring Bean（{@code @Component}）</li>
 *   <li>{@code TraceAutoConfiguration} 通过 {@code @ConditionalOnMissingBean} 自动选择</li>
 *   <li>调用 {@link TraceIdGenerator#setSupplier(TraceIdSupplier)} 将实现注入到静态 holder</li>
 * </ol>
 *
 * <p><b>实现约束：</b>
 * <ul>
 *   <li>{@link #generate()} 必须线程安全（高并发调用）</li>
 *   <li>生成的 TraceId 应全局唯一，长度建议 16-32 字符</li>
 *   <li>时钟回拨场景下应能优雅降级（参见 {@link SnowflakeTraceIdSupplier}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TraceIdGenerator
 * @see SnowflakeTraceIdSupplier
 */
@FunctionalInterface
public interface TraceIdSupplier {

    /**
     * 生成 TraceId
     *
     * @return 生成的 TraceId 字符串
     */
    String generate();
}
