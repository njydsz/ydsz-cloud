package com.njydsz.nextwiki.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Nextwiki 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code CONVERSATION_CREATED} — Agent 对话创建时可关联知识库文档</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CrossModuleEventListener {

    /**
     * Agent 对话创建 — 可关联知识库文档作为对话上下文
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'CONVERSATION_CREATED'")
    public void onConversationCreated(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收对话创建事件: conversationId={}",
                message.getAggregateId());
    }
}
