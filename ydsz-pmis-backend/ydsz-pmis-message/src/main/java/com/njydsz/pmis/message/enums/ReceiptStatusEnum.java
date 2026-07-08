package com.njydsz.pmis.message.enums;


/**
 * 消息回执状态枚举。
 *
 * <p>对应 SQL {@code pmis_msg_log.receipt_status} 的 CHECK 约束取值。
 *
 * <ul>
 *   <li>{@link #NONE} - 无回执（发送成功后初始态）</li>
 *   <li>{@link #DELIVERED} - 已送达（服务商确认投递到终端）</li>
 *   <li>{@link #READ} - 已读（用户已查看）</li>
 *   <li>{@link #CLICKED} - 已点击（用户点击了消息中的链接）</li>
 *   <li>{@link #FAILED} - 投递失败（服务商侧投递失败）</li>
 *   <li>{@link #TIMEOUT} - 回执超时（P2-9: 超过阈值仍未收到回执，由 {@code ReceiptPuller} 标记）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ReceiptStatusEnum {

    /** 无回执 */
    NONE,
    /** 已送达 */
    DELIVERED,
    /** 已读 */
    READ,
    /** 已点击 */
    CLICKED,
    /** 投递失败 */
    FAILED,
    /** 回执超时（P2-9） */
    TIMEOUT
}
