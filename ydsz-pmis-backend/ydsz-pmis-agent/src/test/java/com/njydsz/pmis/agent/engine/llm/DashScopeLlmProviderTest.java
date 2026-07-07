package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * DashScopeLlmProvider 单元测试（P0-5 修复验证）
 *
 * <p>使用 Spring Test 的 {@link MockRestServiceServer} 模拟 DashScope HTTP 响应，
 * 无真实网络请求。覆盖：
 * <ul>
 *   <li>API Key 为空时降级到 MockLlmProvider</li>
 *   <li>HTTP 2xx 响应：正确提取 choices[0].message.content</li>
 *   <li>P0-5: HTTP 401 InvalidApiKey - 抛出带错误码的异常</li>
 *   <li>P0-5: HTTP 500 - fallback=true 时降级到 mock</li>
 *   <li>P0-5: HTTP 429 - 重试 2 次后降级 mock</li>
 *   <li>P0-5: HTTP 500 第 1 次 + HTTP 200 第 2 次 → 重试成功</li>
 *   <li>P0-5: 非 JSON 错误响应体也能解析（不抛异常）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@DisplayName("DashScopeLlmProvider 通义千问 LLM Provider 测试")
class DashScopeLlmProviderTest {

    /** 测试用 base URL（MockRestServiceServer 需要绝对 URI 匹配） */
    private static final String BASE_URL = "https://dashscope.aliyuncs.com";
    /** 完整请求 URL */
    private static final String CHAT_URL = BASE_URL + "/v1/chat/completions";

    /** 当前测试用 Provider 实例（每个测试方法重置） */
    private DashScopeLlmProvider provider;

    @AfterEach
    void tearDown() {
        // P0-4: 销毁共享线程池，避免测试间线程泄漏
        if (provider != null) {
            provider.destroy();
            provider = null;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造带 MockRestServiceServer 的 Provider。
     *
     * @param apiKey     API Key
     * @param maxRetries 最大重试次数
     * @param fallback   是否降级到 mock
     * @return MockRestServiceServer 实例
     */
    private MockRestServiceServer setupProvider(String apiKey, int maxRetries, boolean fallback) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        provider = new DashScopeLlmProvider(apiKey, "qwen-turbo", 5000, maxRetries, fallback,
                builder.build());
        return server;
    }

    /**
     * 构造带 JSON Content-Type 的 HTTP 响应。
     *
     * <p>MockRestServiceServer 默认 Content-Type 为 application/octet-stream，
     * RestClient 找不到合适的 HttpMessageConverter 反序列化为 Map，故需显式指定。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return ResponseCreator
     */
    private static ResponseCreator jsonResp(HttpStatus status, String body) {
        return withStatus(status).body(body).contentType(MediaType.APPLICATION_JSON);
    }

    /** 标准 DashScope 响应体（choices[0].message.content） */
    private String dashScopeResponse(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}";
    }

