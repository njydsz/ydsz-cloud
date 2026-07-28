package com.njydsz.workflow.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Workflow 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code PROJECT_INITIATION_CREATED} — 项目立项创建时可自动创建审批流程</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CrossModuleEventListener {

    /**
     * 项目立项创建 — 可自动创建审批流程
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'PROJECT_INITIATION_CREATED'")
    public void onProjectInitiationCreated(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收项目立项创建事件: projectId={}",
                message.getAggregateId());
    }
}
