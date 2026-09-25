package com.njydsz.agent.server.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import com.njydsz.common.locales.util.I18nMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.metrics.AgentMetrics;

/**
 * {@link AbstractAgentExecutor#loadHistory} 单元测试。
 *
 * <p>验证预置上下文优先与兜底 memory.load 行为。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class AbstractAgentExecutorLoadHistoryTest {

  /** 默认最大历史消息数 */
  private static final int DEFAULT_MAX_MESSAGES = 10;

  @Mock
  private ConversationMemory memory;

  private TestAgentExecutor executor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    AgentProperties properties = new AgentProperties();
    properties.getMemory().setMaxMessages(DEFAULT_MAX_MESSAGES);
    executor =
        new TestAgentExecutor(
            null,
            memory,
            properties,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
  }

  /**
   * 请求携带预置上下文时，应直接返回预置消息，不调用 memory.load。
   */
  @Test
  @DisplayName("预置上下文优先且不调用 memory.load")
  void loadHistoryShouldPreferPreloadedContext() {
    List<ChatMessage> preloaded =
        List.of(ChatMessage.user("preloaded", "conv"), ChatMessage.assistant("ok", "conv", null));
    AgentExecutionRequest request =
        AgentExecutionRequest.builder()
            .userInput("task")
            .contextMessages(preloaded)
            .build();

    List<ChatMessage> result = executor.loadHistory(request, "conv");

    assertSame(preloaded, result);
    verifyNoInteractions(memory);
  }

  /**
   * 请求未携带预置上下文时，应调用 memory.load 并按 maxMessages 取历史。
   */
  @Test
  @DisplayName("无预置上下文时调用 memory.load")
  void loadHistoryShouldFallbackToMemoryLoad() {
    List<ChatMessage> fromMemory =
        List.of(ChatMessage.user("hello", "conv"), ChatMessage.assistant("hi", "conv", null));
    when(memory.load("conv", DEFAULT_MAX_MESSAGES)).thenReturn(fromMemory);
    AgentExecutionRequest request =
        AgentExecutionRequest.builder().userInput("task").build();

    List<ChatMessage> result = executor.loadHistory(request, "conv");

    assertEquals(fromMemory, result);
    verify(memory).load("conv", DEFAULT_MAX_MESSAGES);
  }

  /**
   * 预置上下文为 null 的请求应走 memory.load 兜底。
   */
  @Test
  @DisplayName("contextMessages 为 null 时走 memory.load")
  void loadHistoryShouldUseMemoryWhenContextMessagesNull() {
    List<ChatMessage> fromMemory = List.of(ChatMessage.user("only memory", "conv"));
    when(memory.load("conv", DEFAULT_MAX_MESSAGES)).thenReturn(fromMemory);
    AgentExecutionRequest request =
        AgentExecutionRequest.builder()
            .userInput("task")
            .contextMessages(null)
            .build();

    List<ChatMessage> result = executor.loadHistory(request, "conv");

    assertEquals(fromMemory, result);
    verify(memory).load("conv", DEFAULT_MAX_MESSAGES);
  }

  /**
   * 用于暴露受保护方法的测试执行器。
   */
  private static final class TestAgentExecutor extends AbstractAgentExecutor {

    TestAgentExecutor(
        LlmClient llmClient,
        ConversationMemory memory,
        AgentProperties properties,
        TraceRecorder traceRecorder,
        AgentMetrics agentMetrics,
        CostAnalysisService costAnalysisService,
        GuardrailService guardrailService,
        PromptTemplateProvider promptTemplateProvider,
        MiddlewareChain middlewareChain,
        I18nMessages i18nMessages) {
      super(
          llmClient,
          memory,
          properties,
          traceRecorder,
          agentMetrics,
          costAnalysisService,
          guardrailService,
          promptTemplateProvider,
          middlewareChain,
          i18nMessages);
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
      throw new UnsupportedOperationException("测试桩未实现");
    }

    @Override
    public void executeStream(
        AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
      throw new UnsupportedOperationException("测试桩未实现");
    }

    @Override
    public String getType() {
      return "test";
    }

    @Override
    public boolean supports(String type) {
      return "test".equals(type);
    }
  }
}
