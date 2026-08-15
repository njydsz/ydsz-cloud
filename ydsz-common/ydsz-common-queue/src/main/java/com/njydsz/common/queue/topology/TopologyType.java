package com.njydsz.common.queue.topology;

/**
 * 多 MQ 组合拓扑类型枚举
 *
 * <p>定义消息队列组合使用的拓扑结构，支持主备切换、扇出和多源聚合三种模式。
 *
 * <p><b>拓扑说明：</b>
 * <ul>
 *   <li>{@link #PRIMARY_BACKUP}：主备模式，优先使用主 MQ，主故障时自动切换到备 MQ</li>
 *   <li>{@link #FAN_OUT}：扇出模式，一条消息同时发送到多个 MQ，适用于多通道广播</li>
 *   <li>{@link #AGGREGATION}：聚合模式，从多个 MQ 消费消息，适用于多通道汇聚</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum TopologyType {

    /**
     * 主备模式
     * <p>优先使用主 MQ 发送/消费消息，当主 MQ 故障时自动切换到备 MQ。
     * 适用于对可用性要求高的场景，确保消息不丢失。
     */
    PRIMARY_BACKUP("primary-backup"),

    /**
     * 扇出模式
     * <p>一条消息同时发送到所有参与扇出的 MQ，实现消息多通道广播。
     * 适用于同一消息需要被多个异构系统处理的场景。
     */
    FAN_OUT("fan-out"),

    /**
     * 聚合模式
     * <p>从多个 MQ 消费消息，统一路由到同一个 handler 处理。
     * <p><b>注意：</b>消费顺序不保证，适用于消息来源多通道汇聚处理的场景。
     */
    AGGREGATION("aggregation");

    private final String value;

    TopologyType(String value) {
        this.value = value;
    }

    /**
     * 获取拓扑类型的字符串表示
     *
     * @return 拓扑类型值
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 根据字符串值反序列化为枚举
     *
     * @param value 拓扑类型字符串
     * @return 对应的枚举值
     */
    public static TopologyType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TopologyType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的拓扑类型: " + value);
    }
}
