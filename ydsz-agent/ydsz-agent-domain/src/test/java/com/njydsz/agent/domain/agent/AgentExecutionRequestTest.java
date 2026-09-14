package com.njydsz.agent.domain.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.agent.domain.model.ChatMessage;

/**
 * {@link AgentExecutionRequest} 单元测试。
 *
 * <p>覆盖不可变构造、子代理安全继承、预置上下文注入等核心语义。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class AgentExecutionRequestTest {

  /** 父级工具白名单样本 */
  private static final List<String> PARENT_TOOLS = List.of("search", "calc", "sendEmail");

  /** 子级工具白名单样本 */
  private static final List<String> CHILD_TOOLS = List.of("calc", "sendEmail", "deleteFile");

  /**
   * 子代理请求应继承父级系统提示词、迭代上限与变量。
   */
  @Test
  @DisplayName("deriveForSubAgent 应继承父级非工具参数")
  void deriveForSubAgentShouldInheritNonToolParameters() {
    AgentExecutionRequest parent =
        AgentExecutionRequest.builder()
            .agentCode("parent-agent")
            .userInput("parent task")
            .systemPrompt("system prompt")
            .variables(Map.of("k1", "v1"))
            .maxIterations(5)
            .enabledTools(PARENT_TOOLS)
            .build();

    AgentExecutionRequest child =
        parent.deriveForSubAgent("sub task", "sub-conv", CHILD_TOOLS);

    assertEquals("parent-agent", child.getAgentCode());
    assertEquals("sub task", child.getUserInput());
    assertEquals("sub-conv", child.getConversationId());
    assertEquals("system prompt", child.getSystemPrompt());
    assertEquals(Map.of("k1", "v1"), child.getVariables());
    assertEquals(5, child.getMaxIterations());
  }

  /**
   * 父级白名单非空、子级白名单非空时，结果应为交集。
   */
  @Test
  @DisplayName("deriveForSubAgent 应取父子工具白名单交集")
  void deriveForSubAgentShouldIntersectToolLists() {
    AgentExecutionRequest parent =
        AgentExecutionRequest.builder()
            .userInput("parent task")
            .enabledTools(PARENT_TOOLS)
            .build();

    AgentExecutionRequest child =
        parent.deriveForSubAgent("sub task", "sub-conv", CHILD_TOOLS);

    assertEquals(List.of("calc", "sendEmail"), child.getEnabledTools());
  }

  /**
   * 父级未限制（空）时，子级直接使用子级声明的工具。
   */
  @Test
  @DisplayName("deriveForSubAgent 父级无限制时应沿用子级白名单")
  void deriveForSubAgentShouldUseChildToolsWhenParentUnlimited() {
    AgentExecutionRequest parent =
        AgentExecutionRequest.builder()
            .userInput("parent task")
            .enabledTools(List.of())
            .build();

    AgentExecutionRequest child =
        parent.deriveForSubAgent("sub task", "sub-conv", CHILD_TOOLS);

    assertEquals(CHILD_TOOLS, child.getEnabledTools());
  }

  /**
   * 子级未声明工具时，应完整继承父级白名单。
   */
  @Test
  @DisplayName("deriveForSubAgent 子级无工具时应继承父级白名单")
  void deriveForSubAgentShouldInheritParentToolsWhenChildUnlimited() {
    AgentExecutionRequest parent =
        AgentExecutionRequest.builder()
            .userInput("parent task")
            .enabledTools(PARENT_TOOLS)
            .build();

    AgentExecutionRequest child =
        parent.deriveForSubAgent("sub task", "sub-conv", null);

    assertEquals(PARENT_TOOLS, child.getEnabledTools());
  }

  /**
   * 父子均不限制时，子请求也应保持不限制（空列表）。
   */
  @Test
  @DisplayName("deriveForSubAgent 父子均无限制时结果为空")
  void deriveForSubAgentShouldReturnEmptyWhenBothUnlimited() {
    AgentExecutionRequest parent =
        AgentExecutionRequest.builder()
            .userInput("parent task")
            .enabledTools(List.of())
            .build();

    AgentExecutionRequest child =
        parent.deriveForSubAgent("sub task", "sub-conv", List.of());

    assertTrue(child.getEnabledTools().isEmpty());
  }

  /**
   * 派生请求不应携带父级的预置上下文，避免子任务上下文污染。
   */
  @Test
  @DisplayName("deriveForSubAgent 应清空预置上下文")
  void deriveForSubAgentShouldClearContextMessages() {
    AgentExecutionRequest parent =
        AgentExecutionRequest.builder()
            .userInput("parent task")
            .contextMessages(List.of(ChatMessage.user("history", "conv")))
            .build();

    AgentExecutionRequest child =
        parent.deriveForSubAgent("sub task", "sub-conv", null);

    assertTrue(child.getContextMessages().isEmpty());
    assertFalse(child.hasContextMessages());
  }

  /**
   * withContextMessages 应替换上下文消息并返回新实例，不修改原实例。
   */
  @Test
  @DisplayName("withContextMessages 应替换上下文并保证不可变")
  void withContextMessagesShouldReplaceAndPreserveImmutability() {
    List<ChatMessage> history = List.of(ChatMessage.user("h1", "conv"), ChatMessage.assistant("a1", "conv", null));
    AgentExecutionRequest original =
        AgentExecutionRequest.builder().userInput("task").build();

    AgentExecutionRequest updated = original.withContextMessages(history);

    assertNotSame(original, updated);
    assertEquals("task", updated.getUserInput());
    assertEquals(history, updated.getContextMessages());
    assertTrue(original.getContextMessages().isEmpty());
  }

  /**
   * Builder 不设置系统提示词时，默认值应为 null。
   */
  @Test
  @DisplayName("Builder 默认系统提示词为 null")
  void builderShouldDefaultSystemPromptToNull() {
    AgentExecutionRequest request =
        AgentExecutionRequest.builder().userInput("task").build();

    assertNull(request.getSystemPrompt());
    assertEquals(10, request.getMaxIterations());
    assertTrue(request.getEnabledTools().isEmpty());
  }
}
