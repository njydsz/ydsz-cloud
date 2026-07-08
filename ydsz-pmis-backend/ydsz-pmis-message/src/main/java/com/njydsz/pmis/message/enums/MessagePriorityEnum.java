package com.njydsz.pmis.message.enums;

/**
 * 消息发送优先级枚举。
 *
 * <p>P0-5: 影响发送排队顺序和限流策略：
 * <ul>
 *   <li>{@link #URGENT}：最高优先级，可跳过模板/租户维度限流，仅保留接收人维度限流</li>
 *   <li>{@link #HIGH}：高优先级，限流阈值提升 2 倍</li>
 *   <li>{@link #NORMAL}：默认优先级，正常限流</li>
 *   <li>{@link #LOW}：低优先级，适合批量通知，限流阈值减半</li>
 * </ul>
 *
 * <p>数值越大优先级越高，用于排序比较。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public enum MessagePriorityEnum {

    /** 低优先级（批量通知、非紧急公告） */
    LOW(1),
    /** 默认优先级 */
    NORMAL(5),
    /** 高优先级（重要业务通知） */
    HIGH(8),
    /** 紧急（告警、安全验证码） */
    URGENT(10);

    /** 优先级数值（越大越高） */
    private final int value;

    MessagePriorityEnum(int value) {
        this.value = value;
    }

    /**
     * 获取优先级数值。
     *
     * @return 数值
     */
    public int getValue() {
        return value;
    }

    /**
     * 是否可以跳过限流（仅 URGENT）。
     *
     * @return true 表示可跳过模板/租户维度限流
     */
    public boolean canSkipRateLimit() {
        return this == URGENT;
    }

    /**
     * 获取限流倍率（基于 NORMAL=1.0）。
     *
     * @return 限流倍率
     */
    public double rateLimitMultiplier() {
        return switch (this) {
            case URGENT -> 10.0;
            case HIGH -> 2.0;
            case NORMAL -> 1.0;
            case LOW -> 0.5;
        };
    }

    /**
     * 从字符串安全解析优先级，无效时返回 NORMAL。
     *
     * @param str 优先级字符串
     * @return 枚举值
     */
    public static MessagePriorityEnum fromString(String str) {
        if (str == null || str.isBlank()) {
            return NORMAL;
        }
        try {
            return MessagePriorityEnum.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
