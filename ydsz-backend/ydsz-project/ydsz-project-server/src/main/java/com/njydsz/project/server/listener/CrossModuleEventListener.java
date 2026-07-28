package com.njydsz.project.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.json.YdszJson;
import com.njydsz.project.server.service.ProjectStatusSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Project 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@link StandardEventTypes#FLOW_INSTANCE_APPROVED} — 审批通过后更新项目/合同状态</li>
 *   <li>{@link StandardEventTypes#FLOW_INSTANCE_REJECTED} — 审批驳回后回滚项目状态</li>
 *   <li>{@link StandardEventTypes#USER_LOGIN} — 用户登录时预热项目缓存</li>
 *   <li>{@link StandardEventTypes#CONFIG_CHANGED} — 系统配置变更时刷新项目参数缓存</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

    private final ProjectStatusSyncService projectStatusSyncService;

    /**
     * 流程审批通过 — 更新项目/合同状态
     *
     * <p>当工作流审批通过时，根据业务类型（立项/变更/结项）更新对应的项目状态。
     * payload 中包含 businessType（INITIATION/CHANGE/CLOSEOUT）、businessId 等。
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
            String businessType = payload.getOrDefault("businessType", "").toString();
            String businessId = payload.getOrDefault("businessId", "").toString();
            projectStatusSyncService.onFlowApproved(businessType, businessId);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 处理流程审批通过事件异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 流程审批驳回 — 回滚项目状态
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
            String businessType = payload.getOrDefault("businessType", "").toString();
            String businessId = payload.getOrDefault("businessId", "").toString();
            projectStatusSyncService.onFlowRejected(businessType, businessId);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 处理流程审批驳回事件异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 用户登录 — 预热项目经理的项目缓存
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'USER_LOGIN'")
    public void onUserLogin(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收用户登录事件: aggregateId={}",
                message.getAggregateId());
        try {
            String userId = message.getAggregateId();
            projectStatusSyncService.preheatProjectCache(userId);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 用户登录事件处理异常: userId={}",
                    message.getAggregateId(), e);
        }
    }

    /**
     * 系统配置变更 — 刷新项目参数缓存
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'CONFIG_CHANGED'")
    public void onConfigChanged(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收系统配置变更事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String configKey = payload.getOrDefault("configKey", "").toString();
            projectStatusSyncService.refreshConfigCache(configKey);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 配置变更事件处理异常: aggregateId={}",
                    message.getAggregateId(), e);
        }
    }
}
