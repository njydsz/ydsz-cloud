package com.njydsz.agent.server.event;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.event.AgentDomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;

/**
 * Agent 事件统一发布器
 *
 * <p>封装 Agent 执行生命周期事件的发布，确保事件类型、元数据结构、发布时机的一致性。 消费方可订阅 AGENT_EXECUTION_STARTED /
 * COMPLETED / FAILED 事件实现：用量审计、失败告警、运营分析。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentEventPublisher {

  /** 事件发布门面（可选依赖，未引入 common-event 时安全降级，符合云顶规范 27.4.1） */
  private final ObjectProvider<DomainEventPublisher> publisherProvider;

  public AgentEventPublisher(ObjectProvider<DomainEventPublisher> publisherProvider) {
    this.publisherProvider = publisherProvider;
  }

  private void publishSafely(AgentDomainEvent event) {
    DomainEventPublisher publisher = publisherProvider.getIfAvailable();
    if (publisher == null) {
      log.warn("[AgentEvent] common-event 未装配，事件丢弃: type={}", event.getEventType());
      return;
    }
    publisher.publish(event);
  }

  /**
   * 发布 Agent 执行启动事件。
   *
   * @param executionId 执行 ID
   * @param tenantId 租户 ID
   * @param userId 用户 ID
   * @param agentType Agent 类型（CHAT / REACT / RAG 等）
   * @param model 模型名称
   */
  public void publishExecutionStarted(
      String executionId, String tenantId, String userId, String agentType, String model) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("tenantId", tenantId);
    metadata.put("userId", userId);
    metadata.put("agentType", agentType);
    metadata.put("model", model);
    AgentDomainEvent event =
        AgentDomainEvent.of(DomainEventTypes.AGENT_EXECUTION_STARTED, executionId, metadata);
    publishSafely(event);
    log.debug("[AgentEvent] 执行启动: executionId={}, type={}", executionId, agentType);
  }

  /**
   * 发布 Agent 执行完成事件。
   *
   * @param executionId 执行 ID
   * @param tenantId 租户 ID
   * @param agentType Agent 类型
   * @param model 模型名称
   * @param durationMs 执行耗时（毫秒）
   * @param totalTokens 总 Token 数
   * @param costUsd 成本（USD）
   */
  public void publishExecutionCompleted(
      String executionId,
      String tenantId,
      String agentType,
      String model,
      long durationMs,
      int totalTokens,
      double costUsd) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("tenantId", tenantId);
    metadata.put("agentType", agentType);
    metadata.put("model", model);
    metadata.put("durationMs", String.valueOf(durationMs));
    metadata.put("totalTokens", String.valueOf(totalTokens));
    metadata.put("costUsd", String.valueOf(costUsd));
    AgentDomainEvent event =
        AgentDomainEvent.of(DomainEventTypes.AGENT_EXECUTION_COMPLETED, executionId, metadata);
    publishSafely(event);
    log.debug("[AgentEvent] 执行完成: executionId={}, tokens={}, cost={}", executionId, totalTokens, costUsd);
  }

  /**
   * 发布 Agent 执行失败事件。
   *
   * @param executionId 执行 ID
   * @param tenantId 租户 ID
   * @param agentType Agent 类型
   * @param model 模型名称
   * @param durationMs 执行耗时（毫秒）
   * @param errorMessage 错误描述
   */
  public void publishExecutionFailed(
      String executionId,
      String tenantId,
      String agentType,
      String model,
      long durationMs,
      String errorMessage) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("tenantId", tenantId);
    metadata.put("agentType", agentType);
    metadata.put("model", model);
    metadata.put("durationMs", String.valueOf(durationMs));
    metadata.put("errorMessage", errorMessage != null ? errorMessage : "未知错误");
    AgentDomainEvent event =
        AgentDomainEvent.of(DomainEventTypes.AGENT_EXECUTION_FAILED, executionId, metadata);
    publishSafely(event);
    log.warn("[AgentEvent] 执行失败: executionId={}, error={}", executionId, errorMessage);
  }

  /**
   * 发布对话创建事件（Conversation 聚合根事件）。
   *
   * @param conversationId 对话 ID
   * @param tenantId 租户 ID
   * @param model 模型名称
   * @param costUsd 成本（USD）
   */
  public void publishConversationCreated(
      String conversationId, String tenantId, String model, double costUsd) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("tenantId", tenantId);
    metadata.put("model", model);
    metadata.put("costUsd", String.valueOf(costUsd));
    AgentDomainEvent event =
        new AgentDomainEvent(
            DomainEventTypes.CONVERSATION_CREATED,
            conversationId,
            "CONVERSATION",
            metadata);
    publishSafely(event);
  }
}
