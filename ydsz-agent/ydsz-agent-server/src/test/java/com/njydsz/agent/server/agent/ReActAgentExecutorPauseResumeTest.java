package com.njydsz.agent.server.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.execution.ExecutionCheckpoint;
import com.njydsz.agent.domain.execution.SessionPausedException;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.execution.ExecutionPauseService;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;

/**
 * {@link ReActAgentExecutor} 会话级暂停/恢复单元测试。
 *
 * <p>验证：
 * <ul>
 *   <li>工具审批门触发暂停时返回携带 approvalId 的暂停响应并保存检查点
 *   <li>审批通过后恢复执行，继续 ReAct 循环至最终结果
 *   <li>审批拒绝时返回拒绝响应并清理检查点
 *   <li>多轮迭代 Token 用量正确累计（修复不可变 TokenUsage 累加结果丢弃问题）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class ReActAgentExecutorPauseResumeTest {

  /** 默认最大迭代次数 */
  private static final int DEFAULT_MAX_ITERATIONS = 5;

  /** 对话 ID */
  private static final String CONV_ID = "conv-1";

  /** 链路 ID */
  private static final String TRACE_ID = "trace-1";

  /** 审批 ID */
  private static final String APPROVAL_ID = "approval-1";

  @Mock
  private LlmClient llmClient;

  @Mock
  private ConversationMemory memory;

  @Mock
  private ToolRegistry toolRegistry;

  @Mock
  private TraceRecorder traceRecorder;

  @Mock
  private AgentMetrics agentMetrics;

  @Mock
  private CostAnalysisService costAnalysisService;

  @Mock
  private GuardrailService guardrailService;

  @Mock
  private PromptTemplateProvider promptTemplateProvider;

  @Mock
  private RagService ragService;

  @Mock
  private MiddlewareChain middlewareChain;

  private ExecutionPauseService pauseService;
  private AgentProperties properties;
  private ReActAgentExecutor executor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    pauseService = new ExecutionPauseService();

    properties = new AgentProperties();
    properties.getLlm().setDefaultModel("gpt-4");
    properties.getLlm().setTemperature(0.7);
    properties.getLlm().setMaxTokens(2048);
    properties.getMemory().setMaxMessages(10);

    lenient().when(promptTemplateProvider.load(anyString())).thenReturn("You are an assistant.");
    lenient().when(toolRegistry.size()).thenReturn(0);
    lenient().when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
    lenient().when(traceRecorder.startTrace(anyString(), anyString())).thenReturn(TRACE_ID);
    lenient().when(guardrailService.applyInputGuardrails(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    lenient().when(guardrailService.applyOutputGuardrails(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    lenient().when(memory.load(anyString(), anyInt())).thenReturn(List.of());

    executor = new ReActAgentExecutor(
        llmClient,
        memory,
        toolRegistry,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider,
        ragService,
        middlewareChain,
        pauseService);
  }

  /**
   * 当 onActing 钩子抛出 SessionPausedException 时，应保存检查点并返回暂停响应。
   */
  @Test
  @DisplayName("暂停模式：执行中断并保存检查点")
  void executeShouldReturnPausedResponseAndSaveCheckpoint() {
    AgentExecutionRequest request = buildRequest("call sensitive tool");
    ToolCall sensitiveCall = new ToolCall("call-1", "riskyTool", Map.of("arg", "value"));
    ChatResponse toolCallResponse = new ChatResponse(
        "resp-1",
        "gpt-4",
        ChatMessage.assistantWithTools("use tool", CONV_ID, List.of(sensitiveCall), null),
        new TokenUsage(10, 5),
        "tool_calls",
        List.of(sensitiveCall));

    when(llmClient.chat(any(ChatRequest.class))).thenReturn(toolCallResponse);
    when(middlewareChain.executeActing(any(), any(AgentMiddleware.ActingProceed.class)))
        .thenThrow(new SessionPausedException(APPROVAL_ID, List.of(sensitiveCall)));

    ChatResponse response = executor.execute(request);

    assertEquals("paused", response.getFinishReason());
    assertEquals(APPROVAL_ID, response.getMetadata().get("approvalId"));
    assertEquals("PAUSED", response.getMetadata().get("status"));
    assertNotNull(pauseService.find(APPROVAL_ID).orElse(null));
    assertEquals(1, pauseService.size());
  }

  /**
   * 审批通过后，应执行待执行工具并继续 ReAct 循环得到最终结果。
   */
  @Test
  @DisplayName("恢复执行：审批通过后继续并得到最终结果")
  void resumeShouldExecutePendingToolsAndContinue() {
    ToolCall pendingCall = new ToolCall("call-1", "riskyTool", Map.of("arg", "value"));
    List<ChatMessage> messages = List.of(
        ChatMessage.system("You are an assistant."),
        ChatMessage.user("call sensitive tool", CONV_ID),
        ChatMessage.assistantWithTools("use tool", CONV_ID, List.of(pendingCall), null));

    AgentExecutionRequest request = buildRequest("call sensitive tool");
    ExecutionCheckpoint checkpoint = new ExecutionCheckpoint(
        APPROVAL_ID,
        request,
        CONV_ID,
        TRACE_ID,
        messages,
        List.of(pendingCall),
        new TokenUsage(10, 5),
        0,
        "call sensitive tool",
        "You are an assistant.",
        Map.of());

    when(toolRegistry.execute(pendingCall)).thenReturn("{\"result\":\"ok\"}");
    when(middlewareChain.executeActing(any(), any(AgentMiddleware.ActingProceed.class)))
        .thenAnswer(inv -> {
          AgentMiddleware.ActingProceed proceed = inv.getArgument(1);
          return proceed.execute();
        });

    ChatResponse finalResponse = new ChatResponse(
        "resp-final",
        "gpt-4",
        ChatMessage.assistant("done", CONV_ID, new TokenUsage(8, 4)),
        new TokenUsage(8, 4),
        "stop",
        List.of());
    when(llmClient.chat(any(ChatRequest.class))).thenReturn(finalResponse);

    ChatResponse response = executor.resume(checkpoint, true);

    assertEquals("stop", response.getFinishReason());
    assertEquals("done", response.getContent());
    assertEquals(22, response.getUsage().getTotalTokens());
    assertEquals(0, pauseService.size());
    verify(toolRegistry).execute(pendingCall);
  }

  /**
   * 审批拒绝时，应返回拒绝响应并清理检查点。
   */
  @Test
  @DisplayName("恢复执行：审批拒绝时中止并清理检查点")
  void resumeShouldReturnRejectedResponseWhenNotApproved() {
    AgentExecutionRequest request = buildRequest("call sensitive tool");
    ExecutionCheckpoint checkpoint = new ExecutionCheckpoint(
        APPROVAL_ID,
        request,
        CONV_ID,
        TRACE_ID,
        List.of(ChatMessage.user("call sensitive tool", CONV_ID)),
        List.of(new ToolCall("call-1", "riskyTool", Map.of())),
        TokenUsage.zero(),
        0,
        "call sensitive tool",
        "system",
        Map.of());
    pauseService.save(APPROVAL_ID, checkpoint);

    ChatResponse response = executor.resume(checkpoint, false);

    assertEquals("guardrail_rejected", response.getFinishReason());
    assertTrue(response.getContent().contains("未通过"));
    assertEquals(0, pauseService.size());
  }

  /**
   * 多轮迭代时 Token 用量应正确累计。
   */
  @Test
  @DisplayName("Token 用量跨迭代累计")
  void executeShouldAccumulateTokenUsageAcrossIterations() {
    AgentExecutionRequest request = buildRequest("multi-step task");
    ToolCall toolCall = new ToolCall("call-1", "search", Map.of("q", "x"));

    ChatResponse firstResponse = new ChatResponse(
        "resp-1",
        "gpt-4",
        ChatMessage.assistantWithTools("search", CONV_ID, List.of(toolCall), null),
        new TokenUsage(10, 5),
        "tool_calls",
        List.of(toolCall));
    ChatResponse finalResponse = new ChatResponse(
        "resp-2",
        "gpt-4",
        ChatMessage.assistant("done", CONV_ID, new TokenUsage(6, 3)),
        new TokenUsage(6, 3),
        "stop",
        List.of());

    when(llmClient.chat(any(ChatRequest.class))).thenReturn(firstResponse, finalResponse);
    when(toolRegistry.execute(toolCall)).thenReturn("{\"result\":\"ok\"}");
    when(middlewareChain.executeActing(any(), any(AgentMiddleware.ActingProceed.class)))
        .thenAnswer(inv -> {
          AgentMiddleware.ActingProceed proceed = inv.getArgument(1);
          return proceed.execute();
        });

    ChatResponse response = executor.execute(request);

    assertEquals("stop", response.getFinishReason());
    assertEquals(24, response.getUsage().getTotalTokens());
  }

  private AgentExecutionRequest buildRequest(String userInput) {
    return AgentExecutionRequest.builder()
        .conversationId(CONV_ID)
        .userInput(userInput)
        .maxIterations(DEFAULT_MAX_ITERATIONS)
        .build();
  }
}
