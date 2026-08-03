package com.njydsz.common.core.trace;

import java.security.SecureRandom;

/**
 * TraceId 生成器（统一入口）
 *
 * <p>全项目 TraceId 生成的唯一入口，内部委托到 {@link TraceIdSupplier} SPI。
 * 默认使用 UUID 策略（去除连字符，32 位十六进制），可通过 {@code ydsz.core.trace.id-type}
 * 配置项切换为 Snowflake（有序，16 位十六进制）等策略。</p>
 *
 * <p><b>SPI 接入流程：</b>
 * <ol>
 *   <li>Spring 容器启动时，上层配置类（如 TraceAutoConfiguration）
 *       根据 {@code ydsz.core.trace.id-type} 注册对应的 {@link TraceIdSupplier} Bean</li>
 *   <li>配置类同时调用 {@link #setSupplier(TraceIdSupplier)}
 *       将 Bean 注入到本类的静态 holder</li>
 *   <li>所有调用 {@link #generate()} 的模块（Gateway、TraceFilter、TracerUtils 等）
 *       自动使用配置的策略，无需感知 SPI 细节</li>
 * </ol>
 *
 * <p><b>设计决策 — 静态 holder 模式：</b>
 * 本类使用 {@code volatile} 静态字段持有策略实例，而非 Spring Bean 依赖注入。
 * 这是因为 TraceId 生成在项目极早期（如 Filter 初始化、日志框架启动）即被调用，
 * 此时 Spring 容器可能尚未就绪。静态 holder 保证零依赖、即时可用。
 * 代价是多 ApplicationContext 场景下最后一个上下文的策略会覆盖前一个，
 * 以及单元测试间需手动调用 {@link #resetToDefault()} 隔离状态。</p>
 *
 * <p><b>线程安全性：</b>{@code supplier} 字段使用 {@code volatile} 保证可见性，
 * {@link #generate()} 在多线程并发调用下安全。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 直接生成（自动使用配置的策略）
 * String traceId = TraceIdGenerator.generate();
 *
 * // 运行时切换策略（通常仅用于测试）
 * TraceIdGenerator.setSupplier(() -> "custom-" + System.nanoTime());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TraceIdSupplier
 * @see SnowflakeTraceIdSupplier
 */
public final class TraceIdGenerator {

    /** 默认 UUID 供应器（32 位 hex，无连字符） */
    private static final TraceIdSupplier DEFAULT_SUPPLIER = new UuidTraceIdSupplier();

    /**
     * 高性能 UUID TraceId 供应器。
     *
     * <p>直接编码 128 位随机数为 32 位 hex，避免
     * {@code UUID.randomUUID().toString().replace("-", "")} 产生的
     * 3 个中间 String 对象（UUID 字符串 + replace 结果 + 最终结果）。</p>
     */
    static final class UuidTraceIdSupplier implements TraceIdSupplier {

        /** hex 字符表 */
        private static final char[] HEX_DIGITS = {
                '0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };

        /** 随机源（UUID.randomUUID 内部同样使用 SecureRandom） */
        private static final SecureRandom RANDOM = new SecureRandom();

        @Override
        public String generate() {
            // 一次读取 16 字节，编码为 32 位 hex
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

    /** 当前生效的供应器，volatile 保证多线程可见性 */
    private static volatile TraceIdSupplier supplier = DEFAULT_SUPPLIER;

    private TraceIdGenerator() {
        // 工具类禁止实例化
    }

    /**
     * 生成 TraceId
     *
     * <p>委托到当前注册的 {@link TraceIdSupplier} 生成 TraceId。
     * 默认策略为 UUID（去除连字符，32 位十六进制），
     * 可通过 {@code ydsz.core.trace.id-type=snowflake} 切换为 Snowflake（16 位，有序）。</p>
     *
     * @return TraceId 字符串
     */
    public static String generate() {
        return supplier.generate();
    }

    /**
     * 设置 TraceId 供应器（SPI 注入入口）
     *
     * <p>由上层配置类在容器启动时调用，
     * 将配置的 {@link TraceIdSupplier} Bean 注入到静态 holder。
     * 传入 {@code null} 时恢复为默认 UUID 策略。</p>
     *
     * @param traceIdSupplier TraceId 供应器实例
     */
    public static void setSupplier(TraceIdSupplier traceIdSupplier) {
        supplier = traceIdSupplier != null ? traceIdSupplier : DEFAULT_SUPPLIER;
    }

    /**
     * 获取当前生效的 TraceId 供应器
     *
     * @return 当前供应器实例
     */
    public static TraceIdSupplier getSupplier() {
        return supplier;
    }

    /**
     * 恢复为默认 UUID 策略
     *
     * <p>主要用于单元测试间的状态隔离。</p>
     */
    public static void resetToDefault() {
        supplier = DEFAULT_SUPPLIER;
    }
}
