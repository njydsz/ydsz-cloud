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
 * QianfanLlmProvider 单元测试（P0-5 修复验证）
 *
 * <p>使用 Spring Test 的 {@link MockRestServiceServer} 模拟千帆 HTTP 响应，
 * 无真实网络请求。覆盖：
 * <ul>
 *   <li>API Key 为空时降级到 MockLlmProvider</li>
 *   <li>HTTP 2xx 响应：正确提取 choices[0].message.content</li>
 *   <li>P0-5: HTTP 401 error_code=110 - 抛出带错误码的异常</li>
 *   <li>P0-5: HTTP 500 - fallback=true 时降级到 mock</li>
 *   <li>P0-5: HTTP 429 - 重试 2 次后降级 mock</li>
 *   <li>P0-5: HTTP 500 第 1 次 + HTTP 200 第 2 次 → 重试成功</li>
 *   <li>P0-5: 非 JSON 错误响应体也能解析（不抛异常）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@DisplayName("QianfanLlmProvider 百度千帆 LLM Provider 测试")
class QianfanLlmProviderTest {

    /** 测试用 base URL（MockRestServiceServer 需要绝对 URI 匹配） */
    private static final String BASE_URL = "https://qianfan.baidubce.com";
    /** 完整请求 URL */
    private static final String CHAT_URL = BASE_URL + "/v2/chat/completions";

    /** 当前测试用 Provider 实例（每个测试方法重置） */
    private QianfanLlmProvider provider;

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
        provider = new QianfanLlmProvider(apiKey, "ernie-3.5-8k", 5000, maxRetries, fallback,
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

    /** 标准千帆响应体（choices[0].message.content） */
    private String qianfanResponse(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}";
    }

    /** 千帆标准错误响应体（error_code 数字 + error_msg） */
    private String qianfanError(int errorCode, String errorMsg) {
        return "{\"error_code\":" + errorCode + ",\"error_msg\":\"" + errorMsg + "\"}";
    }

    // ==================== API Key 为空降级测试 ====================

    @Nested
    @DisplayName("API Key 为空降级测试")
    class EmptyApiKeyTest {

        @Test
        @DisplayName("apiKey=null 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenApiKeyNull() {
            provider = new QianfanLlmProvider(null, "ernie-3.5-8k", 5000, 0, true,
                    RestClient.builder().build());

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("apiKey=空串 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenApiKeyEmpty() {
            provider = new QianfanLlmProvider("", "ernie-3.5-8k", 5000, 0, true,
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
                    .andRespond(jsonResp(HttpStatus.OK, qianfanResponse("hello qianfan")));

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("hello qianfan");
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
        @DisplayName("HTTP 401 error_code=110 - 抛出带错误码的异常")
        void shouldThrowWithErrorCodeOn401() {
            MockRestServiceServer server = setupProvider("sk-invalid", 0, false);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.UNAUTHORIZED,
                            qianfanError(110, "Access token invalid")));

            // fallback=false, maxRetries=0：直接抛 RuntimeException("LLM qianfan failed after 1 attempts", cause)
            // cause 为 onStatus 抛出的 "Qianfan HTTP 401 [110]: Access token invalid"
            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("qianfan")
                    .hasMessageContaining("failed")
                    .hasCauseInstanceOf(RuntimeException.class)
                    .cause()
                    .hasMessageContaining("401")
                    .hasMessageContaining("110")
                    .hasMessageContaining("Access token invalid");
        }

        @Test
        @DisplayName("HTTP 500 - fallback=true 时降级到 mock")
        void shouldFallbackToMockOn500() {
            MockRestServiceServer server = setupProvider("sk-test", 0, true);
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.INTERNAL_SERVER_ERROR,
                            qianfanError(500, "internal error")));

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
                            qianfanError(500, "internal error")));

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("qianfan")
                    .hasMessageContaining("failed")
                    .cause()
                    .hasMessageContaining("500")
                    .hasMessageContaining("internal error");
        }

        @Test
        @DisplayName("HTTP 429 - 重试 2 次后降级 mock")
        void shouldRetryOn429ThenFallback() {
            MockRestServiceServer server = setupProvider("sk-test", 2, true);
            String errBody = qianfanError(4, "qps request limit reached");
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
                            qianfanError(500, "transient")));
            server.expect(requestTo(CHAT_URL))
                    .andRespond(jsonResp(HttpStatus.OK, qianfanResponse("recovered")));

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
                            qianfanError(4000, "invalid request body")));

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .cause()
                    .hasMessageContaining("400")
                    .hasMessageContaining("4000")
                    .hasMessageContaining("invalid request body");
        }
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("name() 返回 'qianfan'")
        void shouldReturnQianfanName() {
            provider = new QianfanLlmProvider("sk-test", "ernie-3.5-8k", 5000, 0, true,
                    RestClient.builder().build());

            assertThat(provider.name()).isEqualTo("qianfan");
        }
    }
}
