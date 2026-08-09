package com.njydsz.message.domain.enums.core;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 消息发送状态枚举。
 *
 * <p>对应 SQL {@code ydsz_msg_log.status} 的 CHECK 约束取值。
 * 状态流转必须经 {@link #canTransitTo(MessageStatusEnum)} 校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum MessageStatusEnum implements BaseStatusEnum<MessageStatusEnum> {

    /** 待发送 */
    PENDING,
    /** 发送中 */
    SENDING,
    /** 发送成功 */
    SUCCESS,
    /** 发送失败（终态） */
    FAILED,
    /** 重试中 */
    RETRY,
    /** 死信（终态） */
    DEAD,
    /** 已撤回（终态） */
    RECALLED,
    /** P0-3: 定时发送（等待 scheduledAt 到期后触发） */
    SCHEDULED,
    /** P2-16: 跳过发送（退信/抑制等业务规则拦截,终态） */
    SKIPPED;

    /**
     * 校验状态流转是否合法。
     *
     * @param target 目标状态
     * @return true 表示允许从当前状态流转到目标状态
     */
    @Override
    public boolean canTransitTo(MessageStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == SENDING || target == FAILED || target == RECALLED || target == SCHEDULED || target == SKIPPED;
            case SCHEDULED -> target == SENDING || target == FAILED || target == RECALLED || target == SKIPPED;
            case SENDING -> target == SUCCESS || target == FAILED || target == RETRY || target == RECALLED || target == SKIPPED;
            case RETRY -> target == SENDING || target == SUCCESS || target == FAILED || target == DEAD;
            case SUCCESS -> target == RECALLED;
            case FAILED, DEAD, RECALLED, SKIPPED -> false;
        };
    }

    @Override
    public boolean isTerminal() {
        return this == FAILED || this == DEAD || this == RECALLED || this == SKIPPED;
    }
}
