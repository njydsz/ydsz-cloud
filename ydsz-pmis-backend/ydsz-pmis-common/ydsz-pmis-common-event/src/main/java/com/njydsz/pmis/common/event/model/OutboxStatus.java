package com.njydsz.pmis.common.event.model;

/**
 * Outbox 消息状态枚举
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #PENDING} → {@link #PROCESSING}（被某个实例 claim，正在投递）</li>
 *   <li>{@link #PROCESSING} → {@link #SENT}（正常投递成功）</li>
 *   <li>{@link #PROCESSING} → {@link #PENDING}（投递失败，重试中，retryCount++）</li>
 *   <li>{@link #PROCESSING} → {@link #DEAD_LETTER}（超过最大重试次数）</li>
 *   <li>{@link #PROCESSING} → {@link #PENDING}（实例宕机，reclaim 回收）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum OutboxStatus {

    /** 待投递 */
    PENDING,

    /** 处理中（已被某个实例 claim，正在投递） */
    PROCESSING,

    /** 已投递成功 */
    SENT,

    /** 死信（超过最大重试次数，需人工介入） */
    DEAD_LETTER,

    /** 投递失败（临时不可用，等待 reclaim） */
    FAILED
}
