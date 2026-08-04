package com.njydsz.literule.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Literule 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code PROJECT_STAGE_CHANGED} — 项目阶段变更时触发项目阶段规则评估</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CrossModuleEventListener {

    /**
     * 项目阶段变更 — 可触发项目阶段规则评估
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'PROJECT_STAGE_CHANGED'")
    public void onProjectStageChanged(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收项目阶段变更事件: projectId={}",
                message.getAggregateId());
    }
}
