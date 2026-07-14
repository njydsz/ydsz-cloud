package com.njydsz.pmis.common.core.trace;

import java.util.UUID;

/**
 * TraceId 生成器（默认实现）
 *
 * <p>提供基于 UUID 的 TraceId 生成策略（去除连字符，保留 32 位十六进制）。
 * 高并发场景下表现稳定，但不可排序、不可反解；若业务依赖有序或可解析的 TraceId，
 * 可通过 {@link TraceIdSupplier} SPI 注入自定义实现（如 Snowflake、ULID 等）覆盖默认行为。</p>
 *
 * <p><b>线程安全性：</b>本类为无状态工具类，{@link #generate()} 在多线程并发调用下安全。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 直接生成
 * String traceId = TraceIdGenerator.generate();
 *
 * // 自定义实现
 * TraceIdSupplier supplier = () -> "custom-" + System.nanoTime();
 * String customTraceId = supplier.generate();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see TraceIdSupplier
 */
public final class TraceIdGenerator {

    private TraceIdGenerator() {
        // 工具类禁止实例化
    }

    /**
     * 生成 TraceId
     *
     * <p>基于 {@link UUID#randomUUID()} 去除连字符，长度为 32。
     * 该方法适用于绝大多数分布式追踪场景，并发安全、长度适中、可读性好。</p>
     *
     * <p><b>性能提示：</b>单次生成耗时约数百纳秒，高 QPS 场景（&gt; 10w/s）下，
     * 建议改为预生成 + 队列分发模式以降低 GC 压力。</p>
     *
     * @return 32 位 UUID 字符串（去除连字符）
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
