package com.njydsz.pmis.agent.engine.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * OpenAI 兼容协议 LLM Provider（P1-4 重构版）
 *
 * <p><b>设计动机</b>：原实现依赖 Spring AI 1.0.0-M6 的 {@code ChatClient}，通过反射调用
 * 极不稳定（API 在 M6/GA 间不兼容）。Spring AI 1.0.0 GA 仅支持 Spring Boot 3.x，
 * 本项目使用 Spring Boot 4.0，待 Spring AI 2.0.0 GA（预计 2026 中）发布才能升级。
 * 现改为直接基于 OpenAI Chat Completions HTTP 协议（POST /v1/chat/completions）的轻量实现，
 * 不再依赖 spring-ai 任何类，兼容所有 OpenAI 协议模型：
 * OpenAI / DeepSeek / 通义千问 / Kimi / 豆包 / Ollama / vLLM / LocalAI 等。
 *
 * <p>继承 {@link AbstractHttpLlmProvider} 获得：
 * <ul>
 *   <li>超时控制（默认 10s）</li>
 *   <li>重试（指数退避 2 次）</li>
 *   <li>TraceId 透传（MDC）</li>
 *   <li>失败降级（mock 兜底）</li>
 * </ul>
 *
 * <p>启用条件：Nacos 配置 {@code pmis.agent.llm.provider=spring-ai-openai}。
 *
 * <p>配置示例：
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: spring-ai-openai
 *       timeout-millis: 8000
 *       max-retries: 2
 *       fallback-to-mock: true
 *   openai-config:
 *     openai:
 *       api-key: sk-xxx
 *       base-url: https://api.openai.com
 *       chat:
 *         options:
 *           model: gpt-4o-mini
 *           temperature: 0.3
 * </pre>
 *
 * <p>Bean name 仍为 {@code springAiLlmProvider}，{@link LlmProviderRouter} 通过
 * {@code name().startsWith("spring-ai")} 路由，保持向后兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-4 重构)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "spring-ai-openai")
public class SpringAiLlmProvider extends AbstractHttpLlmProvider {

    /** 默认连接超时（5s） */
    private static final long CONNECT_TIMEOUT_MS = 5_000L;

    /** API Key */
    private final String apiKey;
    /** 完整请求 URL（base-url + /v1/chat/completions） */
    private final String apiUrl;
    /** 模型名称 */
    private final String model;
    /** 温度（0-2） */
    private final double temperature;
    /** HTTP 客户端 */
    private final HttpClient httpClient;

