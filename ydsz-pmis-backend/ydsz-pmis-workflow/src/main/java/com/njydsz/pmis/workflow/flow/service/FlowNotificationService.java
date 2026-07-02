package com.njydsz.pmis.workflow.flow.service;

import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 工作流消息通知服务
 *
 * <p>对接站内信/邮件/企业微信等通知通道，统一管理工作流关键事件的消息推送。
 * 与 {@link com.njydsz.pmis.workflow.flow.engine.FlowNotificationHelper} 的区别：
 * <ul>
 *   <li>FlowNotificationHelper — 通过 Feign 调用通知中心微服务（跨服务）</li>
 *   <li>FlowNotificationService — 本地通知服务，支持多通道（IN_APP/EMAIL/WEBHOOK），可独立扩展</li>
 * </ul>
 *
 * <p>所有方法均为"尽力而为"语义：内部 try-catch 吞异常，不拖垮主流程事务。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowNotificationService {

    /**
     * 任务创建通知
     *
     * @param instanceId    流程实例 ID
     * @param taskId        任务 ID
     * @param assigneeId    办理人 ID
     * @param assigneeName  办理人姓名
     */
    void notifyTaskCreated(Long instanceId, Long taskId, String assigneeId, String assigneeName);

    /**
     * 催办通知
     *
     * @param instanceId    流程实例 ID
     * @param taskId        任务 ID
     * @param assigneeIds   被催办人 ID 列表
     * @param comment       催办备注
     */
    void notifyUrge(Long instanceId, Long taskId, List<String> assigneeIds, String comment);

    /**
     * 抄送通知
     *
     * @param instanceId  流程实例 ID
     * @param nodeCode    抄送节点编码
     * @param ccUserIds   抄送接收人 ID 列表
     * @param title       通知标题
     */
    void notifyCc(Long instanceId, String nodeCode, List<Long> ccUserIds, String title);

    /**
     * 流程完成通知
     *
     * @param instanceId  流程实例 ID
     * @param initiatorId 发起人 ID
     */
    void notifyInstanceCompleted(Long instanceId, Long initiatorId);

    /**
     * 流程驳回通知
     *
     * @param instanceId  流程实例 ID
     * @param initiatorId 发起人 ID
     * @param reason      驳回原因
     */
    void notifyInstanceRejected(Long instanceId, Long initiatorId, String reason);

    /**
     * SLA 超时通知
     *
     * @param instanceId  流程实例 ID
     * @param taskId      任务 ID
     * @param assigneeId  办理人 ID
     * @param action      超时动作（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）
     */
    void notifySlaTimeout(Long instanceId, Long taskId, String assigneeId, String action);

    /**
     * 通用发送
     *
     * @param channel 通知通道：IN_APP / EMAIL / WEBHOOK
     * @param userId  接收人 ID
     * @param title   通知标题
     * @param content 通知内容
     * @param extra   扩展参数（如跳转链接、业务类型等）
     */
    void send(String channel, Long userId, String title, String content, Map<String, Object> extra);
}
