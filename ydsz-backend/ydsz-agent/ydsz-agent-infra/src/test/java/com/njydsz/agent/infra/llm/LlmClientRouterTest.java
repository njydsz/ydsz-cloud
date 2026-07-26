package com.njydsz.agent.infra.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;

/**
 * {@link LlmClientRouter} 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>同步 chat() 的 Fallback 策略（可恢复 vs 不可恢复错误）</li>
 *   <li>流式 stream() 的 Fallback 策略（已开始流式输出不 Fallback）</li>
 *   <li>无可用 Provider / 无可用 Fallback 场景</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("LLM 客户端路由器 LlmClientRouter 测试")
@ExtendWith(MockitoExtension.class)
class LlmClientRouterTest {

    @Mock
    private LlmClient primary;
    @Mock
    private LlmClient secondary;

    private LlmClientRouter router;

    @BeforeEach
    void setUp() {
        router = new LlmClientRouter();
        // 使用 lenient 避免 strict stubbing 报错（部分测试用例不会调用所有 stub）
        // primary.supports=true 确保 resolveClient 总是返回 primary（ConcurrentHashMap 迭代顺序不确定）
        // secondary.supports=false 确保 secondary 仅作为 Fallback 目标（findFallback 不检查 supports）
        lenient().when(primary.getProvider()).thenReturn("openai");
        lenient().when(secondary.getProvider()).thenReturn("deepseek");
        lenient().when(primary.supports(any())).thenReturn(true);
        lenient().when(secondary.supports(any())).thenReturn(false);
        router.register(primary);
        router.register(secondary);
    }

    private ChatRequest request() {
        return ChatRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatMessage.user("hi", "conv-1")))
                .build();
    }

    private ChatResponse response(String content) {
        TokenUsage usage = new TokenUsage(10, 20);
        ChatMessage msg = ChatMessage.assistant(content, "conv-1", usage);
        return new ChatResponse("resp-1", "gpt-4o-mini", msg, usage, "stop", List.of());
    }

    private LlmException llmError(LlmException.ErrorType type) {
        return new LlmException("error: " + type, type);
    }

    // ==================== 同步 chat() ====================

    @Nested
    @DisplayName("同步 chat() Fallback 策略")
    class ChatFallback {

        @Test
        @DisplayName("主 Provider 成功：不触发 Fallback")
        void shouldNotFallbackOnSuccess() {
            when(primary.chat(any())).thenReturn(response("ok"));

            ChatResponse result = router.chat(request());

            assertThat(result.getContent()).isEqualTo("ok");
            verify(secondary, never()).chat(any());
        }

        @Test
        @DisplayName("NETWORK_TIMEOUT：触发 Fallback 到备用 Provider")
        void shouldFallbackOnNetworkTimeout() {
            when(primary.chat(any())).thenThrow(llmError(LlmException.ErrorType.NETWORK_TIMEOUT));
            when(secondary.chat(any())).thenReturn(response("fallback-ok"));

            ChatResponse result = router.chat(request());

            assertThat(result.getContent()).isEqualTo("fallback-ok");
            verify(primary, times(1)).chat(any());
            verify(secondary, times(1)).chat(any());
        }

        @Test
        @DisplayName("RATE_LIMITED：触发 Fallback")
        void shouldFallbackOnRateLimited() {
            when(primary.chat(any())).thenThrow(llmError(LlmException.ErrorType.RATE_LIMITED));
            when(secondary.chat(any())).thenReturn(response("fallback-ok"));

            ChatResponse result = router.chat(request());

            assertThat(result.getContent()).isEqualTo("fallback-ok");
            verify(secondary, times(1)).chat(any());
        }

        @Test
        @DisplayName("PROVIDER_ERROR：触发 Fallback")
        void shouldFallbackOnProviderError() {
            when(primary.chat(any())).thenThrow(llmError(LlmException.ErrorType.PROVIDER_ERROR));
            when(secondary.chat(any())).thenReturn(response("fallback-ok"));

            ChatResponse result = router.chat(request());

            assertThat(result.getContent()).isEqualTo("fallback-ok");
            verify(secondary, times(1)).chat(any());
        }

        @Test
        @DisplayName("AUTH_FAILED：不触发 Fallback，直接抛出")
        void shouldNotFallbackOnAuthFailed() {
            LlmException authError = llmError(LlmException.ErrorType.AUTH_FAILED);
            when(primary.chat(any())).thenThrow(authError);

            assertThatThrownBy(() -> router.chat(request()))
                    .isSameAs(authError);

            verify(secondary, never()).chat(any());
        }

        @Test
        @DisplayName("MODEL_NOT_FOUND：不触发 Fallback，直接抛出")
        void shouldNotFallbackOnModelNotFound() {
            LlmException notFoundError = llmError(LlmException.ErrorType.MODEL_NOT_FOUND);
            when(primary.chat(any())).thenThrow(notFoundError);

            assertThatThrownBy(() -> router.chat(request()))
                    .isSameAs(notFoundError);

            verify(secondary, never()).chat(any());
        }

        @Test
        @DisplayName("INVALID_RESPONSE：不触发 Fallback，直接抛出")
        void shouldNotFallbackOnInvalidResponse() {
            LlmException invalidError = llmError(LlmException.ErrorType.INVALID_RESPONSE);
            when(primary.chat(any())).thenThrow(invalidError);

            assertThatThrownBy(() -> router.chat(request()))
                    .isSameAs(invalidError);

            verify(secondary, never()).chat(any());
        }

        @Test
        @DisplayName("主 Provider 失败且无备用 Provider：抛原始异常")
        void shouldThrowOriginalWhenNoFallbackAvailable() {
            // 只注册一个 Provider
            LlmClientRouter singleRouter = new LlmClientRouter();
            LlmClient only = mock(LlmClient.class);
            when(only.getProvider()).thenReturn("only");
            when(only.supports(any())).thenReturn(true);
            when(only.chat(any())).thenThrow(llmError(LlmException.ErrorType.NETWORK_TIMEOUT));
            singleRouter.register(only);

            assertThatThrownBy(() -> singleRouter.chat(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("NETWORK_TIMEOUT");
        }
    }

    // ==================== 流式 stream() ====================

    @Nested
    @DisplayName("流式 stream() Fallback 策略")
    class StreamFallback {

        @Test
        @DisplayName("流式未开始 + NETWORK_TIMEOUT：触发 Fallback")
        void shouldFallbackWhenStreamNotStarted() {
            // primary.stream 抛异常前不回调 consumer → streamStarted=false
            doThrow(llmError(LlmException.ErrorType.NETWORK_TIMEOUT))
                    .when(primary).stream(any(), any());
            // secondary.stream 正常回调
            doAnswer(invocation -> {
                Consumer<ChatChunk> consumer = invocation.getArgument(1);
                consumer.accept(ChatChunk.content("s1", "model", "fallback-chunk"));
                consumer.accept(ChatChunk.finish("s2", "model", "stop", new TokenUsage(1, 1)));
                return null;
            }).when(secondary).stream(any(), any());

            List<ChatChunk> received = new ArrayList<>();
            router.stream(request(), received::add);

            assertThat(received).hasSize(2);
            assertThat(received.get(0).getDeltaContent()).isEqualTo("fallback-chunk");
            verify(secondary, times(1)).stream(any(), any());
        }

        @Test
        @DisplayName("流式已开始 + 异常：不触发 Fallback（无法回滚已发送的 chunk）")
        void shouldNotFallbackWhenStreamAlreadyStarted() {
            LlmException error = llmError(LlmException.ErrorType.NETWORK_TIMEOUT);
            // primary.stream 先回调一个 chunk，再抛异常
            doAnswer(invocation -> {
                Consumer<ChatChunk> consumer = invocation.getArgument(1);
                consumer.accept(ChatChunk.content("s1", "model", "partial"));
                throw error;
            }).when(primary).stream(any(), any());

            List<ChatChunk> received = new ArrayList<>();
            assertThatThrownBy(() -> router.stream(request(), received::add))
                    .isSameAs(error);

            // 只收到 partial chunk，未 Fallback
            assertThat(received).hasSize(1);
            assertThat(received.get(0).getDeltaContent()).isEqualTo("partial");
            verify(secondary, never()).stream(any(), any());
        }

        @Test
        @DisplayName("流式 AUTH_FAILED：不触发 Fallback")
        void shouldNotFallbackOnAuthFailedStream() {
            LlmException authError = llmError(LlmException.ErrorType.AUTH_FAILED);
            doThrow(authError)
                    .when(primary).stream(any(), any());

            assertThatThrownBy(() -> router.stream(request(), chunk -> {}))
                    .isSameAs(authError);

            verify(secondary, never()).stream(any(), any());
        }
    }
}
