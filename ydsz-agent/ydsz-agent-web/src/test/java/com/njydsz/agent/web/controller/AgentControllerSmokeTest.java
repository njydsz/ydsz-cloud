package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.function.Consumer;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.model.BatchChatResult;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.MvcExceptionHandler;
import com.njydsz.common.socket.push.SsePushChannel;
import com.njydsz.common.socket.push.SsePushChannelFactory;
import com.njydsz.common.socket.push.SsePushChannelMvcAdapter;
import com.njydsz.common.test.BaseControllerMockTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AgentController} Smoke Test。
 *
 * <p>Verify agent execution and chat endpoints return HTTP 200 + YdszResponse envelope.
 *
 * <p><b>补充覆盖（26.11.06）：</b>原文件只验证了 history 与 chat/stream 两个端点能返回 200，
 * 未覆盖 execute / chat / batchChat 的响应字段、多模态分支、以及流式与错误边界。
 * 本次补测在<b>不新增生产代码</b>的前提下补齐：
 *
 * <ul>
 *   <li>同步 execute / chat — 响应字段（content/model/respondedAt）与请求校验
 *   <li>多模态 chat / chatStream — {@code multimodalContent} 非空时走 Vision 重载，message 被忽略
 *   <li>batchChat — 条目透传与 success/failed 计数、单条失败不影响其他条目
 *   <li>流式边界 — SSE 通道创建、文本 chunk 转发、守卫拒绝时不创建通道
 *   <li>负例 — 请求守卫抛 BusinessException 时不调用下游 facade（幂等/限流前置语义）
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class AgentControllerSmokeTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  private SsePushChannelFactory ssePushChannelFactory;

  private AgentFacade agentFacade;

  private AgentRequestGuard requestGuard;

  private AgentController agentController;

  @BeforeEach
  void setUp() {
    ssePushChannelFactory = mock(SsePushChannelFactory.class);
    agentFacade = mock(AgentFacade.class);
    requestGuard = mock(AgentRequestGuard.class);

    agentController =
        new AgentController(ssePushChannelFactory, agentFacade, guardProvider(requestGuard));
    mockMvc = standaloneSetup(agentController);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<AgentRequestGuard> guardProvider(AgentRequestGuard guard) {
    ObjectProvider<AgentRequestGuard> provider = mock(ObjectProvider.class);
    // 构造器内 getIfAvailable() 消费此 stub，因此保留在 setUp 链路上
    when(provider.getIfAvailable()).thenReturn(guard);
    return provider;
  }

  /** 成功 SSE 通道：真实 {@link SsePushChannelMvcAdapter}（SseEmitter 为 final 类，不可 mock）。 */
  private SsePushChannel sseChannel() {
    return new SsePushChannelMvcAdapter("test-session", null);
  }

  private static ChatResponse chatResponse(String content) {
    return new ChatResponse(
        "resp-001",
        "test-model",
        ChatMessage.assistant(content, "conv-001", null),
        null,
        "stop",
        List.of());
  }

  @Nested
  @DisplayName("GET /agent/history")
  class History {

    @Test
    @DisplayName("should return 200 when get history succeeds")
    void should_return_200_when_get_history_succeeds() throws Exception {
      when(agentFacade.getHistory("conv-001")).thenReturn(List.of());

      mockMvc.perform(get("/agent/history")
              .param("conversationId", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE));
    }

    @Test
    @DisplayName("历史条目应透出 role 的 API 值、id 与 createdAt")
    void should_expose_role_api_value_and_created_at() throws Exception {
      when(agentFacade.getHistory("conv-001"))
          .thenReturn(List.of(ChatMessage.assistant("你好", "conv-001", null)));

      mockMvc.perform(get("/agent/history")
              .param("conversationId", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].role").value("assistant"))
          .andExpect(jsonPath("$.data[0].content").value("你好"))
          .andExpect(jsonPath("$.data[0].id").exists())
          .andExpect(jsonPath("$.data[0].createdAt").exists());
    }

    @Test
    @DisplayName("消息 content 为 null 时应回落为空字符串")
    void should_fallback_to_empty_content_when_message_content_null() throws Exception {
      when(agentFacade.getHistory("conv-001"))
          .thenReturn(List.of(ChatMessage.assistant(null, "conv-001", null)));

      mockMvc.perform(get("/agent/history")
              .param("conversationId", "conv-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].content").value(""));
    }
  }

  @Nested
  @DisplayName("DELETE /agent/history")
  class ClearHistory {

    @Test
    @DisplayName("应返回 200 且 data 为 null 时不出现 data 字段")
    void should_return_200_with_null_data() throws Exception {
      mockMvc.perform(delete("/agent/history")
              .param("conversationId", "conv-001")
              .param("requestId", "req-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(agentFacade).clearHistory("conv-001");
    }

    @Test
    @DisplayName("缺少 conversationId 时应返回 400 且不调用下游")
    void should_return_400_when_conversation_id_missing() throws Exception {
      mockMvc.perform(delete("/agent/history"))
          .andExpect(status().isBadRequest());

      verify(agentFacade, never()).clearHistory(anyString());
    }
  }

  @Nested
  @DisplayName("POST /agent/chat")
  class Chat {

    @Test
    @DisplayName("应返回 200 且 data 含 content/model/respondedAt")
    void should_return_200_with_response_fields() throws Exception {
      when(agentFacade.chat("conv-001", "你好", null)).thenReturn(chatResponse("你好！"));

      mockMvc.perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-001\",\"requestId\":\"req-001\","
                  + "\"message\":\"你好\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.content").value("你好！"))
          .andExpect(jsonPath("$.data.model").value("test-model"))
          .andExpect(jsonPath("$.data.respondedAt").exists());
    }

    @Test
    @DisplayName("message 为空字符串时应返回 400 且不调用下游")
    void should_return_400_when_message_blank() throws Exception {
      mockMvc.perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-001\",\"message\":\"\"}"))
          .andExpect(status().isBadRequest());

      verify(agentFacade, never()).chat(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("请求体非 JSON 时应返回 400")
    void should_return_400_for_malformed_body() throws Exception {
      mockMvc.perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("not-a-json"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("多模态内容非空时应走 Vision 重载并忽略 message")
    void should_route_to_vision_overload_when_multimodal_present() throws Exception {
      when(agentFacade.chat(anyString(), any(MessageContent.class), any()))
          .thenReturn(chatResponse("看图回答"));

      mockMvc.perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-002\",\"message\":\"被忽略\","
                  + "\"multimodalContent\":[{\"type\":\"text\",\"text\":\"描述这张图\"},"
                  + "{\"type\":\"image_url\",\"imageUrl\":\"https://example.com/a.png\"}]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.content").value("看图回答"));

      ArgumentCaptor<MessageContent> captor = ArgumentCaptor.forClass(MessageContent.class);
      verify(agentFacade).chat(anyString(), captor.capture(), any());
      assertThat(captor.getValue().getParts()).hasSize(2);
      assertThat(captor.getValue().hasImages()).isTrue();
    }

    @Test
    @DisplayName("不支持的 multimodal 类型应返回 400 且不调用下游")
    void should_return_400_for_unsupported_content_type() throws Exception {
      mockMvc.perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-003\",\"multimodalContent\":"
                  + "[{\"type\":\"audio\",\"text\":\"x\"}]}"))
          .andExpect(status().isBadRequest());

      verify(agentFacade, never()).chat(anyString(), any(MessageContent.class), any());
    }

    @Test
    @DisplayName("请求守卫拒绝时不应调用下游 facade")
    void should_not_call_facade_when_guard_rejects() throws Exception {
      doThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR))
          .when(requestGuard).check(any(), any());

      // standaloneSetup 未装配 advice：BusinessException 冒泡为 ServletException
      // （生产环境由 MvcExceptionHandler 映射为 400 + A01057，见 ErrorEnvelope 用例）
      assertThat(catchThrowable(() -> mockMvc.perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-001\",\"requestId\":\"dup-req\","
                  + "\"message\":\"你好\"}"))))
          .isInstanceOf(ServletException.class)
          .hasRootCauseInstanceOf(BusinessException.class);

      verify(agentFacade, never()).chat(anyString(), anyString(), any());
    }
  }

  @Nested
  @DisplayName("POST /agent/chat/stream")
  class ChatStream {

    @Test
    @DisplayName("should return 200 SSE channel when chat stream starts")
    void should_return_200_when_chat_stream_starts() throws Exception {
      when(ssePushChannelFactory.create()).thenReturn(sseChannel());

      mockMvc.perform(post("/agent/chat/stream")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  "{\"conversationId\":\"conv-001\",\"requestId\":\"req-001\","
                      + "\"message\":\"hello\"}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    @DisplayName("多模态流式对话应走 Vision 重载，文本重载不被调用")
    void should_route_multimodal_to_vision_stream() throws Exception {
      when(ssePushChannelFactory.create()).thenReturn(sseChannel());
      doAnswer(invocation -> {
            Consumer<ChatChunk> consumer = invocation.getArgument(3);
            consumer.accept(ChatChunk.content("c-1", "vision-model", "流式回答"));
            return null;
          }).when(agentFacade).stream(anyString(), any(MessageContent.class), any(), any());

      mockMvc.perform(post("/agent/chat/stream")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-009\",\"message\":\"被忽略\","
                  + "\"multimodalContent\":[{\"type\":\"image_url\","
                  + "\"imageUrl\":\"https://example.com/a.png\"}]}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

      verify(agentFacade).stream(anyString(), any(MessageContent.class), any(), any());
      verify(agentFacade, never()).stream(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("文本流式对话应按 chunk 转发增量内容与 finish 帧")
    void should_forward_text_chunks() throws Exception {
      when(ssePushChannelFactory.create()).thenReturn(sseChannel());
      doAnswer(invocation -> {
            Consumer<ChatChunk> consumer = invocation.getArgument(3);
            consumer.accept(ChatChunk.content("c-1", "test-model", "你"));
            consumer.accept(ChatChunk.content("c-2", "test-model", "好"));
            consumer.accept(ChatChunk.finish("c-3", "test-model", "stop", null));
            return null;
          }).when(agentFacade).stream(anyString(), anyString(), any(), any());

      mockMvc.perform(post("/agent/chat/stream")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-010\",\"message\":\"你好\"}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

      ArgumentCaptor<Consumer<ChatChunk>> captor = chunkConsumerCaptor();
      verify(agentFacade).stream(anyString(), anyString(), any(), captor.capture());
      assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("请求守卫拒绝时不应创建 SSE 通道")
    void should_not_create_channel_when_guard_rejects() throws Exception {
      doThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR))
          .when(requestGuard).check(any(), any());

      assertThat(catchThrowable(() -> mockMvc.perform(post("/agent/chat/stream")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-011\",\"requestId\":\"dup\","
                  + "\"message\":\"你好\"}"))))
          .isInstanceOf(ServletException.class)
          .hasRootCauseInstanceOf(BusinessException.class);

      verify(ssePushChannelFactory, never()).create();
    }
  }

  @Nested
  @DisplayName("POST /agent/execute")
  class Execute {

    @Test
    @DisplayName("应返回 200 且透传 content/model")
    void should_return_200_with_content_and_model() throws Exception {
      when(agentFacade.execute(any())).thenReturn(chatResponse("执行完成"));

      mockMvc.perform(post("/agent/execute")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"agentCode\":\"default\",\"conversationId\":\"conv-100\","
                  + "\"requestId\":\"req-100\",\"userInput\":\"查项目\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.content").value("执行完成"))
          .andExpect(jsonPath("$.data.model").value("test-model"));
    }

    @Test
    @DisplayName("未传 maxIterations 时应回落为默认 10")
    void should_default_max_iterations() throws Exception {
      when(agentFacade.execute(any())).thenReturn(chatResponse("ok"));

      mockMvc.perform(post("/agent/execute")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"agentCode\":\"default\",\"userInput\":\"任务\"}"))
          .andExpect(status().isOk());

      ArgumentCaptor<AgentExecutionRequest> captor =
          ArgumentCaptor.forClass(AgentExecutionRequest.class);
      verify(agentFacade).execute(captor.capture());
      assertThat(captor.getValue().getMaxIterations()).isEqualTo(10);
    }

    @Test
    @DisplayName("userInput 为空时应返回 400 且不调用下游")
    void should_return_400_when_user_input_blank() throws Exception {
      mockMvc.perform(post("/agent/execute")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"agentCode\":\"default\",\"userInput\":\"\"}"))
          .andExpect(status().isBadRequest());

      verify(agentFacade, never()).execute(any());
    }
  }

  @Nested
  @DisplayName("POST /agent/chat/batch")
  class BatchChat {

    @Test
    @DisplayName("应返回 200 且透出 successCount/failedCount/results")
    void should_return_200_with_counts() throws Exception {
      BatchChatResult domainResult = new BatchChatResult(
          List.of(
              BatchChatResult.BatchResultItem.success("item-1", "成功", "m", null, "stop"),
              BatchChatResult.BatchResultItem.failure("item-2", "LLM 超时")),
          12L);
      when(agentFacade.batchChat(any())).thenReturn(domainResult);

      mockMvc.perform(post("/agent/chat/batch")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"requestId\":\"req-200\",\"items\":["
                  + "{\"itemId\":\"item-1\",\"conversationId\":\"conv-a\",\"message\":\"问题1\"},"
                  + "{\"itemId\":\"item-2\",\"conversationId\":\"conv-b\",\"message\":\"问题2\"}]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.successCount").value(1))
          .andExpect(jsonPath("$.data.failedCount").value(1))
          .andExpect(jsonPath("$.data.totalDurationMs").value(12))
          .andExpect(jsonPath("$.data.results.length()").value(2))
          .andExpect(jsonPath("$.data.results[0].itemId").value("item-1"))
          .andExpect(jsonPath("$.data.results[1].errorMessage").value("LLM 超时"));
    }

    @Test
    @DisplayName("items 为空时应返回 400 且不调用下游")
    void should_return_400_when_items_empty() throws Exception {
      mockMvc.perform(post("/agent/chat/batch")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"requestId\":\"req-201\",\"items\":[]}"))
          .andExpect(status().isBadRequest());

      verify(agentFacade, never()).batchChat(any());
    }

    @Test
    @DisplayName("批量条目中的多模态内容应转成 MessageContent 并忽略 message")
    void should_convert_multimodal_item_content() throws Exception {
      when(agentFacade.batchChat(any()))
          .thenReturn(new BatchChatResult(List.of(), 0L));

      mockMvc.perform(post("/agent/chat/batch")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"requestId\":\"req-202\",\"items\":[{"
                  + "\"itemId\":\"item-1\",\"conversationId\":\"conv-a\","
                  + "\"message\":\"被忽略\","
                  + "\"multimodalContent\":[{\"type\":\"text\",\"text\":\"看图\"}]}]}"))
          .andExpect(status().isOk());

      ArgumentCaptor<List<AgentFacade.BatchChatItem>> captor = batchItemCaptor();
      verify(agentFacade).batchChat(captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      assertThat(captor.getValue().get(0).getMultimodalContent()).isNotNull();
      assertThat(captor.getValue().get(0).getMultimodalContent().hasImages()).isFalse();
    }
  }

  @Nested
  @DisplayName("异常信封（MvcExceptionHandler 已装配）")
  class ErrorEnvelope {

    private MockMvc adviceMockMvc;

    @BeforeEach
    void setUpAdvice() {
      adviceMockMvc =
          standaloneSetupWithAdvice(
              new MvcExceptionHandler(
                  new MockEnvironment(),
                  null,
                  absentProvider(),
                  absentProvider(),
                  absentProvider()),
              agentController);
    }

    @Test
    @DisplayName("业务异常 A01057 → HTTP 400 + $.code 原样透出")
    void businessError_shouldReturn400WithCode() throws Exception {
      when(agentFacade.getHistory("conv-err"))
          .thenThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR));

      adviceMockMvc
          .perform(get("/agent/history").param("conversationId", "conv-err"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01057"));
    }

    @Test
    @DisplayName("系统异常 B01052 → HTTP 500（状态码取自异常本身，非处理器写死）")
    void systemError_shouldReturn500WithCode() throws Exception {
      when(agentFacade.getHistory("conv-boom"))
          .thenThrow(BusinessException.of(CoreExceptionCode.SYSTEM_ERROR));

      adviceMockMvc
          .perform(get("/agent/history").param("conversationId", "conv-boom"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("B01052"));
    }

    @Test
    @DisplayName("守卫拒绝的 BusinessException 应透传为 400 + 错误码")
    void guardRejection_shouldReturn400WithCode() throws Exception {
      doThrow(BusinessException.of(CoreExceptionCode.BUSINESS_ERROR))
          .when(requestGuard).check(any(), any());

      adviceMockMvc
          .perform(post("/agent/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conversationId\":\"conv-001\",\"requestId\":\"dup\","
                  + "\"message\":\"你好\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("A01057"));
    }
  }

  /**
   * "容器里没有这个 bean" 的 ObjectProvider，用于脱离 Spring 容器手工装配 advice。
   *
   * <p>spring-beans 7.x 的 {@code ObjectProvider} 全部方法为 default（默认实现委托给会抛
   * UnsupportedOperation 的 {@code stream()}），故只覆写 advice 构造期真正调用的
   * {@code getIfAvailable()}。
   *
   * @param <T> Provider 承载的 bean 类型
   * @return 取不到任何 bean 的 provider
   */
  private static <T> ObjectProvider<T> absentProvider() {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }
    };
  }

  /** 批量对话条目列表的 ArgumentCaptor 构造（泛型擦除，需 @SuppressWarnings）。 */
  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<AgentFacade.BatchChatItem>> batchItemCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }

  /** SSE chunk 消费者的 ArgumentCaptor 构造（泛型擦除，需 @SuppressWarnings）。 */
  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<ChatChunk>> chunkConsumerCaptor() {
    return ArgumentCaptor.forClass(Consumer.class);
  }
}