    /**
     * 生产构造函数（Spring 注入）。
     *
     * @param apiKey       OpenAI API Key
     * @param baseUrl      OpenAI 兼容服务 base URL（如 https://api.openai.com）
     * @param model        模型名称
     * @param temperature  温度
     * @param timeoutMillis 调用超时（ms）
     * @param maxRetries   最大重试次数
     * @param fallback     失败时是否降级到 mock
     */
    public SpringAiLlmProvider(
            @Value("${pmis.openai-config.openai.api-key:}") String apiKey,
            @Value("${pmis.openai-config.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${pmis.openai-config.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${pmis.openai-config.openai.chat.options.temperature:0.3}") double temperature,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis,
            @Value("${pmis.agent.llm.max-retries:2}") int maxRetries,
            @Value("${pmis.agent.llm.fallback-to-mock:true}") boolean fallback) {
        this(apiKey, baseUrl, model, temperature, timeoutMillis, maxRetries, fallback,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.min(timeoutMillis, CONNECT_TIMEOUT_MS)))
                        .build());
    }

    /**
     * 测试用构造函数（注入 HttpClient，便于单测 mock 网络层）。
     *
     * <p>仅用于单元测试，生产环境应使用
     * {@link #SpringAiLlmProvider(String, String, String, double, long, int, boolean)}。
     *
     * @param apiKey       API Key
     * @param baseUrl      base URL
     * @param model        模型名称
     * @param temperature  温度
     * @param timeoutMillis 调用超时
     * @param maxRetries   最大重试次数
     * @param fallback     是否降级到 mock
     * @param httpClient   注入的 HttpClient 实例
     */
    SpringAiLlmProvider(String apiKey, String baseUrl, String model, double temperature,
                       long timeoutMillis, int maxRetries, boolean fallback,
                       HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiUrl = normalizeUrl(baseUrl);
        this.model = model;
        this.temperature = temperature;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbackToMockOnError = fallback;
        this.httpClient = httpClient;
    }

    /**
     * 规范化 base URL，确保以 /v1/chat/completions 结尾。
     */
    private static String normalizeUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1/chat/completions";
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1/chat/completions")) {
            return url;
        }
        if (url.endsWith("/v1")) {
            return url + "/chat/completions";
        }
        return url + "/v1/chat/completions";
    }

    @Override
    public String name() {
        return "spring-ai-openai";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[SpringAiLlm] API Key 未配置, 降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        Callable<String> call = () -> doChat(systemPrompt, userPrompt, context);
        return executeWithGuard(call, context);
    }

    /**
     * 实际执行 OpenAI 兼容协议 HTTP 调用。
     *
     * <p>本方法为 protected，便于子类或测试通过覆盖来注入 mock 响应。
     *
     * <p>P1-4 增强：从响应体中提取 {@code id} 字段（OpenAI 兼容协议的请求 ID），
     * 写入 {@link AgentContext#setProviderTraceId}，用于审计/账单核对。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param context      Agent 上下文（用于写入 providerTraceId；可为 null）
     * @return LLM 返回的文本内容
     * @throws Exception 网络/HTTP/解析异常
     */
    protected String doChat(String systemPrompt, String userPrompt, AgentContext context) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", temperature);

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(msg("system", systemPrompt));
        }
        messages.add(msg("user", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status / 100 != 2) {
            String respBody = response.body();
            String snippet = respBody == null ? "" : respBody.substring(0, Math.min(respBody.length(), 200));
            log.warn("[SpringAiLlm] HTTP {}: {}", status, snippet);
            throw new RuntimeException("LLM HTTP " + status + ": " + snippet);
        }
        String responseBody = response.body();
        // P1-4: 提取 OpenAI 兼容协议的 id 字段写入 AgentContext，用于审计/账单核对
        if (context != null && responseBody != null && !responseBody.isEmpty()) {
            try {
                JSONObject root = JSON.parseObject(responseBody);
                String id = root == null ? null : root.getString("id");
                if (id != null && !id.isEmpty()) {
                    context.setProviderTraceId(id);
                }
            } catch (Exception parseEx) {
                // 响应体非 JSON，忽略（extractContent 会兜底处理）
                log.debug("[SpringAiLlm] 解析响应 id 失败: {}", parseEx.getMessage());
            }
        }
        return extractContent(responseBody);
    }

    /**
     * 从 OpenAI 兼容响应体中提取 assistant 内容。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>标准：{@code choices[0].message.content}</li>
     *   <li>流式片段：{@code choices[0].delta.content}</li>
     * </ul>
     *
     * @param responseBody HTTP 响应体
     * @return assistant 文本内容；为空返回空字符串
     */
    protected String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "";
        }
        JSONObject root = JSON.parseObject(responseBody);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            log.warn("[SpringAiLlm] 响应中无 choices: {}", responseBody);
            return "";
        }
        JSONObject first = choices.getJSONObject(0);
        if (first == null) {
            return "";
        }
        JSONObject message = first.getJSONObject("message");
        if (message != null) {
            return message.getString("content");
        }
        // 兼容流式 delta
        JSONObject delta = first.getJSONObject("delta");
        return delta == null ? "" : delta.getString("content");
    }

    /**
     * 构造 OpenAI 消息对象。
     */
    private JSONObject msg(String role, String content) {
        JSONObject m = new JSONObject();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