    /** 带 request_id 的 DashScope 响应体（P1-4: providerTraceId 提取测试用） */
    private String dashScopeResponseWithRequestId(String content, String requestId) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content
                + "\"}}],\"request_id\":\"" + requestId + "\"}";
    }

    /** DashScope 标准错误响应体 */
    private String dashScopeError(String code, String message) {
        return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
    }

    // ==================== API Key 为空降级测试 ====================

    @Nested
    @DisplayName("API Key 为空降级测试")
    class EmptyApiKeyTest {

        @Test
        @DisplayName("apiKey=null 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenApiKeyNull() {
            provider = new DashScopeLlmProvider(null, "qwen-turbo", 5000, 0, true,
                    RestClient.builder().build());

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("apiKey=空串 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenApiKeyEmpty() {
            provider = new DashScopeLlmProvider("", "qwen-turbo", 5000, 0, true,
                    RestClient.builder().build());

            String result = provider.chat("sys", "严重告警", new AgentContext());

            // MockLlmProvider 检测到"严重"关键词返回 RED
            assertThat(result).contains("RED");
        }
    }

    // ==================== HTTP 2xx 成功调用测试 ====================

    @Nested
    @DisplayName("HTTP 2xx 成功调用测试")
    class SuccessTest {

        @Test
        @DisplayName("HTTP 200 时正确提取 choices[0].message.content")
        void shouldExtractContentOnSuccess() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK, dashScopeResponse("hello dashscope")));

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("hello dashscope");
        }

        @Test
        @DisplayName("响应 choices 为空数组时返回空字符串")
        void shouldReturnEmptyWhenChoicesEmpty() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK, "{\"choices\":[]}"));

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("");
        }
    }

    // ==================== P0-5: HTTP 错误响应处理测试 ====================

    @Nested
    @DisplayName("P0-5: HTTP 错误响应处理测试")
    class HttpErrorTest {

        @Test
        @DisplayName("HTTP 401 InvalidApiKey - 抛出带错误码的异常")
        void shouldThrowWithErrorCodeOn401() {
            MockRestServiceServer server = setupProvider("sk-invalid", 0, false);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.UNAUTHORIZED,
                            dashScopeError("InvalidApiKey", "The API key provided is invalid.")));

            // fallback=false, maxRetries=0：直接抛 RuntimeException("LLM dashscope failed after 1 attempts", cause)
            // cause 为 onStatus 抛出的 "DashScope HTTP 401 [InvalidApiKey]: ..."
            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("dashscope")
                    .hasMessageContaining("failed")
                    .hasCauseInstanceOf(RuntimeException.class)
                    .cause()
                    .hasMessageContaining("401")
                    .hasMessageContaining("InvalidApiKey")
                    .hasMessageContaining("invalid");
        }

        @Test
        @DisplayName("HTTP 500 - fallback=true 时降级到 mock")
        void shouldFallbackToMockOn500() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.INTERNAL_SERVER_ERROR,
                            dashScopeError("InternalError", "server busy")));

            String result = provider.chat("sys", "user", new AgentContext());

            // 降级到 MockLlmProvider
            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("HTTP 500 - fallback=false 时抛 RuntimeException")
        void shouldThrowOnHttp500WhenFallbackDisabled() {
            MockRestServiceServer server = setupProvider("sk-test", 0, false);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.INTERNAL_SERVER_ERROR,
                            dashScopeError("InternalError", "server busy")));

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("dashscope")
                    .hasMessageContaining("failed")
                    .cause()
                    .hasMessageContaining("500")
                    .hasMessageContaining("InternalError");
        }

        @Test
        @DisplayName("HTTP 429 - 重试 2 次后降级 mock")
        void shouldRetryOn429ThenFallback() {
            MockRestServiceServer server = setupProvider("sk-test", 2, true);
            String errBody = dashScopeError("RateLimitExceeded", "too many requests");
            // 首次 + 2 次重试 = 3 次请求
            server.expect(requestTo(CHAT_URL)).andRespond(jsonResp(HttpStatus.TOO_MANY_REQUESTS, errBody));
            server.expect(requestTo(CHAT_URL)).andRespond(jsonResp(HttpStatus.TOO_MANY_REQUESTS, errBody));
            server.expect(requestTo(CHAT_URL)).andRespond(jsonResp(HttpStatus.TOO_MANY_REQUESTS, errBody));

            String result = provider.chat("sys", "user", new AgentContext());

            // 重试 3 次都失败，降级 mock
            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("HTTP 500 第 1 次 + HTTP 200 第 2 次 → 重试成功")
        void shouldRetryAndSucceedOnSecondAttempt() {
            MockRestServiceServer server = setupProvider("sk-test", 2, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.INTERNAL_SERVER_ERROR,
                            dashScopeError("InternalError", "transient")));
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK, dashScopeResponse("recovered")));

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("recovered");
        }

        @Test
        @DisplayName("非 JSON 错误响应体也能解析（不抛异常，保留原始文本）")
        void shouldHandleNonJsonErrorBody() {
            MockRestServiceServer server = setupProvider("sk-test", 0, false);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.BAD_GATEWAY, "Bad Gateway"));

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .cause()
                    .hasMessageContaining("502")
                    .hasMessageContaining("UNKNOWN")
                    .hasMessageContaining("Bad Gateway");
        }

        @Test
        @DisplayName("HTTP 400 请求参数错误 - 抛出带错误码的异常")
        void shouldThrowWithBadRequestCode() {
            MockRestServiceServer server = setupProvider("sk-test", 0, false);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.BAD_REQUEST,
                            dashScopeError("InvalidParameter", "model not found")));

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .cause()
                    .hasMessageContaining("400")
                    .hasMessageContaining("InvalidParameter")
                    .hasMessageContaining("model not found");
        }
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("name() 返回 'dashscope'")
        void shouldReturnDashScopeName() {
            provider = new DashScopeLlmProvider("sk-test", "qwen-turbo", 5000, 0, true,
                    RestClient.builder().build());

            assertThat(provider.name()).isEqualTo("dashscope");
        }
    }

    // ==================== P1-4: providerTraceId 提取测试 ====================

    @Nested
    @DisplayName("P1-4: providerTraceId 提取测试")
    class ProviderTraceIdTest {

        @Test
        @DisplayName("响应包含 request_id 时写入 AgentContext.providerTraceId")
        void shouldExtractRequestIdToContext() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            String requestId = "ds-req-a1b2c3d4";
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK,
                            dashScopeResponseWithRequestId("hello dashscope", requestId)));

            AgentContext ctx = new AgentContext();
            String result = provider.chat("sys", "user", ctx);

            assertThat(result).isEqualTo("hello dashscope");
            assertThat(ctx.getProviderTraceId()).isEqualTo(requestId);
        }

        @Test
        @DisplayName("响应不含 request_id 时 providerTraceId 保持为 null")
        void shouldKeepNullWhenNoRequestId() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK, dashScopeResponse("hello")));

            AgentContext ctx = new AgentContext();
            provider.chat("sys", "user", ctx);

            assertThat(ctx.getProviderTraceId()).isNull();
        }

        @Test
        @DisplayName("context 为 null 时不抛异常")
        void shouldNotThrowWhenContextNull() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK,
                            dashScopeResponseWithRequestId("hello", "req-xxx")));

            // context 为 null 不抛异常
            String result = provider.chat("sys", "user", null);
            assertThat(result).isEqualTo("hello");
        }
    }
}
