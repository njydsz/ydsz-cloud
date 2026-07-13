package com.njydsz.pmis.common.util;

import com.njydsz.pmis.common.util.id.SnowflakeUtils;

/**
 * 雪花 ID 生成器（兼容入口）。
 *
 * <p>委托给 {@link SnowflakeUtils} 实现，保持与旧代码的兼容性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see SnowflakeUtils
 */
public final class SnowflakeIdGenerator {

    private SnowflakeIdGenerator() {
    }

    /**
     * 生成下一个唯一 ID 字符串。
     *
     * @return 唯一 ID 字符串
     */
    public static String nextIdStr() {
        return SnowflakeUtils.nextIdStr();
    }

    /**
     * 生成下一个唯一 ID（long 类型）。
     *
     * @return 唯一 ID
     */
    public static long nextId() {
        return SnowflakeUtils.nextIdLong();
    }

    /**
     * 生成下一个追踪 ID（兼容别名，等价于 {@link #nextIdStr()}）。
     *
     * @return 唯一追踪 ID 字符串
     */
    public static String nextTraceId() {
        return SnowflakeUtils.nextIdStr();
    }
}
