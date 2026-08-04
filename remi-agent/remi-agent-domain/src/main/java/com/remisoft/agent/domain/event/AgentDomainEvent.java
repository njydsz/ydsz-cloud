package com.remisoft.agent.domain.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.remisoft.common.event.api.DomainEvent;
import com.remisoft.common.event.api.ModuleEventTypes;

import lombok.Getter;

/**
 * Agent 域领域事件。
 *
 * <p>封装 Agent 执行生命周期事件，继承 {@link DomainEvent}，
 * 事件类型常量统一取自 {@link ModuleEventTypes}（AGENT_EXECUTION_STARTED /
 * AGENT_EXECUTION_COMPLETED / AGENT_EXECUTION_FAILED）。
 *
 * <p><b>发布方式：</b>
 * <pre>{@code
 * applicationEventPublisher.publishEvent(
 *     AgentDomainEvent.of(ModuleEventTypes.AGENT_EXECUTION_STARTED, executionId,
 *         Map.of("agentCode", "chat-assistant")));
 * }</pre>
 *
 * <p><b>消费方式（推荐事务提交后）：</b>
 * <pre>{@code
 * &#64;TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onAgentExecutionCompleted(AgentDomainEvent event) { ... }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Getter
public class AgentDomainEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 Agent 域事件。
     *
     * @param eventType    事件类型（取自 {@link ModuleEventTypes}）
     * @param executionId  执行 ID（映射为 aggregateId）
     * @param aggregateType 聚合根类型（AGENT / AGENT_EXECUTION）
     * @param metadata     扩展元数据
     */
    public AgentDomainEvent(String eventType, String executionId, String aggregateType,
                            Map<String, Object> metadata) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(), eventType,
                executionId, aggregateType,
                metadata != null ? metadata : Collections.emptyMap());
    }

    /**
     * 便捷工厂：创建 Agent 域事件。
     *
     * @param eventType   事件类型（取自 {@link ModuleEventTypes}）
     * @param executionId 执行 ID
     * @param metadata    扩展元数据
     * @return Agent 域事件实例
     */
    public static AgentDomainEvent of(String eventType, String executionId, Map<String, Object> metadata) {
        return new AgentDomainEvent(eventType, executionId, "AGENT_EXECUTION", metadata);
    }

    /**
     * 获取执行 ID（即 aggregateId，语义别名）。
     *
     * @return 执行 ID
     */
    public String getExecutionId() {
        return getAggregateId();
    }
}
