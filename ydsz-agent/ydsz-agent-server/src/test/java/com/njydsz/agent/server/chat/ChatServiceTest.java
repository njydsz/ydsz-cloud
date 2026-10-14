package com.njydsz.agent.server.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.config.properties.LlmProperties;
import com.njydsz.agent.domain.config.properties.MemoryProperties;
import com.njydsz.agent.domain.config.properties.QuotaProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.CostEstimate;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.event.AgentEventPublisher;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;
import com.njydsz.agent.server.quota.TenantQuotaService;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * ChatService 单元测试
 *
 * <p>使用 Mockito 模拟所有外部依赖，重点验证同步对话、流式对话、护栏拦截、错误处理等核心业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  private static final String CONV_ID = "conv-001";
  private static final String TRACE_ID = "trace-001";
  private static final String MODEL = "gpt-4";
  private static final String PROVIDER = "openai";

  @Mock private LlmClient llmClient;
  @Mock private ConversationMemory memory;
  @Mock private AgentProperties properties;
  @Mock private GuardrailService guardrailService;
  @Mock private AgentMetrics metrics;
  @Mock private AgentRuntimeMetrics runtimeMetrics;
  @Mock private CostAnalysisService costAnalysisService;
  @Mock private TraceRecorder traceRecorder;
  @Mock private AgentEventPublisher eventPublisher;
  @Mock private SnowflakeIdGenerator snowflakeIdGenerator;
  @Mock private TokenCostCalculator tokenCostCalculator;
  @Mock private TenantQuotaService quotaService;

  @InjectMocks
  private ChatService chatService;

  private LlmProperties llmProps() {
    LlmProperties p = new LlmProperties();
    p.setDefaultModel(MODEL);
    p.setTemperature(0.7);
    p.setMaxTokens(2048);
    return p;
  }

  private MemoryProperties memProps() {
    MemoryProperties p = new MemoryProperties();
    p.setMaxMessages(20);
    p.setTokenCharRatio(new BigDecimal("2.5"));
    return p;
  }

  private QuotaProperties disabledQuota() {
    QuotaProperties q = new QuotaProperties();
    q.setEnabled(false);
    return q;
  }

  @Test
  @DisplayName("同步对话 - 正常流程：护栏通过、LLM 调用成功、记忆保存")
  void should_completeChat_when_guardrailPassesAndLlmSucceeds() {
    // Arrange
    LlmProperties llm = llmProps();
    MemoryProperties mem = memProps();

    when(snowflakeIdGenerator.nextId()).thenReturn(100L);
    when(traceRecorder.startTrace(CONV_ID, "CHAT")).thenReturn(TRACE_ID);
    when(properties.getLlm()).thenReturn(llm);
    when(properties.getMemory()).thenReturn(mem);
    when(properties.getQuota()).thenReturn(disabledQuota());
    when(properties.getDefaultSystemPrompt()).thenReturn("default-prompt");
    when(llmClient.getProvider()).thenReturn(PROVIDER);
    when(guardrailService.applyInputGuardrails("hello")).thenReturn("hello");
    when(memory.load(CONV_ID, 20)).thenReturn(List.of());

    TokenUsage usage = new TokenUsage(100, 50);
    ChatMessage assistantMsg = ChatMessage.assistant("Hi!", CONV_ID, usage);
    ChatResponse llmResp = new ChatResponse("resp-1", MODEL, assistantMsg, usage, "stop", List.of());

    CostEstimate estimate = CostEstimate.estimate(80, 2048, MODEL, new BigDecimal("0.001"));
    when(tokenCostCalculator.estimateBeforeCall(any(ChatRequest.class))).thenReturn(estimate);
    when(tokenCostCalculator.calculateActual(any(TokenUsage.class), eq(MODEL)))
        .thenReturn(CostEstimate.actual(usage, MODEL, new BigDecimal("0.001")));
    when(llmClient.chat(any(ChatRequest.class))).thenReturn(llmResp);
    when(guardrailService.applyOutputGuardrails("Hi!")).thenReturn("Hi!");

    // Act
    ChatResponse result = chatService.chat(CONV_ID, "hello", null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEqualTo("Hi!");
    assertThat(result.getModel()).isEqualTo(MODEL);
    assertThat(result.getUsage().getTotalTokens()).isEqualTo(150);

    InOrder inOrder = inOrder(guardrailService, memory, tokenCostCalculator, llmClient);
    inOrder.verify(guardrailService).applyInputGuardrails("hello");
    inOrder.verify(memory).save(anyString(), any(ChatMessage.class));
    inOrder.verify(tokenCostCalculator).estimateBeforeCall(any(ChatRequest.class));
    inOrder.verify(llmClient).chat(any(ChatRequest.class));
  }

  @Test
  @DisplayName("同步对话 - 护栏拒绝输入：返回安全拒绝响应且不调用 LLM")
  void should_returnRejectionResponse_when_guardrailRejectsInput() {
    // Arrange
    when(snowflakeIdGenerator.nextId()).thenReturn(200L);
    when(traceRecorder.startTrace(CONV_ID, "CHAT")).thenReturn(TRACE_ID);
    when(guardrailService.applyInputGuardrails("dangerous")).thenReturn(null);

    // Act
    ChatResponse result = chatService.chat(CONV_ID, "dangerous", null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getFinishReason()).isEqualTo("guardrail_rejected");
    assertThat(result.getUsage().getTotalTokens()).isZero();

    verify(llmClient, never()).chat(any(ChatRequest.class));
    verify(metrics).recordGuardrailRejection(anyString(), anyString());
  }

  @Test
  @DisplayName("同步对话 - LLM 调用抛异常：记录失败指标并抛出 SysException")
  void should_handleExceptionAndThrowSysException_when_llmCallFails() {
    // Arrange
    LlmProperties llm = llmProps();
    MemoryProperties mem = memProps();
    RuntimeException llmError = new RuntimeException("LLM timeout");
    CostEstimate estimate = CostEstimate.estimate(50, 2048, MODEL, new BigDecimal("0.001"));

    when(snowflakeIdGenerator.nextId()).thenReturn(300L);
    when(traceRecorder.startTrace(CONV_ID, "CHAT")).thenReturn(TRACE_ID);
    when(properties.getLlm()).thenReturn(llm);
    when(properties.getMemory()).thenReturn(mem);
    when(properties.getQuota()).thenReturn(disabledQuota());
    when(properties.getDefaultSystemPrompt()).thenReturn("default-prompt");
    when(llmClient.getProvider()).thenReturn(PROVIDER);
    when(guardrailService.applyInputGuardrails("fail-test")).thenReturn("fail-test");
    when(memory.load(CONV_ID, 20)).thenReturn(List.of());
    when(tokenCostCalculator.estimateBeforeCall(any(ChatRequest.class))).thenReturn(estimate);
    when(llmClient.chat(any(ChatRequest.class))).thenThrow(llmError);

    // Act & Assert
    assertThatThrownBy(() -> chatService.chat(CONV_ID, "fail-test", null))
        .isInstanceOf(SysException.class);

    verify(metrics).recordLlmCall(eq(PROVIDER), eq(MODEL), any(long.class), any(), any(Throwable.class));
    verify(traceRecorder).endTrace(TRACE_ID, "FAILED");
    verify(eventPublisher).publishExecutionFailed(anyString(), anyString(), anyString(),
        anyString(), any(long.class), anyString());
  }

  @Test
  @DisplayName("多模态对话 - 跳过输入护栏，直接走多模态 LLM 调用")
  void should_skipInputGuardrailAndCallLlm_when_multimodal() {
    // Arrange
    MessageContent content = MessageContent.text("describe this image");
    LlmProperties llm = llmProps();
    MemoryProperties mem = memProps();
    TokenUsage usage = new TokenUsage(500, 200);
    ChatMessage assistantMsg = ChatMessage.assistant("I see a cat", CONV_ID, usage);
    ChatResponse llmResp = new ChatResponse("resp-mm", MODEL, assistantMsg, usage, "stop", List.of());
    CostEstimate estimate = CostEstimate.estimate(500, 2048, MODEL, new BigDecimal("0.002"));

    when(snowflakeIdGenerator.nextId()).thenReturn(400L);
    when(traceRecorder.startTrace(CONV_ID, "CHAT_MULTIMODAL")).thenReturn(TRACE_ID);
    when(properties.getLlm()).thenReturn(llm);
    when(properties.getMemory()).thenReturn(mem);
    when(properties.getQuota()).thenReturn(disabledQuota());
    when(properties.getDefaultSystemPrompt()).thenReturn("multimodal-prompt");
    when(llmClient.getProvider()).thenReturn(PROVIDER);
    when(memory.load(CONV_ID, 20)).thenReturn(List.of());
    when(tokenCostCalculator.estimateBeforeCall(any(ChatRequest.class))).thenReturn(estimate);
    when(tokenCostCalculator.calculateActual(any(TokenUsage.class), eq(MODEL)))
        .thenReturn(CostEstimate.actual(usage, MODEL, new BigDecimal("0.002")));
    when(llmClient.chat(any(ChatRequest.class))).thenReturn(llmResp);
    when(guardrailService.applyOutputGuardrails("I see a cat")).thenReturn("I see a cat");

    // Act
    ChatResponse result = chatService.chat(CONV_ID, content, null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEqualTo("I see a cat");
    verify(guardrailService, never()).applyInputGuardrails(anyString());
  }

  @Test
  @DisplayName("流式对话 - 护栏拒绝输入时投递错误响应")
  void should_emitErrorChunks_when_guardrailRejectsStreamInput() {
    // Arrange
    when(traceRecorder.startTrace(CONV_ID, "CHAT_STREAM")).thenReturn(TRACE_ID);
    when(guardrailService.applyInputGuardrails("blocked")).thenReturn(null);

    // Act
    chatService.stream(CONV_ID, "blocked", null, chunk -> { /* no-op */ });

    // Assert
    verify(llmClient, never()).stream(any(ChatRequest.class), any());
    verify(runtimeMetrics).recordExecution("simple", false, 0L);
  }

  @Test
  @DisplayName("流式对话 - 收到多个 chunk 后完整拼接并保存记忆")
  void should_processChunksAndSaveMemory_when_streamSucceeds() {
    // Arrange
    LlmProperties llm = llmProps();
    MemoryProperties mem = memProps();
    TokenUsage streamUsage = new TokenUsage(80, 40);
    CostEstimate estimate = CostEstimate.estimate(80, 2048, MODEL, new BigDecimal("0.001"));

    when(snowflakeIdGenerator.nextId()).thenReturn(600L);
    when(traceRecorder.startTrace(CONV_ID, "CHAT_STREAM")).thenReturn(TRACE_ID);
    when(properties.getLlm()).thenReturn(llm);
    when(properties.getMemory()).thenReturn(mem);
    when(properties.getQuota()).thenReturn(disabledQuota());
    when(properties.getDefaultSystemPrompt()).thenReturn("stream-prompt");
    when(llmClient.getProvider()).thenReturn(PROVIDER);
    when(guardrailService.applyInputGuardrails("question")).thenReturn("question");
    when(memory.load(CONV_ID, 20)).thenReturn(List.of());
    when(tokenCostCalculator.estimateBeforeCall(any(ChatRequest.class))).thenReturn(estimate);
    when(tokenCostCalculator.calculateActual(any(TokenUsage.class), eq(MODEL)))
        .thenReturn(CostEstimate.actual(streamUsage, MODEL, new BigDecimal("0.001")));

    // Use ArgumentCaptor to capture and invoke the Consumer callback directly
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<ChatChunk>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
    doAnswer(invocation -> {
      Consumer<ChatChunk> consumer = consumerCaptor.getValue();
      consumer.accept(ChatChunk.content("c1", MODEL, "Hello"));
      consumer.accept(ChatChunk.content("c2", MODEL, " world"));
      consumer.accept(ChatChunk.finish("c3", MODEL, "stop", streamUsage));
      return null;
    }).when(llmClient).stream(any(ChatRequest.class), consumerCaptor.capture());

    when(guardrailService.applyOutputGuardrails("Hello world")).thenReturn("Hello world");

    StringBuilder received = new StringBuilder();

    // Act
    chatService.stream(CONV_ID, "question", null, chunk -> {
      if (chunk.hasContent()) {
        received.append(chunk.getDeltaContent());
      }
    });

    // Assert
    assertThat(received.toString()).isEqualTo("Hello world");
    verify(llmClient).stream(any(ChatRequest.class), consumerCaptor.capture());
    verify(runtimeMetrics).recordMessage("user");
    verify(runtimeMetrics).recordMessage("assistant");
  }

  @Test
  @DisplayName("获取对话历史 - 委托给 memory.load")
  void should_returnHistoryFromMemory_when_getHistory() {
    // Arrange
    MemoryProperties mem = memProps();
    when(properties.getMemory()).thenReturn(mem);

    ChatMessage msg1 = ChatMessage.user("msg1", CONV_ID);
    ChatMessage msg2 = ChatMessage.assistant("reply", CONV_ID, TokenUsage.zero());
    when(memory.load(CONV_ID, 20)).thenReturn(List.of(msg1, msg2));

    // Act
    List<ChatMessage> history = chatService.getHistory(CONV_ID);

    // Assert
    assertThat(history).hasSize(2);
    assertThat(history.get(0).getContent()).isEqualTo("msg1");
    assertThat(history.get(1).getContent()).isEqualTo("reply");
  }

  @Test
  @DisplayName("清除对话历史 - 委托给 memory.clear")
  void should_delegateToMemoryClear_when_clearHistory() {
    // Act
    chatService.clearHistory(CONV_ID);

    // Assert
    verify(memory).clear(CONV_ID);
  }
}
