package com.njydsz.message.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.core.NotifyRequest;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyPriority;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — 消息中心订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@link StandardEventTypes#JOB_EXECUTION_FAILED} — 定时任务执行失败时发送告警通知</li>
 *   <li>{@link StandardEventTypes#AGENT_APPROVAL_REQUESTED} — Agent 审批请求时发送通知</li>
 *   <li>{@link StandardEventTypes#FLOW_INSTANCE_APPROVED} — 流程审批通过时通知发起人</li>
 *   <li>{@link StandardEventTypes#FLOW_INSTANCE_REJECTED} — 流程审批驳回时通知发起人</li>
 *   <li>{@link StandardEventTypes#FLOW_INSTANCE_TERMINATED} — 流程终止时通知参与人</li>
 *   <li>{@link StandardEventTypes#PROJECT_INITIATION_APPROVED} — 项目立项审批通过通知</li>
 *   <li>{@link StandardEventTypes#PROJECT_CONTRACT_SIGNED} — 合同签订通知</li>
 *   <li>{@link StandardEventTypes#JOB_TIMEOUT} — 定时任务超时告警</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

    private final NotifyService notifyService;

    /**
     * 定时任务执行失败 — 发送告警通知
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'JOB_EXECUTION_FAILED'")
    public void onJobExecutionFailed(OutboxMessage message) {
        log.warn("[CrossModuleEventListener] 接收定时任务执行失败事件: aggregateId={}, payload={}",
                message.getAggregateId(), message.getPayload());
        try {
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, null,
                    "定时任务执行失败告警",
                    String.format("定时任务执行失败，请及时处理。任务ID: %s", message.getAggregateId()))
                    .priority(NotifyPriority.P0_CRITICAL)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送定时任务失败告警通知异常", e);
        }
    }

    /**
     * Agent 审批请求 — 发送通知
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'AGENT_APPROVAL_REQUESTED'")
    public void onAgentApprovalRequested(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收 Agent 审批请求事件: aggregateId={}",
                message.getAggregateId());
        try {
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, null,
                    "AI Agent 审批请求",
                    String.format("您有一个 AI Agent 审批请求待处理，请求ID: %s",
                            message.getAggregateId()))
                    .priority(NotifyPriority.P1_HIGH)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送 Agent 审批通知异常", e);
        }
    }

    /**
     * 流程审批通过 — 通知发起人
     *
     * <p>当工作流审批通过时，向流程发起人发送站内信/邮件通知。
     * payload 中包含流程标题、发起人 ID、审批人等信息。
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'FLOW_INSTANCE_APPROVED'")
    public void onFlowInstanceApproved(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收流程审批通过事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
            String initiatorId = payload.getOrDefault("initiatorId", "").toString();
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, initiatorId,
                    "审批通过通知：" + flowTitle,
                    String.format("您的审批申请「%s」已通过。流程实例ID: %s",
                            flowTitle, message.getAggregateId()))
                    .priority(NotifyPriority.P2_NORMAL)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送审批通过通知异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 流程审批驳回 — 通知发起人
     *
     * <p>当工作流审批被驳回时，向流程发起人发送站内信/邮件通知，
     * 附带驳回原因和驳回节点信息。
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'FLOW_INSTANCE_REJECTED'")
    public void onFlowInstanceRejected(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收流程审批驳回事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
            String initiatorId = payload.getOrDefault("initiatorId", "").toString();
            String rejectReason = payload.getOrDefault("rejectReason", "未提供原因").toString();
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, initiatorId,
                    "审批驳回通知：" + flowTitle,
                    String.format("您的审批申请「%s」已被驳回。驳回原因: %s。流程实例ID: %s",
                            flowTitle, rejectReason, message.getAggregateId()))
                    .priority(NotifyPriority.P1_HIGH)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送审批驳回通知异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 流程终止 — 通知参与人
     *
     * <p>当流程被管理员终止时，向所有相关参与人发送通知。
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'FLOW_INSTANCE_TERMINATED'")
    public void onFlowInstanceTerminated(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收流程终止事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String flowTitle = payload.getOrDefault("flowTitle", "未命名流程").toString();
            String reason = payload.getOrDefault("reason", "管理员终止").toString();
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, null,
                    "流程终止通知：" + flowTitle,
                    String.format("流程「%s」已被终止。终止原因: %s。流程实例ID: %s",
                            flowTitle, reason, message.getAggregateId()))
                    .priority(NotifyPriority.P1_HIGH)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送流程终止通知异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 项目立项审批通过 — 通知项目经理
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'PROJECT_INITIATION_APPROVED'")
    public void onProjectInitiationApproved(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收项目立项审批通过事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String projectName = payload.getOrDefault("projectName", "未命名项目").toString();
            String managerId = payload.getOrDefault("managerId", "").toString();
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, managerId,
                    "项目立项通过：" + projectName,
                    String.format("项目「%s」立项审批已通过，请尽快启动项目。项目编号: %s",
                            projectName, message.getAggregateId()))
                    .priority(NotifyPriority.P2_NORMAL)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送项目立项通知异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 合同签订 — 通知相关方
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'PROJECT_CONTRACT_SIGNED'")
    public void onProjectContractSigned(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收合同签订事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String contractName = payload.getOrDefault("contractName", "未命名合同").toString();
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, null,
                    "合同签订通知：" + contractName,
                    String.format("合同「%s」已签订。合同编号: %s",
                            contractName, message.getAggregateId()))
                    .priority(NotifyPriority.P2_NORMAL)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送合同签订通知异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 定时任务超时 — 发送告警通知
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'JOB_TIMEOUT'")
    public void onJobTimeout(OutboxMessage message) {
        log.warn("[CrossModuleEventListener] 接收定时任务超时事件: aggregateId={}",
                message.getAggregateId());
        try {
            NotifyRequest request = NotifyRequest.of(
                    NotifyChannel.INSITE, null,
                    "定时任务超时告警",
                    String.format("定时任务执行超时，请检查任务配置。任务ID: %s",
                            message.getAggregateId()))
                    .priority(NotifyPriority.P1_HIGH)
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送任务超时告警通知异常", e);
        }
    }
}
