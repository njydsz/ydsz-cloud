package com.njydsz.agent.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.agent.server.rag.RagService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Agent 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code FILE_UPLOADED} — 文件上传完成时自动索引到 RAG 知识库</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

    private final RagService ragService;

    /**
     * 文件上传完成 — 自动索引到 RAG 知识库
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'FILE_UPLOADED'")
    public void onFileUploaded(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收文件上传事件: aggregateId={}, payload={}",
                message.getAggregateId(), message.getPayload());
        try {
            ragService.ingestByFileId(message.getAggregateId());
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 自动索引文件到 RAG 知识库异常: fileId={}",
                    message.getAggregateId(), e);
        }
    }
}
