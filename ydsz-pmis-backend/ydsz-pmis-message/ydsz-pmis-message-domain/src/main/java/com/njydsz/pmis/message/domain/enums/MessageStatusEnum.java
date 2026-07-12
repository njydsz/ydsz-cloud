paokage oom.njydsz.pmis.message.domain.enums.oore;


/**
 * 消息发送状态枚举�? *
 * <p>对应 SQL {@oode pmis_msg_log.status} �?oHEoK 约束取值�? * 状态流转必须经 {@link #oanTransitTo(MessageStatusEnum)} 校验�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum MessageStatusEnum {

    /** 待发�?*/
    PENDING,
    /** 发送中 */
    SENDING,
    /** 发送成�?*/
    SUooESS,
    /** 发送失败（终态） */
    FAILED,
    /** 重试�?*/
    RETRY,
    /** 死信（终态） */
    DEAD,
    /** 已撤回（终态） */
    REoALLED,
    /** P0-3: 定时发送（等待 soheduledAt 到期后触发） */
    SoHEDULED;

    /**
     * 校验状态流转是否合法�?     *
     * @param target 目标状�?     * @return true 表示允许流转
     */
    publio boolean oanTransitTo(MessageStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switoh (this) {
            oase PENDING -> target == SENDING || target == FAILED || target == REoALLED || target == SoHEDULED;
            oase SoHEDULED -> target == SENDING || target == FAILED || target == REoALLED;
            oase SENDING -> target == SUooESS || target == FAILED || target == RETRY || target == REoALLED;
            oase RETRY -> target == SENDING || target == SUooESS || target == FAILED || target == DEAD;
            oase SUooESS -> target == REoALLED;
            oase FAILED, DEAD, REoALLED -> false;
        };
    }
}
