package com.njydsz.message.server.listener;


import com.njydsz.common.locales.util.I18n;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.consumer.OutboxIdempotentConsumer;
import com.njydsz.common.event.consumer.OutboxSubscriber;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.notify.helper.NotifyHelper;

/**
 * 跨模块事件订阅者 — 消息中心通过 OutboxSubscriber SPI 订阅其他模块的领域事件并发送通知。
 *
 * <p>替代原 {@code CrossModuleEventListener}（@EventListener 手动过滤），统一由 OutboxSubscriberDispatcher 路由分发。
 *
 * <p>当前订阅事件类型：
 *
 * <ul>
 *   <li>{@link DomainEventTypes#JOB_EXECUTION_FAILED} — 定时任务执行失败时发送告警通知
 *   <li>{@link DomainEventTypes#JOB_TIMEOUT} — 定时任务超时告警
 *   <li>{@link DomainEventTypes#AGENT_APPROVAL_REQUESTED} — Agent 审批请求时发送通知
 *   <li>{@link DomainEventTypes#FLOW_INSTANCE_APPROVED} — 流程审批通过时通知发起人
 *   <li>{@link DomainEventTypes#FLOW_INSTANCE_REJECTED} — 流程审批驳回时通知发起人
 *   <li>{@link DomainEventTypes#FLOW_INSTANCE_TERMINATED} — 流程终止时通知参与人
 *   <li>{@link DomainEventTypes#PROJECT_INITIATION_APPROVED} — 项目立项审批通过通知
 *   <li>{@link DomainEventTypes#PROJECT_CONTRACT_SIGNED} — 合同签订通知
 * </ul>
 *
 * <p><b>收敛说明</b>：使用 {@link NotifyHelper} 替代直接调用 NotifyService，符合 ADR-001 统一业务入口策略。
 * 使用通配符主题（topic="*"）接收跨模块事件，通过 eventType 字段过滤内部路由。
 *
 * <p><b>i18n 合规</b>：所有通知文案通过 {@link I18nMessages} 解析，符合 YDIZ-COMMON-O40 禁止硬编码中文的规定。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventSubscriber implements OutboxSubscriber {

  private final NotifyHelper notifyHelper;
  private final I18nMessages i18n;

  /**
   * 使用通配符主题接收所有跨模块事件。
   *
   * <p>跨模块事件来自 workflow / cronjob / agent / project 等多个模块，无法按固定 topic 路由。 使用通配符在
   * {@link #onMessage(OutboxMessage)} 内部按 eventType 字段进一步过滤。
   *
   * @return "*" 通配符
   */
  @Override
  public String getTopic() {
    return "*";
  }

  /**
   * 处理跨模块事件：根据 eventType 路由到对应的通知逻辑。
   *
   * @param message Outbox 消息
   */
  @Async
  @Override
  @OutboxIdempotentConsumer(idempotencyKey = "#message.id", expireSeconds = 3600)
  public void onMessage(OutboxMessage message) {
    if (message == null || message.getEventType() == null) {
      return;
    }
    String eventType = message.getEventType();
    // 仅处理本模块关注的跨模块事件类型
    switch (eventType) {
      case DomainEventTypes.JOB_EXECUTION_FAILED -> onJobExecutionFailed(message);
      case DomainEventTypes.JOB_TIMEOUT -> onJobTimeout(message);
      case DomainEventTypes.AGENT_APPROVAL_REQUESTED -> onAgentApprovalRequested(message);
      case DomainEventTypes.FLOW_INSTANCE_APPROVED -> onFlowInstanceApproved(message);
      case DomainEventTypes.FLOW_INSTANCE_REJECTED -> onFlowInstanceRejected(message);
      case DomainEventTypes.FLOW_INSTANCE_TERMINATED -> onFlowInstanceTerminated(message);
      case DomainEventTypes.PROJECT_INITIATION_APPROVED -> onProjectInitiationApproved(message);
      case DomainEventTypes.PROJECT_CONTRACT_SIGNED -> onProjectContractSigned(message);
      default -> {
        // 不关心的事件类型，忽略
      }
    }
  }

  /**
   * 定时任务执行失败 — 发送告警通知。
   *
   * @param message Outbox 消息
   */
  private void onJobExecutionFailed(OutboxMessage message) {
    log.warn(I18n.message("message.log.server.crosmodulesub.warn.job.execution.failed"),
        message.getAggregateId(), message.getPayload());
    String title = i18n.resolve("message.notify.job.execution.failed.title");
    String content = i18n.resolve("message.notify.job.execution.failed.content",
        new Object[] {message.getAggregateId()});
    notifyHelper.sendSystemAlert(title, content);
  }

  /**
   * Agent 审批请求 — 发送通知。
   *
   * @param message Outbox 消息
   */
  private void onAgentApprovalRequested(OutboxMessage message) {
    log.info(I18n.message("message.log.server.crosmodulesub.info.agent.approval"),
        message.getAggregateId());
    String title = i18n.resolve("message.notify.agent.approval.requested.title");
    String content = i18n.resolve("message.notify.agent.approval.requested.content",
        new Object[] {message.getAggregateId()});
    notifyHelper.sendSystemAlert(title, content);
  }

  /**
   * 流程审批通过 — 通知发起人。
   *
   * @param message Outbox 消息
   */
  private void onFlowInstanceApproved(OutboxMessage message) {
    log.info(I18n.message("message.log.server.crosmodulesub.info.flow.approved"), message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
    String initiatorId = payload.getOrDefault("initiatorId", "").toString();
    if (initiatorId.isBlank()) {
      log.debug(I18n.message("message.log.server.crosmodulesub.debug.flow.approved.no.initiator.skipped"),
          message.getAggregateId());
      return;
    }
    String title = i18n.resolve("message.notify.flow.instance.approved.title",
        new Object[] {flowTitle});
    String content = i18n.resolve("message.notify.flow.instance.approved.content",
        new Object[] {flowTitle, message.getAggregateId()});
    notifyHelper.sendInApp(initiatorId, title, content);
  }

  /**
   * 流程审批驳回 — 通知发起人。
   *
   * @param message Outbox 消息
   */
  private void onFlowInstanceRejected(OutboxMessage message) {
    log.info(I18n.message("message.log.server.crosmodulesub.info.flow.rejected"), message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
    String initiatorId = payload.getOrDefault("initiatorId", "").toString();
    String rejectReason = payload.getOrDefault("rejectReason", "未提供原因").toString();
    if (initiatorId.isBlank()) {
      log.debug(I18n.message("message.log.server.crosmodulesub.debug.flow.rejected.no.initiator.skipped"),
          message.getAggregateId());
      return;
    }
    String title = i18n.resolve("message.notify.flow.instance.rejected.title",
        new Object[] {flowTitle});
    String content = i18n.resolve("message.notify.flow.instance.rejected.content",
        new Object[] {flowTitle, rejectReason, message.getAggregateId()});
    notifyHelper.sendInApp(initiatorId, title, content);
  }

  /**
   * 流程终止 — 通知参与人。
   *
   * @param message Outbox 消息
   */
  private void onFlowInstanceTerminated(OutboxMessage message) {
    log.info(I18n.message("message.log.server.crosmodulesub.info.flow.terminated"), message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
    String reason = payload.getOrDefault("reason", "管理员终止").toString();
    String title = i18n.resolve("message.notify.flow.instance.terminated.title",
        new Object[] {flowTitle});
    String content = i18n.resolve("message.notify.flow.instance.terminated.content",
        new Object[] {flowTitle, reason, message.getAggregateId()});
    notifyHelper.sendSystemAlert(title, content);
  }

  /**
   * 项目立项审批通过 — 通知项目经理。
   *
   * @param message Outbox 消息
   */
  private void onProjectInitiationApproved(OutboxMessage message) {
    log.info(I18n.message("message.log.server.crosmodulesub.info.project.initiation.approved"),
        message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String projectName = payload.getOrDefault("projectName", "未命名项目").toString();
    String managerId = payload.getOrDefault("managerId", "").toString();
    if (managerId.isBlank()) {
      log.debug(I18n.message("message.log.server.crosmodulesub.debug.project.no.manager.skipped"),
          message.getAggregateId());
      return;
    }
    String title = i18n.resolve("message.notify.project.initiation.approved.title",
        new Object[] {projectName});
    String content = i18n.resolve("message.notify.project.initiation.approved.content",
        new Object[] {projectName, message.getAggregateId()});
    notifyHelper.sendInApp(managerId, title, content);
  }

  /**
   * 合同签订 — 通知相关方。
   *
   * @param message Outbox 消息
   */
  private void onProjectContractSigned(OutboxMessage message) {
    log.info(I18n.message("message.log.server.crosmodulesub.info.contract.signed"), message.getAggregateId());
    var payload = YdszJson.parseMap(message.getPayload());
    String contractName = payload.getOrDefault("contractName", "未命名合同").toString();
    String title = i18n.resolve("message.notify.project.contract.signed.title",
        new Object[] {contractName});
    String content = i18n.resolve("message.notify.project.contract.signed.content",
        new Object[] {contractName, message.getAggregateId()});
    notifyHelper.sendSystemAlert(title, content);
  }

  /**
   * 定时任务超时 — 发送告警通知。
   *
   * @param message Outbox 消息
   */
  private void onJobTimeout(OutboxMessage message) {
    log.warn(I18n.message("message.log.server.crosmodulesub.warn.job.timeout"),
        message.getAggregateId());
    String title = i18n.resolve("message.notify.job.timeout.title");
    String content = i18n.resolve("message.notify.job.timeout.content",
        new Object[] {message.getAggregateId()});
    notifyHelper.sendSystemAlert(title, content);
  }
}
