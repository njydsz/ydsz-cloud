package com.njydsz.agent.server.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.agent.domain.trigger.AgentTrigger;
import com.njydsz.agent.domain.trigger.TriggerType;
import com.njydsz.agent.server.trigger.TriggerExecutionService.AgentExecutionDelegate;
import com.njydsz.agent.server.trigger.TriggerExecutionService.AgentExecutionRequest;
import com.njydsz.agent.server.trigger.TriggerExecutionService.TriggerExecutionException;
import com.njydsz.common.locales.util.I18nMessages;

/**
 * TriggerExecutionService 单元测试
 *
 * <p>验证触发器执行逻辑：执行请求构建、正常委托调用、异常封装与传递。
 */
@ExtendWith(MockitoExtension.class)
class TriggerExecutionServiceTest {

  private static final String TRIGGER_ID = "trigger-001";
  private static final String TENANT_ID = "tenant-001";
  private static final String AGENT_CODE = "data-analyst";
  private static final String AGENT_TYPE = "REACT";

  @Mock
  private AgentExecutionDelegate executionDelegate;

  @Mock
  private I18nMessages i18nMessages;

  /**
   * 构造一个最小化的 AgentTrigger 用于测试。
   */
  private AgentTrigger buildTrigger() {
    return AgentTrigger.builder()
        .triggerId(TRIGGER_ID)
        .tenantId(TENANT_ID)
        .name("test-trigger")
        .description("test trigger for unit test")
        .triggerType(TriggerType.CRON)
        .targetAgentCode(AGENT_CODE)
        .targetAgentType(AGENT_TYPE)
        .cronExpression("0 0 * * *")
        .isEnabled(true)
        .createdAt(LocalDateTime.now())
        .totalTriggerCount(0)
        .build();
  }

  @Test
  @DisplayName("正常执行 - 将触发器信息封装为 AgentExecutionRequest 后调用 delegate.execute")
  void should_delegateToExecutionService_when_triggerExecutes() {
    // Arrange
    AgentTrigger trigger = buildTrigger();
    Map<String, Object> context = Map.of("event", "data");

    TriggerExecutionService service =
        new TriggerExecutionService(executionDelegate, i18nMessages);

    ArgumentCaptor<AgentExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(AgentExecutionRequest.class);

    // Act
    service.executeTrigger(trigger, context);

    // Assert
    verify(executionDelegate).execute(requestCaptor.capture());
    AgentExecutionRequest request = requestCaptor.getValue();

    assertThat(request.executionId()).startsWith("trigger-");
    assertThat(request.tenantId()).isEqualTo(TENANT_ID);
    assertThat(request.agentCode()).isEqualTo(AGENT_CODE);
    assertThat(request.agentType()).isEqualTo(AGENT_TYPE);
    assertThat(request.metadata()).containsEntry("triggerId", TRIGGER_ID);
    assertThat(request.metadata()).containsEntry("triggerName", "test-trigger");
    assertThat(request.metadata()).containsEntry("triggerType", "CRON");
    assertThat(request.metadata()).containsEntry("event", "data");
  }

  @Test
  @DisplayName("生成 executionId - 以 trigger- 前缀加唯一 ID")
  void should_generateExecutionIdWithTriggerPrefix() {
    // Arrange
    AgentTrigger trigger = buildTrigger();

    TriggerExecutionService service =
        new TriggerExecutionService(executionDelegate, i18nMessages);

    ArgumentCaptor<AgentExecutionRequest> captor =
        ArgumentCaptor.forClass(AgentExecutionRequest.class);

    // Act
    service.executeTrigger(trigger, Map.of());

    // Assert
    verify(executionDelegate).execute(captor.capture());
    assertThat(captor.getValue().executionId()).startsWith("trigger-");
    assertThat(captor.getValue().executionId().length()).isGreaterThan("trigger-".length());
  }

  @Test
  @DisplayName("delegate 抛出异常 - 包装为 TriggerExecutionException 并保留原始异常")
  void should_wrapExceptionAsTriggerExecutionException_whenDelegateFails() {
    // Arrange
    AgentTrigger trigger = buildTrigger();
    RuntimeException cause = new RuntimeException("LLM service unavailable");
    when(i18nMessages.resolve(anyString(), any())).thenReturn("Trigger execution failed");

    TriggerExecutionService service =
        new TriggerExecutionService(executionDelegate, i18nMessages);

    doThrow(cause).when(executionDelegate).execute(any(AgentExecutionRequest.class));

    // Act & Assert
    assertThatThrownBy(() -> service.executeTrigger(trigger, Map.of()))
        .isInstanceOf(TriggerExecutionException.class)
        .hasCause(cause);
  }

  @Test
  @DisplayName("构造函数参数校验 - executionDelegate 为 null 时抛出 NullPointerException")
  void should_throwNpe_when_executionDelegateIsNull() {
    // Act & Assert
    assertThatThrownBy(() -> new TriggerExecutionService(null, i18nMessages))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("executionDelegate");
  }
}
