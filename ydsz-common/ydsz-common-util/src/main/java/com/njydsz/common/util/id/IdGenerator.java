package com.njydsz.common.util.id;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 分布式 ID 生成器统一门面。
 *
 * <p>为非 Spring 管理的类（Domain Model、工具类、非 Bean Pojo）提供
 * 便捷的 ID 生成入口，替代 {@code UUID.randomUUID()}。</p>
 *
 * <p><b>两种使用方式：</b>
 * <ol>
 *   <li><b>静态门面（本类）：</b>适用于非 Spring 托管场景，通过注册的
 *       {@link Supplier} 懒获取 {@link SnowflakeIdGenerator} Bean，未注册时安全降级为伪随机 long</li>
 *   <li><b>依赖注入：</b>Spring Bean 直接注入 {@link SnowflakeIdGenerator}（由 {@link SnowflakeIdBean} 注册）</li>
 * </ol>
 *
 * <p><b>降级策略：</b>
 * <ul>
 *   <li>ydsz.util.snowflake.fallback-to-uuid=false（默认）：降级使用 {@link ThreadLocalRandom#nextLong()}，
 *       比特分布均匀，不会产生 B+Tree 热点</li>
 *   <li>ydsz.util.snowflake.fallback-to-uuid=true：行为与旧版一致，使用 {@code UUID.getMostSignificantBits()}</li>
 * </ul>
 *
 * <p><b>推荐实践：</b>Spring Bean 注入 {@link SnowflakeIdGenerator} 优于使用本类，
 * 避免服务定位器反模式。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
public final class IdGenerator {

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 由 UtilAutoConfiguration 设置的 Supplier，用于替代 SpringContextHolder 查找 */
    private static volatile Supplier<SnowflakeIdGenerator> generatorSupplier;

    /**
     * 注册 SnowflakeIdGenerator 的 Supplier。
     *
     * <p>由 {@code UtilAutoConfiguration} 在容器初始化时调用，传入 ObjectProvider 风格的 Supplier。
     *
     * @param supplier SnowflakeIdGenerator 提供者，非空
     */
    public static void setGeneratorSupplier(Supplier<SnowflakeIdGenerator> supplier) {
        generatorSupplier = supplier;
    }

    /**
     * 是否允许降级到 UUID（bit 分布与 Snowflake 不同，可能产生索引热点）。
     * <p>默认关闭，降级时使用 {@link ThreadLocalRandom#nextLong()} 替代。
     */
    private static volatile boolean fallbackToUuid = false;

    /** 缓存 Bean 引用以跳过多次 SpringContextHolder 查找（仅成功结果被永久缓存） */
    private static volatile SnowflakeIdGenerator cached;

    /**
     * 上次获取失败的时间戳（毫秒）。
     *
     * <p>区别于"尚未解析"与"确实不存在"：失败仅记录冷却时间，
     * 冷却期过后会再次尝试获取 Bean，避免启动期上下文未就绪时
     * 被一次性判定为"不可用"而永久降级为 UUID（可能与 Snowflake 主键空间重叠）。
     */
    private static volatile long lastFailureMillis = 0L;

    /** 失败重试冷却时间（毫秒），避免非 Spring 环境下每次生成 ID 都触发 getBean 查找 */
    private static final long FAILURE_COOLDOWN_MILLIS = 60_000L;

    /**
     * 配置降级策略。
     *
     * @param useUuid {@code true} 时使用 {@code UUID.getMostSignificantBits()} 降级；
     *                {@code false}（推荐）时使用 {@link ThreadLocalRandom#nextLong()} 降级
     */
    public static void setFallbackToUuid(boolean useUuid) {
        fallbackToUuid = useUuid;
    }

    /**
     * 生成下一个分布式唯一 ID（字符串形式）。
     *
     * @return Snowflake ID 字符串；容器未初始化时降级为伪随机字符串
     */
    public static String nextIdStr() {
        SnowflakeIdGenerator gen = getGenerator();
        if (gen != null) {
            return String.valueOf(gen.nextId());
        }
        if (fallbackToUuid) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
        return Long.toString(ThreadLocalRandom.current().nextLong());
    }

    /**
     * 生成下一个分布式唯一 ID（long 形式）。
     *
     * @return Snowflake ID；容器未初始化时降级为伪随机 long
     */
    public static long nextId() {
        SnowflakeIdGenerator gen = getGenerator();
        if (gen != null) {
            return gen.nextId();
        }
        if (fallbackToUuid) {
            return java.util.UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        }
        return ThreadLocalRandom.current().nextLong();
    }

    private static SnowflakeIdGenerator getGenerator() {
        SnowflakeIdGenerator gen = cached;
        if (gen != null) {
            return gen;
        }
        Supplier<SnowflakeIdGenerator> supplier = generatorSupplier;
        if (supplier == null) {
            return null;
        }
        // 失败冷却：避免非 Spring 环境下每次生成 ID 都触发 getBean 查找
        long now = System.currentTimeMillis();
        if (now - lastFailureMillis < FAILURE_COOLDOWN_MILLIS) {
            return null;
        }
        synchronized (IdGenerator.class) {
            gen = cached;
            if (gen != null) {
                return gen;
            }
            if (System.currentTimeMillis() - lastFailureMillis < FAILURE_COOLDOWN_MILLIS) {
                return null;
            }
            try {
                gen = supplier.get();
                cached = gen;
                return gen;
            } catch (Exception ignored) {
                // 容器尚未初始化，本次降级到 UUID；不缓存失败，冷却期过后会重试
                lastFailureMillis = System.currentTimeMillis();
                return null;
            }
        }
    }

    /**
     * 测试用：重置缓存与失败冷却。
     */
    static void resetForTesting() {
        cached = null;
        lastFailureMillis = 0L;
    }
}
