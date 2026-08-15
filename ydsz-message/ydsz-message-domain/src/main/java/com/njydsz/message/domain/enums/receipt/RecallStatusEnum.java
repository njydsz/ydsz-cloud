package com.njydsz.message.domain.enums.receipt;


/**
 * 消息撤回状态枚举
 *
 * <p>对应 SQL {@code ydsz_msg_log.recall_status} 的 CHECK 约束取值。
 * 撤回操作由 {@code RecallService} 发起，撤回后消息在接收侧展示为「该消息已被撤回」。
 *
 * <p><b>状态说明：</b>
 * <ul>
 *   <li>{@link #NONE} — 未撤回（默认值，消息正常展示）</li>
 *   <li>{@link #RECALLED} — 已撤回（终态，消息内容被清空，接收侧显示撤回提示）</li>
 * </ul>
 *
 * <p><b>撤回约束：</b>仅 {@code SUCCESS} 状态的消息可撤回，且撤回有时效限制
 * （默认 24 小时内，由 {@code ydsz.message.recall-timeout-hours} 配置）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RecallStatusEnum {

    /** 未撤回 */
    NONE,
    /** 已撤回 */
    RECALLED
}
