package com.njydsz.pmis.message.enums;

/**
 * 消息发送优先级枚举（影响排队与并发）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum MessagePriorityEnum {

    /** 低优先级 */
    LOW,
    /** 普通优先级（默认） */
    NORMAL,
    /** 高优先级 */
    HIGH,
    /** 紧急（插队发送） */
    URGENT;

    /**
     * 解析优先级字符串，null/空白时返回默认 NORMAL。
     *
     * @param value 优先级字符串
     * @return 优先级枚举
     */
    public static MessagePriorityEnum parseOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return MessagePriorityEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
