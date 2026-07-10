package com.njydsz.pmis.message.enums.core;


/**
 * 消息发送状态枚举。
 *
 * <p>对应 SQL {@code pmis_msg_log.status} 的 CHECK 约束取值。
 * 状态流转必须经 {@link #canTransitTo(MessageStatusEnum)} 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum MessageStatusEnum {

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
    SCHEDULED;

    /**
     * 校验状态流转是否合法。
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    public boolean canTransitTo(MessageStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == SENDING || target == FAILED || target == RECALLED || target == SCHEDULED;
            case SCHEDULED -> target == SENDING || target == FAILED || target == RECALLED;
            case SENDING -> target == SUCCESS || target == FAILED || target == RETRY || target == RECALLED;
            case RETRY -> target == SENDING || target == SUCCESS || target == FAILED || target == DEAD;
            case SUCCESS -> target == RECALLED;
            case FAILED, DEAD, RECALLED -> false;
        };
    }
}
