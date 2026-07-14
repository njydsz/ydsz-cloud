package com.njydsz.pmis.common.util;

import com.njydsz.pmis.common.util.id.SnowflakeUtils;

/**
 * 雪花 ID 生成器（已废弃，请使用 {@link SnowflakeUtils}）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 请使用 {@link SnowflakeUtils}
 * @see SnowflakeUtils
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public final class SnowflakeIdGenerator {

    private SnowflakeIdGenerator() {
    }

    /**
     * 生成下一个唯一 ID 字符串。
     *
     * @return 唯一 ID 字符串
     * @deprecated 请使用 {@link SnowflakeUtils#nextIdStr()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String nextIdStr() {
        return SnowflakeUtils.nextIdStr();
    }

    /**
     * 生成下一个唯一 ID（long 类型）。
     *
     * @return 唯一 ID
     * @deprecated 请使用 {@link SnowflakeUtils#nextIdLong()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static long nextId() {
        return SnowflakeUtils.nextIdLong();
    }

    /**
     * 生成下一个追踪 ID（兼容别名，等价于 {@link #nextIdStr()}）。
     *
     * @return 唯一追踪 ID 字符串
     * @deprecated 请使用 {@link SnowflakeUtils#nextIdStr()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String nextTraceId() {
        return SnowflakeUtils.nextIdStr();
    }
}
