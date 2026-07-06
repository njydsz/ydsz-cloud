package com.njydsz.pmis.literule.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAICompatibleLLMClient 单元测试
 *
 * <p>使用 Mockito mock {@link HttpClient} 与 {@link HttpResponse}，
 * 覆盖请求构造、响应解析、超时分类、非 2xx 处理、delta 兼容、options 透传等核心路径。
 * 不依赖真实网络。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("OpenAI 兼容 LLM 客户端测试")
@SuppressWarnings("unchecked")
class OpenAICompatibleLLMClientTest {

    private LiteRuleProperties.Ai config;
    private HttpClient httpClient;
    private HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);

    @BeforeEach
    void setUp() {
        config = new LiteRuleProperties.Ai();
        config.setEnabled(true);
        config.setLlmClient("OPENAI_COMPATIBLE");
        config.setLlmApiUrl("https://api.openai.com/v1/chat/completions");
        config.setLlmApiKey("sk-test-key");
        config.setLlmModel("gpt-4o-mini");
        config.setLlmTimeoutMs(15000);
        config.setLlmTemperature(0.2);

        httpClient = mock(HttpClient.class);
        response = (HttpResponse<String>) mock(HttpResponse.class);
    }

    // ============ 正常路径 ============

    @Test
    @DisplayName("chat 正常调用应提取 message.content 字段")
    void chatShouldExtractContentFromMessage() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("你好，世界"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        String result = client.chat("你是助手", "打招呼", null);

        assertEquals("你好，世界", result);
    }

    @Test
    @DisplayName("chat 无 systemPrompt 应只发送 user 消息")
    void chatWithoutSystemPromptShouldOnlySendUserMessage() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat(null, "hi", null);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String body = extractBody(captor.getValue());
        JSONObject json = JSON.parseObject(body);
        // 没有 system，只有一条 user
        assertEquals(1, json.getJSONArray("messages").size());
        assertEquals("user", json.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("hi", json.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    @DisplayName("chatWithHistory 正常调用应透传消息列表")
    void chatWithHistoryShouldPassThroughMessages() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("回复"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        List<Map<String, String>> messages = List.of(
                msg("system", "你是助手"),
                msg("user", "第一轮"),
                msg("assistant", "回复1"),
                msg("user", "第二轮"));
        String result = client.chatWithHistory(messages, null);

        assertEquals("回复", result);
    }

    @Test
    @DisplayName("options 中的 maxTokens 和 topP 应透传到请求体")
    void shouldPassThroughMaxTokensAndTopP() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        Map<String, Object> options = new HashMap<>();
        options.put("maxTokens", 512);
        options.put("topP", 0.9);

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat("sys", "u", options);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        JSONObject body = JSON.parseObject(extractBody(captor.getValue()));
        assertEquals(512, body.getIntValue("max_tokens"));
        assertEquals(0.9, body.getDouble("top_p").doubleValue(), 0.0001);
    }

    @Test
    @DisplayName("请求应包含 Authorization Bearer 头与 Content-Type")
    void shouldIncludeAuthorizationAndContentTypeHeaders() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat("sys", "u", null);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest req = captor.getValue();
        assertEquals("Bearer sk-test-key", req.headers().firstValue("Authorization").orElse(""));
        assertEquals("application/json", req.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("provider 与 model 应返回正确标识")
    void providerAndModelShouldReturnConfigured() {
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals(OpenAICompatibleLLMClient.PROVIDER, client.provider());
        assertEquals("gpt-4o-mini", client.model());
    }

    @Test
    @DisplayName("model 为 null 时应返回空串（防御）")
    void modelShouldReturnEmptyWhenConfigNull() {
        // config 字段虽不能为 null（构造校验由调用方保证），但 model 字段可能为空
        config.setLlmModel("");
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals("", client.model());
    }

    // ============ 响应解析变体 ============

    @Test
    @DisplayName("响应 message 为空时应回退到 delta.content")
    void shouldFallbackToDeltaWhenMessageMissing() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        // 部分代理返回 delta 而非 message
        when(response.body()).thenReturn(
                "{\"choices\":[{\"delta\":{\"content\":\"delta-content\"}}]}");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals("delta-content", client.chat("s", "u", null));
    }

    @Test
    @DisplayName("响应 body 为空应返回空串而非抛异常")
    void shouldReturnEmptyWhenBodyNull() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(null);

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals("", client.chat("s", "u", null));
    }

    @Test
    @DisplayName("响应 body 为空串应返回空串")
    void shouldReturnEmptyWhenBodyEmpty() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals("", client.chat("s", "u", null));
    }

    @Test
    @DisplayName("响应无 choices 数组应返回空串")
    void shouldReturnEmptyWhenChoicesMissing() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"chat_1\"}");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals("", client.chat("s", "u", null));
    }

    @Test
    @DisplayName("响应 choices 为空数组应返回空串")
    void shouldReturnEmptyWhenChoicesEmpty() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[]}");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertEquals("", client.chat("s", "u", null));
    }

    @Test
    @DisplayName("响应 message.content 为 null 应返回 null")
    void shouldReturnNullWhenContentNull() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        // content 为 null，extractContent 返回 null（getString 对 null 字段返回 null）
        assertEquals(null, client.chat("s", "u", null));
    }

    @Test
    @DisplayName("响应 JSON 格式错误应抛 LLMException")
    void shouldThrowOnMalformedJson() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not a json {{{");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("LLM 响应解析失败"));
        assertEquals(OpenAICompatibleLLMClient.PROVIDER, e.getProvider());
    }

    // ============ 配置校验 ============

    @Test
    @DisplayName("API Key 未配置应抛 LLMException")
    void shouldThrowWhenApiKeyMissing() {
        config.setLlmApiKey("");
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("API Key"));
    }

    @Test
    @DisplayName("API Key 为 null 应抛 LLMException")
    void shouldThrowWhenApiKeyNull() {
        config.setLlmApiKey(null);
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        assertThrows(LLMException.class, () -> client.chat("s", "u", null));
    }

    @Test
    @DisplayName("API URL 未配置应抛 LLMException")
    void shouldThrowWhenApiUrlMissing() {
        config.setLlmApiUrl("");
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("API URL"));
    }

    @Test
    @DisplayName("无效的 API URL 应抛 LLMException（非 IllegalArgumentException）")
    void shouldThrowOnInvalidApiUrl() {
        config.setLlmApiUrl(":::not-a-url:::");
        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("无效的 LLM API URL"));
    }

    // ============ HTTP 错误状态 ============

    @Test
    @DisplayName("非 2xx 状态码应抛带 statusCode 的 LLMException")
    void shouldThrowOnNon2xxStatus() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":{\"message\":\"invalid api key\"}}");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertEquals(401, e.getStatusCode());
        assertTrue(e.getMessage().contains("401"));
    }

    @Test
    @DisplayName("500 服务端错误应抛 LLMException 含 statusCode=500")
    void shouldThrowOn500ServerError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("internal error");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertEquals(500, e.getStatusCode());
    }

    @Test
    @DisplayName("非 2xx 且 body 超过 200 字符应截断到 200 字符后写日志")
    void shouldTruncateLongErrorBodyInLog() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(429);
        String longBody = "x".repeat(500);
        when(response.body()).thenReturn(longBody);

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        // 不会抛 StringIndexOutOfBoundsException，仅记录 warn 日志
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertEquals(429, e.getStatusCode());
    }

    @Test
    @DisplayName("非 2xx 且 body 为 null 应安全处理")
    void shouldHandleNullBodyOnNon2xx() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(502);
        when(response.body()).thenReturn(null);

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertEquals(502, e.getStatusCode());
    }

    // ============ 超时与网络异常分类 ============

    @Test
    @DisplayName("HttpConnectTimeoutException 应分类为连接超时异常")
    void shouldClassifyConnectTimeout() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpConnectTimeoutException("connect timed out"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("LLM 连接超时"));
    }

    @Test
    @DisplayName("HttpTimeoutException（非连接超时）应分类为响应超时")
    void shouldClassifyResponseTimeout() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("read timed out") {
                    // HttpTimeoutException 是抽象类，需匿名子类
                });

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("LLM 响应超时"));
    }

    @Test
    @DisplayName("IOException 应分类为网络异常")
    void shouldClassifyIOException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("LLM 网络异常"));
        assertTrue(e.getMessage().contains("connection reset"));
    }

    @Test
    @DisplayName("InterruptedException 应恢复中断标志并抛 LLMException")
    void shouldRestoreInterruptFlagOnInterruptedException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        LLMException e = assertThrows(LLMException.class, () -> client.chat("s", "u", null));
        assertTrue(e.getMessage().contains("LLM 调用被中断"));
        assertTrue(Thread.currentThread().isInterrupted(), "中断标志应被恢复");
        // 清理中断状态，避免影响后续测试
        Thread.interrupted();
    }

    // ============ 选项边界 ============

    @Test
    @DisplayName("options 为 null 应正常调用（不透传额外参数）")
    void shouldHandleNullOptions() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat("s", "u", null);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        JSONObject body = JSON.parseObject(extractBody(captor.getValue()));
        assertFalse(body.containsKey("max_tokens"));
        assertFalse(body.containsKey("top_p"));
    }

    @Test
    @DisplayName("options 中 maxTokens 为非 Number 类型应忽略")
    void shouldIgnoreNonNumberMaxTokens() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        Map<String, Object> options = new HashMap<>();
        options.put("maxTokens", "512"); // 字符串而非 Number
        options.put("topP", "0.9");

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat("s", "u", options);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        JSONObject body = JSON.parseObject(extractBody(captor.getValue()));
        assertFalse(body.containsKey("max_tokens"));
        assertFalse(body.containsKey("top_p"));
    }

    @Test
    @DisplayName("userPrompt 为 null 应发送空字符串")
    void shouldSendEmptyStringWhenUserPromptNull() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat("sys", null, null);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        JSONObject body = JSON.parseObject(extractBody(captor.getValue()));
        // system 消息 + user 消息（user content 为空串）
        assertEquals(2, body.getJSONArray("messages").size());
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("sys", body.getJSONArray("messages").getJSONObject(0).getString("content"));
        assertEquals("user", body.getJSONArray("messages").getJSONObject(1).getString("role"));
        assertEquals("", body.getJSONArray("messages").getJSONObject(1).getString("content"));
    }

    @Test
    @DisplayName("请求体应包含 model、temperature、messages 字段")
    void shouldIncludeModelTemperatureMessagesInBody() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildResponseBody("ok"));

        OpenAICompatibleLLMClient client = new OpenAICompatibleLLMClient(config, httpClient);
        client.chat("sys", "u", null);

        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        JSONObject body = JSON.parseObject(extractBody(captor.getValue()));
        assertEquals("gpt-4o-mini", body.getString("model"));
        assertEquals(0.2, body.getDouble("temperature").doubleValue(), 0.0001);
        assertTrue(body.containsKey("messages"));
    }

    // ============ 辅助方法 ============

    /** 构造 OpenAI 标准响应体 */
    private static String buildResponseBody(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}";
    }

    /** 从 HttpRequest 的 BodyPublisher 同步读取 body 字符串 */
    private static String extractBody(HttpRequest request) {
        if (request.bodyPublisher().isEmpty()) {
            return "";
        }
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().get();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        publisher.subscribe(new BodyCollector(baos, latch));
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 同步收集 BodyPublisher 字节的具名 Subscriber */
    private static final class BodyCollector
            implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
        private final java.io.ByteArrayOutputStream baos;
        private final java.util.concurrent.CountDownLatch latch;

        BodyCollector(java.io.ByteArrayOutputStream baos, java.util.concurrent.CountDownLatch latch) {
            this.baos = baos;
            this.latch = latch;
        }

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.nio.ByteBuffer b) {
            byte[] bytes = new byte[b.remaining()];
            b.get(bytes);
            baos.writeBytes(bytes);
        }

        @Override
        public void onError(Throwable t) {
            latch.countDown();
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }
    }

    private static Map<String, String> msg(String role, String content) {
        Map<String, String> m = new HashMap<>(2);
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
