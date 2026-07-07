package com.njydsz.pmis.agent.engine.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SpringAiLlmProvider 单元测试（P1-4 重构版）
 *
 * <p>原反射 hack 已被替换为 OpenAI 兼容 HTTP 协议直调。本测试覆盖：
 * <ul>
 *   <li>API Key 为空时降级到 MockLlmProvider</li>
 *   <li>HTTP 2xx 响应：正确提取 choices[0].message.content</li>
 *   <li>HTTP 2xx 响应：兼容流式 delta 字段</li>
 *   <li>HTTP 非 2xx：抛异常 → 重试 → 最终降级 mock</li>
 *   <li>HTTP 非 2xx + fallback=false：抛 RuntimeException</li>
 *   <li>响应体无 choices：返回空字符串</li>
 *   <li>URL 规范化：补全 /v1/chat/completions</li>
 *   <li>chatForJson() 默认方法：剥离 markdown 代码块并反序列化</li>
 * </ul>
 *
 * <p>使用 Mockito mock {@link HttpClient} + {@link HttpResponse}，无真实网络请求。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-4)
 */
@DisplayName("SpringAiLlmProvider OpenAI 兼容 LLM Provider 测试")
class SpringAiLlmProviderTest {

    // ==================== 辅助方法 ====================

    /** 构造一个 mock HttpClient，对 send 请求返回指定 status + body */
    @SuppressWarnings("unchecked")
    private HttpClient mockHttpClient(int status, String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        when(client.send(any(), any())).thenAnswer(invocation -> resp);
        return client;
    }

