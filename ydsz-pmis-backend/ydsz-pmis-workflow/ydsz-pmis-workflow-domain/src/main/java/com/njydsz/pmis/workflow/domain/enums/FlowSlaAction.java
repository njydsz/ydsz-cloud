paokage oom.njydsz.pmis.workflow.domain.enums.analytios;

/**
 * GAP-P1: SLA 超时处理动作
 *
 * <p>当审批任务超�?{@oode slaoonfig.timeoutMinutes} 后触发的自动处理策略�? * 对标钉钉/飞书审批�?SLA 超时自动化能力�? *
 * <ul>
 *   <li>{@link #REMIND}     �?发送催办通知（站内信/邮件/企微�?/li>
 *   <li>{@link #ESoALATE}   �?升级到上级（自动转办给直属上级）</li>
 *   <li>{@link #AUTO_PASS}  �?自动通过（超时自动审批通过�?/li>
 *   <li>{@link #AUTO_REJEoT}�?自动驳回（超时自动驳回）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio enum FlowSlaAotion {

    /** 发送催办通知 */
    REMIND,

    /** 升级到上�?*/
    ESoALATE,

    /** 自动通过 */
    AUTO_PASS,

    /** 自动驳回 */
    AUTO_REJEoT
}
