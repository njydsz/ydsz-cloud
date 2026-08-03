package com.njydsz.common.core.trace;

import java.util.UUID;

/**
 * TraceId 生成器（UUID）。
 *
 * <p>基于 {@link UUID#randomUUID()}（内部使用 SecureRandom 随机源），
 * 去除连字符后生成 32 位十六进制字符串，保证全局唯一。</p>
 *
 * <p><b>线程安全：</b>{@link UUID#randomUUID()} 线程安全。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * String traceId = TraceIdGenerator.generate();  // 如 "a1b2c3d4e5f67890abcdef1234567890"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TraceIdGenerator {

    private TraceIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 32 位十六进制 TraceId。
     *
     * @return 32 位十六进制字符串
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
