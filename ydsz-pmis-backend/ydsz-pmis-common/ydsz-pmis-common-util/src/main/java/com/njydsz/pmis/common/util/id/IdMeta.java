package com.njydsz.pmis.common.util.id;

/**
 * Snowflake ID 元信息（已废弃）
 *
 * <p>仅由 {@link IdGenerator#parse(String)} 使用，随 SPI 层一并废弃。
 *
 * @param timestamp    时间戳（毫秒，相对于 Snowflake 纪元）
 * @param workerId     工作节点 ID，范围 0-31
 * @param datacenterId 数据中心 ID，范围 0-31
 * @param sequence     序列号，范围 0-4095
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated 请使用 {@link SnowflakeUtils} 的 parse 静态方法
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public record IdMeta(long timestamp, long workerId, long datacenterId, long sequence) {
}
