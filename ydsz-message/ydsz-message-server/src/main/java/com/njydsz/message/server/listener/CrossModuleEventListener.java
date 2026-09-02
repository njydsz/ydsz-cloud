package com.njydsz.message.server.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.helper.NotifyHelper;

/**
 * 跨模块事件监听器 — 消息中心订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 *
 * <ul>
 *   <li>{@link DomainEventTypes#JOB_EXECUTION_FAILED} — 定时任务执行失败时发送告警通知
 *   <li>{@link DomainEventTypes#AGENT_APPROVAL_REQUESTED} — Agent 审批请求时发送通知
 *   <li>{@link DomainEventTypes#FLOW_INSTANCE_APPROVED} — 流程审批通过时通知发起人
 *   <li>{@link DomainEventTypes#FLOW_INSTANCE_REJECTED} — 流程审批驳回时通知发起人
 *   <li>{@link DomainEventTypes#FLOW_INSTANCE_TERMINATED} — 流程终止时通知参与人
 *   <li>{@link DomainEventTypes#PROJECT_INITIATION_APPROVED} — 项目立项审批通过通知
 *   <li>{@link DomainEventTypes#PROJECT_CONTRACT_SIGNED} — 合同签订通知
 *   <li>{@link DomainEventTypes#JOB_TIMEOUT} — 定时任务超时告警
 * </ul>
 *
 * <p><b>收敛说明</b>：使用 {@link NotifyHelper} 替代直接调用 {@code NotifyService}， 符合 ADR-001 统一业务入口策略。
 * 对于无明确接收人的事件（receiver=null），不再发送无效通知。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

  private final NotifyHelper notifyHelper;

  /**
   * 定时任务执行失败 — 发送告警通知
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).JOB_EXECUTION_FAILED")
  public void onJobExecutionFailed(OutboxMessage message) {
    log.warn(
        "[CrossModuleEventListener] 接收定时任务执行失败事件: aggregateId={}, payload={}",
        message.getAggregateId(),
        message.getPayload());
    // 无特定接收人：发送系统告警（自动路由到运维渠道）
    notifyHelper.sendSystemAlert(
        "定时任务执行失败告警", String.format("定时任务执行失败，请及时处理。任务ID: %s", message.getAggregateId()));
  }

  /**
   * Agent 审批请求 — 发送通知
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).AGENT_APPROVAL_REQUESTED")
  public void onAgentApprovalRequested(OutboxMessage message) {
    log.info(
        "[CrossModuleEventListener] 接收 Agent 审批请求事件: aggregateId={}", message.getAggregateId());
    // 无特定接收人：发送系统告警
    notifyHelper.sendSystemAlert(
        "AI Agent 审批请求", String.format("您有一个 AI Agent 审批请求待处理，请求ID: %s", message.getAggregateId()));
  }

  /**
   * 流程审批通过 — 通知发起人
   *
   * <p>当工作流审批通过时，向流程发起人发送站内信/邮件通知。 payload 中包含流程标题、发起人 ID、审批人等信息。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).FLOW_INSTANCE_APPROVED")
  public void onFlowInstanceApproved(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收流程审批通过事件: aggregateId={}", message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
    String initiatorId = payload.getOrDefault("initiatorId", "").toString();
    if (initiatorId.isBlank()) {
      log.debug(
          "[CrossModuleEventListener] 审批通过事件无发起人，跳过通知: aggregateId={}", message.getAggregateId());
      return;
    }
    notifyHelper.sendInApp(
        initiatorId,
        "审批通过通知：" + flowTitle,
        String.format("您的审批申请「%s」已通过。流程实例ID: %s", flowTitle, message.getAggregateId()));
  }

  /**
   * 流程审批驳回 — 通知发起人
   *
   * <p>当工作流审批被驳回时，向流程发起人发送站内信/邮件通知， 附带驳回原因和驳回节点信息。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).FLOW_INSTANCE_REJECTED")
  public void onFlowInstanceRejected(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收流程审批驳回事件: aggregateId={}", message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
    String initiatorId = payload.getOrDefault("initiatorId", "").toString();
    String rejectReason = payload.getOrDefault("rejectReason", "未提供原因").toString();
    if (initiatorId.isBlank()) {
      log.debug(
          "[CrossModuleEventListener] 审批驳回事件无发起人，跳过通知: aggregateId={}", message.getAggregateId());
      return;
    }
    notifyHelper.sendInApp(
        initiatorId,
        "审批驳回通知：" + flowTitle,
        String.format(
            "您的审批申请「%s」已被驳回。驳回原因: %s。流程实例ID: %s",
            flowTitle, rejectReason, message.getAggregateId()));
  }

  /**
   * 流程终止 — 通知参与人
   *
   * <p>当流程被管理员终止时，向所有相关参与人发送通知。
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).FLOW_INSTANCE_TERMINATED")
  public void onFlowInstanceTerminated(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收流程终止事件: aggregateId={}", message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
    String reason = payload.getOrDefault("reason", "管理员终止").toString();
    // 无特定接收人：发送系统告警
    notifyHelper.sendSystemAlert(
        "流程终止通知：" + flowTitle,
        String.format(
            "流程「%s」已被终止。终止原因: %s。流程实例ID: %s", flowTitle, reason, message.getAggregateId()));
  }

  /**
   * 项目立项审批通过 — 通知项目经理
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).PROJECT_INITIATION_APPROVED")
  public void onProjectInitiationApproved(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收项目立项审批通过事件: aggregateId={}", message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String projectName = payload.getOrDefault("projectName", "未命名项目").toString();
    String managerId = payload.getOrDefault("managerId", "").toString();
    if (managerId.isBlank()) {
      log.debug(
          "[CrossModuleEventListener] 项目立项事件无项目经理，跳过通知: aggregateId={}", message.getAggregateId());
      return;
    }
    notifyHelper.sendInApp(
        managerId,
        "项目立项通过：" + projectName,
        String.format("项目「%s」立项审批已通过，请尽快启动项目。项目编号: %s", projectName, message.getAggregateId()));
  }

  /**
   * 合同签订 — 通知相关方
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).PROJECT_CONTRACT_SIGNED")
  public void onProjectContractSigned(OutboxMessage message) {
    log.info("[CrossModuleEventListener] 接收合同签订事件: aggregateId={}", message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String contractName = payload.getOrDefault("contractName", "未命名合同").toString();
    // 无特定接收人：发送系统告警
    notifyHelper.sendSystemAlert(
        "合同签订通知：" + contractName,
        String.format("合同「%s」已签订。合同编号: %s", contractName, message.getAggregateId()));
  }

  /**
   * 定时任务超时 — 发送告警通知
   *
   * @param message Outbox 消息
   */
  @Async
  @EventListener(
      condition =
          "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).JOB_TIMEOUT")
  public void onJobTimeout(OutboxMessage message) {
    log.warn("[CrossModuleEventListener] 接收定时任务超时事件: aggregateId={}", message.getAggregateId());
    // 无特定接收人：发送系统告警
    notifyHelper.sendSystemAlert(
        "定时任务超时告警", String.format("定时任务执行超时，请检查任务配置。任务ID: %s", message.getAggregateId()));
  }
}
