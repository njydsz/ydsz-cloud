package com.njydsz.pmis.common.app.util;

import com.njydsz.pmis.common.util.id.SnowflakeUtils;

/**
 * 请求 ID 生成器
 *
 * <p>委托给 {@link SnowflakeUtils} 统一生成分布式唯一 ID，用于在过滤器链中标识单次请求。
 * 本类为工具类，禁止实例化。
 *
 * <p><b>线程安全性：</b>仅包含静态方法，无共享状态，线程安全；
 * 底层 {@link SnowflakeUtils} 在分布式部署中应正确配置 workerId 以避免 ID 冲突。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public final class RequestIdGenerator {

    /**
     * 私有构造方法，工具类禁止实例化。
     *
     * @throws UnsupportedOperationException 任何实例化尝试都会抛出
     */
    private RequestIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成请求 ID
     *
     * @return 唯一请求 ID（雪花算法生成的 long 值字符串）
     */
    public static String generateId() {
        return String.valueOf(SnowflakeUtils.getInstance().nextId());
    }

    /**
     * 生成带前缀的请求 ID
     *
     * <p>常用于区分多端或多个调用链的 ID 前缀。
     *
     * @param prefix 前缀，非空
     * @return 形如 {@code prefix + snowflakeId} 的字符串
     */
    public static String generateId(String prefix) {
        return prefix + SnowflakeUtils.getInstance().nextId();
    }
}
