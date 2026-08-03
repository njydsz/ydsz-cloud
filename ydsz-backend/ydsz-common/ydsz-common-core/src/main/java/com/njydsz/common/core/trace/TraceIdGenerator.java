package com.njydsz.common.core.trace;

import java.security.SecureRandom;

/**
 * TraceId 生成器（纯 UUID，无配置切换）。
 *
 * <p>直接编码 128 位随机数为 32 位十六进制字符串，避免
 * {@code UUID.randomUUID().toString().replace("-", "")} 产生的 3 个中间 String 对象。
 * 使用 {@link SecureRandom}（与 {@code UUID.randomUUID()} 相同的随机源）保证唯一性。</p>
 *
 * <p><b>线程安全：</b>{@link SecureRandom#nextBytes} 方法线程安全，无锁调用。</p>
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

    /** hex 字符表 */
    private static final char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /** 随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 32 位十六进制 TraceId。
     *
     * <p>一次读取 16 字节随机数，编码为 32 位 hex，零中间 String 分配。</p>
     *
     * @return 32 位十六进制字符串
     */
    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        char[] buf = new char[32];
        for (int i = 0; i < 16; i++) {
            int b = bytes[i] & 0xFF;
            buf[i * 2] = HEX_DIGITS[b >>> 4];
            buf[i * 2 + 1] = HEX_DIGITS[b & 0x0F];
        }
        return new String(buf);
    }
}
