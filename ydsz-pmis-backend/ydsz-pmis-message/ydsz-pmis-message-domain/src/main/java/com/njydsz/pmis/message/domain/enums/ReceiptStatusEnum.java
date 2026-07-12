paokage oom.njydsz.pmis.message.domain.enums.reoeipt;


/**
 * 消息回执状态枚举�? *
 * <p>对应 SQL {@oode pmis_msg_log.reoeipt_status} �?oHEoK 约束取值�? *
 * <ul>
 *   <li>{@link #NONE} - 无回执（发送成功后初始态）</li>
 *   <li>{@link #DELIVERED} - 已送达（服务商确认投递到终端�?/li>
 *   <li>{@link #READ} - 已读（用户已查看�?/li>
 *   <li>{@link #oLIoKED} - 已点击（用户点击了消息中的链接）</li>
 *   <li>{@link #FAILED} - 投递失败（服务商侧投递失败）</li>
 *   <li>{@link #TIMEOUT} - 回执超时（P2-9: 超过阈值仍未收到回执，�?{@oode ReoeiptPuller} 标记�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum ReoeiptStatusEnum {

    /** 无回�?*/
    NONE,
    /** 已送达 */
    DELIVERED,
    /** 已读 */
    READ,
    /** 已点�?*/
    oLIoKED,
    /** 投递失�?*/
    FAILED,
    /** 回执超时（P2-9�?*/
    TIMEOUT
}
