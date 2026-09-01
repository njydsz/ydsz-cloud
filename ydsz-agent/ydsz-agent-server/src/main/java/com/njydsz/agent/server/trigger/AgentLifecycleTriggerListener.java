package com.njydsz.agent.server.trigger;

import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.event.AgentDomainEvent;

/**
 * Agent 生命周期事件触发器监听器。
 *
 * <p>监听 Agent 执行完成/失败事件，评估并执行匹配的 AGENT_LIFECYCLE 类型触发器。
 * 实现事件驱动的 Agent 链式编排。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class AgentLifecycleTriggerListener {

    private final TriggerEvaluationService evaluationService;

    private static final String METADATA_TENANT_ID = "tenantId";
    private static final String METADATA_AGENT_TYPE = "agentType";
    private static final String METADATA_MODEL = "model";
    private static final String METADATA_DURATION_MS = "durationMs";
    private static final String METADATA_ERROR_MESSAGE = "errorMessage";

    public AgentLifecycleTriggerListener(TriggerEvaluationService evaluationService) {
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService 不能为 null");
    }

    /**
     * 处理 Agent 执行完成事件。
     *
     * @param event 领域事件
     */
    public void onAgentExecutionCompleted(AgentDomainEvent event) {
        log.debug("[TriggerListener] 收到 Agent 执行完成事件: executionId={}", event.getExecutionId());
        evaluateAgentLifecycleEvent(event, "AGENT_EXECUTION_COMPLETED");
    }

    /**
     * 处理 Agent 执行失败事件。
     *
     * @param event 领域事件
     */
    public void onAgentExecutionFailed(AgentDomainEvent event) {
        log.debug("[TriggerListener] 收到 Agent 执行失败事件: executionId={}", event.getExecutionId());
        evaluateAgentLifecycleEvent(event, "AGENT_EXECUTION_FAILED");
    }

    /**
     * 评估 Agent 生命周期事件。
     *
     * @param event     领域事件
     * @param eventType 事件类型标识
     */
    private void evaluateAgentLifecycleEvent(AgentDomainEvent event, String eventType) {
        Map<String, Object> metadata = event.getMetadata();
        String tenantId = getStringFromMetadata(metadata, METADATA_TENANT_ID);
        if (tenantId == null) {
            log.warn("[TriggerListener] 事件中缺少 tenantId，跳过触发器评估");
            return;
        }

        try {
            evaluationService.evaluateAgentLifecycleTriggers(
                    tenantId,
                    eventType,
                    event.getExecutionId(),
                    metadata
            );
        } catch (Exception e) {
            log.error("[TriggerListener] 触发器评估异常: eventType={}, error={}",
                    eventType, e.getMessage(), e);
        }
    }

    /**
     * 从元数据中安全获取字符串值。
     *
     * @param metadata 元数据映射
     * @param key      键
     * @return 字符串值，不存在时返回 null
     */
    private String getStringFromMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }
}
