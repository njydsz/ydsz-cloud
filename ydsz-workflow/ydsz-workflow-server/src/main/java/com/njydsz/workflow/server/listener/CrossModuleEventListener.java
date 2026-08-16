package com.njydsz.workflow.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.server.service.FlowTaskTransferService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Workflow 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@link DomainEventTypes#PROJECT_INITIATION_CREATED} — 项目立项创建时自动创建审批流程</li>
 *   <li>{@link DomainEventTypes#USER_DISABLED} — 用户禁用时转交待办任务</li>
 *   <li>{@link DomainEventTypes#ORG_STRUCTURE_CHANGED} — 组织架构变更时批量调整审批人</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

    private final FlowTaskTransferService flowTaskTransferService;

    /**
     * 项目立项创建 — 自动创建审批流程
     *
     * <p>当 project 模块发布项目立项创建事件后，workflow 模块自动
     * 为该项目创建对应的审批流程实例。
     *
     * @param message Outbox 消息，payload 包含项目编号、项目名称、项目经理等
     */
    @Async
    @EventListener(condition = "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).PROJECT_INITIATION_CREATED")
    public void onProjectInitiationCreated(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收项目立项创建事件: projectId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String projectId = message.getAggregateId();
            String projectName = payload.getOrDefault("projectName", "").toString();
            String managerId = payload.getOrDefault("managerId", "").toString();
            flowTaskTransferService.createInitiationApprovalFlow(projectId, projectName, managerId);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 处理项目立项创建事件异常: projectId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 用户禁用 — 转交该用户名下的待办任务
     *
     * <p>当 userinfo 模块发布用户禁用事件后，workflow 模块将该用户
     * 名下的所有待办任务转交给其上级或指定代理人。
     *
     * @param message Outbox 消息，payload 包含 userId、deptId 等
     */
    @Async
    @EventListener(condition = "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).USER_DISABLED")
    public void onUserDisabled(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收用户禁用事件: userId={}",
                message.getAggregateId());
        try {
            String userId = message.getAggregateId();
            var payload = YdszJson.parseMap(message.getPayload());
            String transferToUserId = payload.getOrDefault("transferToUserId", "").toString();
            flowTaskTransferService.transferTasksByUserDisable(userId, transferToUserId);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 处理用户禁用事件异常: userId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 组织架构变更 — 批量调整审批人
     *
     * <p>当 userinfo 模块发布组织架构变更事件后，workflow 模块
     * 批量调整涉及部门下的审批人配置。
     *
     * @param message Outbox 消息，payload 包含 deptId、changeType 等
     */
    @Async
    @EventListener(condition = "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).ORG_STRUCTURE_CHANGED")
    public void onOrgStructureChanged(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收组织架构变更事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String deptId = payload.getOrDefault("deptId", "").toString();
            String changeType = payload.getOrDefault("changeType", "").toString();
            flowTaskTransferService.adjustApproversByOrgChange(deptId, changeType);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 处理组织架构变更事件异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }
}
