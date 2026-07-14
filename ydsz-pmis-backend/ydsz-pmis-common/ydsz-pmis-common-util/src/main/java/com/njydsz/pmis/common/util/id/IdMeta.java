package com.njydsz.pmis.common.util.id;

/**
 * Snowflake ID 元信息
 *
 * <p>用于解析 Snowflake ID 时返回各组成部分的只读快照。
 * Snowflake ID 结构（64 位）：
 * <ul>
 *   <li>1 位符号位（始终为 0）</li>
 *   <li>41 位时间戳（毫秒级，相对于自定义纪元）</li>
 *   <li>5 位数据中心 ID</li>
 *   <li>5 位工作节点 ID</li>
 *   <li>12 位序列号（单毫秒内的自增值）</li>
 * </ul>
 *
 * @param timestamp    时间戳（毫秒，相对于 Snowflake 纪元）
 * @param workerId     工作节点 ID，范围 0-31
 * @param datacenterId 数据中心 ID，范围 0-31
 * @param sequence     序列号，范围 0-4095
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 */
public record IdMeta(long timestamp, long workerId, long datacenterId, long sequence) {
}
