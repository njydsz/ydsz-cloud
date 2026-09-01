package com.njydsz.agent.server.memory;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.event.AgentDomainEvent;

/**
 * 对话结束事件监听器 — 触发实时记忆整合。
 *
 * <p>当 Agent 执行完成后，异步触发记忆提取流程。
 * 不阻塞主执行流程，提取失败不影响用户体验。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
@Component
public class ConversationCompletedEventListener {

    private final ConversationMemoryConsolidationService consolidationService;

    public ConversationCompletedEventListener(
            ConversationMemoryConsolidationService consolidationService) {
        this.consolidationService = consolidationService;
    }

    /**
     * 监听 Agent 执行完成事件，触发记忆整合。
     *
     * <p>使用异步执行，不阻塞主流程。
     * 仅对消息数 >= 4 的对话执行提取（由 consolidationService 二次检查）。</p>
     *
     * @param event Agent 领域事件
     */
    @Async
    @EventListener(condition = "#event.eventType == T(com.njydsz.common.event.api.DomainEventTypes).AGENT_EXECUTION_COMPLETED")
    public void onExecutionCompleted(AgentDomainEvent event) {
        if (event == null) {
            return;
        }

        String executionId = event.getExecutionId();
        String tenantId = extractTenantId(event);

        if (executionId == null || tenantId == null) {
            return;
        }

        try {
            log.debug("对话结束，触发记忆整合: executionId={}, tenantId={}",
                    executionId, tenantId);
            int facts = consolidationService.consolidateConversation(executionId, tenantId);
            if (facts > 0) {
                log.info("实时记忆整合完成: executionId={}, facts={}", executionId, facts);
            }
        } catch (Exception e) {
            log.warn("实时记忆整合失败: executionId={}, error={}", executionId, e.getMessage());
        }
    }

    /**
     * 从事件元数据中提取租户 ID。
     *
     * @param event Agent 领域事件
     * @return 租户 ID，未找到返回 null
     */
    @SuppressWarnings("unchecked")
    private String extractTenantId(AgentDomainEvent event) {
        Map<String, Object> metadata = event.getMetadata();
        if (metadata != null && metadata.containsKey("tenantId")) {
            return String.valueOf(metadata.get("tenantId"));
        }
        return null;
    }
}
