package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * 流程通知服务。
 *
 * <p>向审批人发送任务通知。作为工作流域的<b>通知适配器</b>， 将工作流事件（待办/催办/抄送/完成/驳回/超时）转换为通知请求， 通过 {@code
 * NotificationClient} Feign 契约投递。
 *
 * <p><b>通道映射（对齐 NotifyChannel 枚举）：</b>
 *
 * <ul>
 *   <li>INAPP → NotifyChannel.INSITE（站内信）
 *   <li>EMAIL → NotifyChannel.EMAIL（邮件）
 *   <li>WEBHOOK → NotifyChannel.DINGTALK / FEISHU / WECOM（机器人）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowNotificationService {

  /**
   * 任务创建通知
   *
   * @param instanceId 流程实例 ID
   * @param taskId 任务 ID
   * @param assigneeId 办理人 ID
   * @param assigneeName 办理人姓名
   */
  void notifyTaskCreated(String instanceId, String taskId, String assigneeId, String assigneeName);

  /**
   * 催办通知
   *
   * @param instanceId 流程实例 ID
   * @param taskId 任务 ID
   * @param assigneeIds 被催办人 ID 列表
   * @param comment 催办备注
   */
  void notifyUrge(String instanceId, String taskId, List<String> assigneeIds, String comment);

  /**
   * 抄送通知
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 抄送节点编码
   * @param ccUserIds 抄送接收人 ID 列表
   * @param title 通知标题
   */
  void notifyCc(String instanceId, String nodeCode, List<Long> ccUserIds, String title);

  /**
   * 流程完成通知
   *
   * @param instanceId 流程实例 ID
   * @param initiatorId 发起人 ID
   */
  void notifyInstanceCompleted(String instanceId, String initiatorId);

  /**
   * 流程驳回通知
   *
   * @param instanceId 流程实例 ID
   * @param initiatorId 发起人 ID
   * @param reason 驳回原因
   */
  void notifyInstanceRejected(String instanceId, String initiatorId, String reason);

  /**
   * SLA 超时通知
   *
   * @param instanceId 流程实例 ID
   * @param taskId 任务 ID
   * @param assigneeId 办理人 ID
   * @param action 超时动作（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）
   */
  void notifySlaTimeout(String instanceId, String taskId, String assigneeId, String action);

  /**
   * 通用发送
   *
   * @param channel 通知通道：INAPP / EMAIL / WEBHOOK
   * @param userId 接收人 ID
   * @param title 通知标题
   * @param content 通知内容
   * @param extra 扩展参数（如跳转链接、业务类型等）
   */
  void send(String channel, String userId, String title, String content, Map<String, Object> extra);

  /**
   * P1-5: 带自动脱敏的便捷通知方法（原 FlowNotificationHelper 功能合并）
   *
   * <p>自动构建 category=WORKFLOW 的 extra Map，并对 title/content 做敏感信息脱敏后发送。
   *
   * @param channel 通知通道：INAPP / EMAIL / WEBHOOK
   * @param userId 接收人 ID
   * @param title 通知标题（将自动脱敏）
   * @param content 通知内容（将自动脱敏）
   * @param bizType 业务类型（如 WORKFLOW_TASK / WORKFLOW_URGE / WORKFLOW_COMPLETED 等）
   * @param level 级别 INFO / WARN / ERROR / URGENT
   */
  void notify(
      String channel, String userId, String title, String content, String bizType, String level);

  /**
   * P1-5: 带自动脱敏的批量通知方法
   *
   * @param channel 通知通道
   * @param receiverIds 接收人 ID 列表
   * @param title 通知标题（将自动脱敏）
   * @param content 通知内容（将自动脱敏）
   * @param bizType 业务类型
   * @param level 级别
   */
  void notifyBatch(
      String channel,
      List<String> receiverIds,
      String title,
      String content,
      String bizType,
      String level);
}
