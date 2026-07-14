package com.njydsz.pmis.common.event.model;

/**
 * Outbox 消息状态枚举
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #PENDING} → {@link #SENT}（正常投递成功）</li>
 *   <li>{@link #PENDING} → {@link #PENDING}（重试中，retryCount++）</li>
 *   <li>{@link #PENDING} → {@link #DEAD_LETTER}（超过最大重试次数）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
public enum OutboxStatus {

    /** 待投递 */
    PENDING,

    /** 已投递成功 */
    SENT,

    /** 死信（超过最大重试次数，需人工介入） */
    DEAD_LETTER,

    /** 投递失败（临时不可用） */
    FAILED
}
