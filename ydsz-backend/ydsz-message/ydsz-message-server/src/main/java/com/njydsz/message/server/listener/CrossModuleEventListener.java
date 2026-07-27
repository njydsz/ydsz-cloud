package com.njydsz.message.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.model.NotifyRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — 消息中心订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@link StandardEventTypes#JOB_EXECUTION_FAILED} — 定时任务执行失败时发送告警通知</li>
 *   <li>{@link StandardEventTypes#AGENT_APPROVAL_REQUESTED} — Agent 审批请求时发送通知</li>
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
            NotifyRequest request = NotifyRequest.builder()
                    .subject("定时任务执行失败告警")
                    .content(String.format("定时任务执行失败，请及时处理。任务ID: %s", message.getAggregateId()))
                    .priority("P0_CRITICAL")
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
            NotifyRequest request = NotifyRequest.builder()
                    .subject("AI Agent 审批请求")
                    .content(String.format("您有一个 AI Agent 审批请求待处理，请求ID: %s",
                            message.getAggregateId()))
                    .priority("P1_HIGH")
                    .build();
            notifyService.send(request);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 发送 Agent 审批通知异常", e);
        }
    }
}
