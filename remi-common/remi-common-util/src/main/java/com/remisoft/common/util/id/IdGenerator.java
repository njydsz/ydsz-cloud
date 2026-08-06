package com.remisoft.common.util.id;

import com.remisoft.common.util.spring.SpringContextHolder;

/**
 * 分布式 ID 静态工具入口。
 *
 * <p>为非 Spring 管理的类（Domain Model、工具类、非 Bean Pojo）提供
 * 便捷的 Snowflake ID 生成入口，替代 {@code UUID.randomUUID()}。</p>
 *
 * <p>内部通过 {@link SpringContextHolder} 懒获取 {@link SnowflakeIdGenerator} Bean，
 * 容器未初始化时安全降级为 {@link java.util.UUID}。</p>
 *
 * <p><b>Spring Bean 应注入 {@link SnowflakeIdGenerator}，不应使用本类。</b></p>
 *
 * @author remi-team
 * @since 2.1.0
 */
public final class IdGenerator {

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 缓存 Bean 引用以跳过多次 SpringContextHolder 查找 */
    private static volatile SnowflakeIdGenerator cached;

    /**
     * 生成下一个分布式唯一 ID（字符串形式）。
     *
     * @return Snowflake ID 字符串；容器未初始化时降级为 UUID 字符串
     */
    public static String nextIdStr() {
        SnowflakeIdGenerator gen = getGenerator();
        if (gen != null) {
            return String.valueOf(gen.nextId());
        }
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成下一个分布式唯一 ID（long 形式）。
     *
     * @return Snowflake ID；容器未初始化时降级为 UUID.hashCode()
     */
    public static long nextId() {
        SnowflakeIdGenerator gen = getGenerator();
        if (gen != null) {
            return gen.nextId();
        }
        return java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    private static SnowflakeIdGenerator getGenerator() {
        if (cached != null) {
            return cached;
        }
        try {
            cached = SpringContextHolder.getBean(SnowflakeIdGenerator.class);
        } catch (Exception ignored) {
            // 容器未初始化，降级到 UUID
        }
        return cached;
    }

    /**
     * 测试用：重置缓存。
     */
    static void resetForTesting() {
        cached = null;
    }
}