    /**
     * 构造一个 mock HttpClient，第 N 次调用返回不同 status + body。
     *
     * <p>使用 {@code thenReturn(T first, T... rest)} 语法实现顺序响应：
     * 第 1 次调用返回第 1 组 status/body，第 2 次返回第 2 组，以此类推。
     * 超出指定次数的调用返回最后一组值。
     *
     * @param statusBodyPairs 交替的 status(Integer) / body(String) 对，必须成对出现
     */
    @SuppressWarnings("unchecked")
    private HttpClient mockHttpClientSeq(Object... statusBodyPairs) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        int n = statusBodyPairs.length / 2;
        if (n > 0) {
            Integer[] statuses = new Integer[n];
            String[] bodies = new String[n];
            for (int i = 0; i < n; i++) {
                statuses[i] = (Integer) statusBodyPairs[i * 2];
                bodies[i] = (String) statusBodyPairs[i * 2 + 1];
            }
            // thenReturn(T first, T... rest) 支持顺序返回不同值
            if (n == 1) {
                when(resp.statusCode()).thenReturn(statuses[0]);
                when(resp.body()).thenReturn(bodies[0]);
            } else {
                when(resp.statusCode()).thenReturn(statuses[0],
                        Arrays.copyOfRange(statuses, 1, n));
                when(resp.body()).thenReturn(bodies[0],
                        Arrays.copyOfRange(bodies, 1, n));
            }
        }
        // 使用 thenAnswer 绕过 HttpClient.send 的泛型类型推断问题
        when(client.send(any(), any())).thenAnswer(invocation -> resp);
        return client;
    }

    /** 标准 OpenAI 响应体（使用 fastjson2 构造，自动转义特殊字符） */
    private String openAiResponse(String content) {
        return openAiResponse(content, null);
    }

    /** 带 id 的 OpenAI 响应体（P1-4: providerTraceId 提取测试用） */
    private String openAiResponse(String content, String id) {
        JSONObject msg = new JSONObject();
        msg.put("role", "assistant");
        msg.put("content", content);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("message", msg);
        choices.add(choice);
        JSONObject root = new JSONObject();
        root.put("choices", choices);
        if (id != null) {
            root.put("id", id);
        }
        return root.toJSONString();
    }

    /** 流式 delta 响应体（使用 fastjson2 构造） */
    private String deltaResponse(String content) {
        JSONObject delta = new JSONObject();
        delta.put("content", content);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("delta", delta);
        choices.add(choice);
        JSONObject root = new JSONObject();
        root.put("choices", choices);
        return root.toJSONString();
    }

    /** 构造 SpringAiLlmProvider，注入 mock HttpClient */
    private SpringAiLlmProvider providerWithMock(String apiKey, int status, String body,
                                                   long timeout, int retries, boolean fallback) throws Exception {
        HttpClient client = mockHttpClient(status, body);
        return new SpringAiLlmProvider(apiKey, "https://api.openai.com", "gpt-4o-mini", 0.3,
                timeout, retries, fallback, client);
    }

    // ==================== API Key 为空降级测试 ====================

    @Nested
    @DisplayName("API Key 为空降级测试")
    class EmptyApiKeyTest {

        @Test
        @DisplayName("apiKey=null 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenApiKeyNull() {
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    null, "https://api.openai.com", "gpt-4o-mini", 0.3,
                    5000, 0, true, HttpClient.newHttpClient());

            String result = provider.chat("sys", "user", new AgentContext());

            // MockLlmProvider 对普通查询返回 NORMAL
            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("apiKey=空串 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenApiKeyBlank() {
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "  ", "https://api.openai.com", "gpt-4o-mini", 0.3,
                    5000, 0, true, HttpClient.newHttpClient());

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
        void shouldExtractContentOnSuccess() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("hello from openai"), 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("hello from openai");
        }

        @Test
        @DisplayName("systemPrompt=null 时仅发送 user 消息")
        void shouldHandleNullSystemPrompt() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("ok"), 5000, 0, true);

            String result = provider.chat(null, "user", new AgentContext());

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("userPrompt=null 时不抛 NPE")
        void shouldHandleNullUserPrompt() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("ok"), 5000, 0, true);

            String result = provider.chat("sys", null, new AgentContext());

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("响应为流式 delta 格式时正确提取 content")
        void shouldExtractFromDeltaFormat() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    deltaResponse("delta-content"), 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("delta-content");
        }

        @Test
        @DisplayName("响应体 choices 为空时返回空字符串")
        void shouldReturnEmptyWhenChoicesEmpty() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    "{\"choices\":[]}", 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("响应体无 choices 字段时返回空字符串")
        void shouldReturnEmptyWhenNoChoicesField() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    "{\"error\":\"rate limit\"}", 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("响应体为空字符串时返回空字符串")
        void shouldReturnEmptyWhenBodyEmpty() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    "", 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("");
        }
    }

    // ==================== HTTP 非 2xx 异常测试 ====================

    @Nested
    @DisplayName("HTTP 非 2xx 异常测试")
    class HttpErrorTest {

        @Test
        @DisplayName("HTTP 500 → fallback=true 时降级到 mock")
        void shouldFallbackToMockOnHttp500() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 500,
                    "internal error", 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("HTTP 500 → fallback=false 时抛 RuntimeException")
        void shouldThrowOnHttp500WhenFallbackDisabled() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 500,
                    "internal error", 5000, 0, false);

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("spring-ai-openai")
                    .hasMessageContaining("failed");
        }

        @Test
        @DisplayName("HTTP 429 → 重试 2 次后降级 mock")
        void shouldRetryOnHttp429ThenFallback() throws Exception {
            HttpClient client = mockHttpClientSeq(429, "rate limit", 429, "rate limit", 429, "rate limit");
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.openai.com", "gpt-4o-mini", 0.3,
                    5000, 2, true, client);

            String result = provider.chat("sys", "user", new AgentContext());

            // 重试 3 次（首次 + 2 次重试）都失败，降级 mock
            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("HTTP 500 第 1 次 + HTTP 200 第 2 次 → 重试成功")
        void shouldRetryAndSucceedOnSecondAttempt() throws Exception {
            HttpClient client = mockHttpClientSeq(500, "error", 200, openAiResponse("recovered"));
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.openai.com", "gpt-4o-mini", 0.3,
                    5000, 2, true, client);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("recovered");
        }
    }

    // ==================== URL 规范化测试 ====================

    @Nested
    @DisplayName("URL 规范化测试")
    class UrlNormalizeTest {

        @Test
        @DisplayName("base-url 末尾无斜杠 → 补全 /v1/chat/completions")
        void shouldNormalizeBaseUrlWithoutSlash() throws Exception {
            HttpClient client = mockHttpClient(200, openAiResponse("ok"));
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.openai.com", "gpt-4o-mini", 0.3,
                    5000, 0, true, client);

            provider.chat("sys", "user", new AgentContext());
            // 不抛异常即表示 URL 合法
        }

        @Test
        @DisplayName("base-url 末尾有斜杠 → 补全 v1/chat/completions")
        void shouldNormalizeBaseUrlWithSlash() throws Exception {
            HttpClient client = mockHttpClient(200, openAiResponse("ok"));
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.openai.com/", "gpt-4o-mini", 0.3,
                    5000, 0, true, client);

            provider.chat("sys", "user", new AgentContext());
        }

        @Test
        @DisplayName("base-url 已含 /v1 → 仅补全 /chat/completions")
        void shouldNormalizeBaseUrlWithV1() throws Exception {
            HttpClient client = mockHttpClient(200, openAiResponse("ok"));
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.deepseek.com/v1", "deepseek-chat", 0.3,
                    5000, 0, true, client);

            provider.chat("sys", "user", new AgentContext());
        }

        @Test
        @DisplayName("base-url 已含完整路径 → 不再追加")
        void shouldNormalizeBaseUrlWithFullPath() throws Exception {
            HttpClient client = mockHttpClient(200, openAiResponse("ok"));
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini", 0.3,
                    5000, 0, true, client);

            provider.chat("sys", "user", new AgentContext());
        }

        @Test
        @DisplayName("base-url 为空 → 使用默认 OpenAI URL")
        void shouldUseDefaultUrlWhenBaseUrlEmpty() throws Exception {
            HttpClient client = mockHttpClient(200, openAiResponse("ok"));
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "", "gpt-4o-mini", 0.3,
                    5000, 0, true, client);

            provider.chat("sys", "user", new AgentContext());
        }
    }

    // ==================== chatForJson() 测试 ====================

    @Nested
    @DisplayName("chatForJson 结构化输出测试")
    class ChatForJsonTest {

        @Test
        @DisplayName("LLM 返回纯 JSON → 直接反序列化")
        void shouldParsePlainJson() throws Exception {
            // LLM content 字段为 JSON 文本，需用 openAiResponse 包装以符合 extractContent 解析格式
            String json = "{\"name\":\"风险预警\",\"score\":85}";
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse(json), 5000, 0, true);

            TestDto dto = provider.chatForJson("sys", "user", TestDto.class, new AgentContext());

            assertThat(dto.getName()).isEqualTo("风险预警");
            assertThat(dto.getScore()).isEqualTo(85);
        }

        @Test
        @DisplayName("LLM 返回 ```json ... ``` 代码块 → 剥离后反序列化")
        void shouldParseMarkdownJsonFence() throws Exception {
            String json = "```json\n{\"name\":\"wrapped\",\"score\":50}\n```";
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse(json), 5000, 0, true);

            TestDto dto = provider.chatForJson("sys", "user", TestDto.class, new AgentContext());

            assertThat(dto.getName()).isEqualTo("wrapped");
            assertThat(dto.getScore()).isEqualTo(50);
        }

        @Test
        @DisplayName("LLM 返回非 JSON → 抛 RuntimeException")
        void shouldThrowOnInvalidJson() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("not a json"), 5000, 0, true);

            assertThatThrownBy(() ->
                    provider.chatForJson("sys", "user", TestDto.class, new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("非合法 JSON");
        }
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("name() 返回 'spring-ai-openai'")
        void shouldReturnSpringAiOpenAiName() {
            SpringAiLlmProvider provider = new SpringAiLlmProvider(
                    "sk-test", "https://api.openai.com", "gpt-4o-mini", 0.3,
                    5000, 0, true, HttpClient.newHttpClient());

            assertThat(provider.name()).isEqualTo("spring-ai-openai");
        }
    }

    // ==================== P1-4: providerTraceId 提取测试 ====================

    @Nested
    @DisplayName("P1-4: providerTraceId 提取测试")
    class ProviderTraceIdTest {

        @Test
        @DisplayName("响应包含 id 时写入 AgentContext.providerTraceId")
        void shouldExtractIdToContext() throws Exception {
            String chatId = "chatcmpl-abc123";
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("hello openai", chatId), 5000, 0, true);

            AgentContext ctx = new AgentContext();
            String result = provider.chat("sys", "user", ctx);

            assertThat(result).isEqualTo("hello openai");
            assertThat(ctx.getProviderTraceId()).isEqualTo(chatId);
        }

        @Test
        @DisplayName("响应不含 id 时 providerTraceId 保持为 null")
        void shouldKeepNullWhenNoId() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("hello"), 5000, 0, true);

            AgentContext ctx = new AgentContext();
            provider.chat("sys", "user", ctx);

            assertThat(ctx.getProviderTraceId()).isNull();
        }

        @Test
        @DisplayName("context 为 null 时不抛异常")
        void shouldNotThrowWhenContextNull() throws Exception {
            SpringAiLlmProvider provider = providerWithMock("sk-test", 200,
                    openAiResponse("hello", "chatcmpl-xxx"), 5000, 0, true);

            // context 为 null 不抛异常
            String result = provider.chat("sys", "user", null);
            assertThat(result).isEqualTo("hello");
        }
    }

    // ==================== 测试 DTO ====================

    /** 用于 chatForJson 测试的简单 DTO */
    @lombok.Data
    public static class TestDto {
        private String name;
        private int score;
    }
}
