package com.njydsz.pmis.literule.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 LLM 客户端（P2-15 AI 增强）
 *
 * <p>符合 OpenAI Chat Completions 接口规范（POST /v1/chat/completions），
 * 可直接对接 OpenAI / DeepSeek / 通义千问 / Ollama / vLLM / LocalAI 等
 * 所有兼容同一协议的服务。
 *
 * <p>请求体格式：
 * <pre>
 * {
 *   "model": "gpt-4o-mini",
 *   "temperature": 0.2,
 *   "messages": [
 *     {"role": "system", "content": "..."},
 *     {"role": "user", "content": "..."}
 *   ]
 * }
 * </pre>
 *
 * <p>响应体格式：
 * <pre>
 * {
 *   "choices": [
 *     {"message": {"role": "assistant", "content": "..."}}
 *   ]
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class OpenAICompatibleLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleLLMClient.class);

    /** 提供方标识 */
    public static final String PROVIDER = "OPENAI_COMPATIBLE";

    private final LiteRuleProperties.Ai config;
    private final HttpClient httpClient;

    public OpenAICompatibleLLMClient(LiteRuleProperties.Ai config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(config.getLlmTimeoutMs(), 1000)))
                .build();
    }

    /**
     * 测试用构造函数（注入 HttpClient，便于单测 mock 网络层）
     *
     * <p>仅用于单元测试，生产环境应使用 {@link #OpenAICompatibleLLMClient(LiteRuleProperties.Ai)}。
     *
     * @param config     配置
     * @param httpClient 注入的 HttpClient 实例
     * @since 1.5.0
     */
    OpenAICompatibleLLMClient(LiteRuleProperties.Ai config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, Map<String, Object> options) {
        List<Map<String, String>> messages = new ArrayList<>(2);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(msg("system", systemPrompt));
        }
        messages.add(msg("user", userPrompt == null ? "" : userPrompt));
        return chatWithHistory(messages, options);
    }

    @Override
    public String chatWithHistory(List<Map<String, String>> messages, Map<String, Object> options) {
        if (config.getLlmApiKey() == null || config.getLlmApiKey().isEmpty()) {
            throw new LLMException(PROVIDER, "LLM API Key 未配置（pmis.literule.ai.llm-api-key）");
        }
        if (config.getLlmApiUrl() == null || config.getLlmApiUrl().isEmpty()) {
            throw new LLMException(PROVIDER, "LLM API URL 未配置（pmis.literule.ai.llm-api-url）");
        }

        JSONObject body = new JSONObject();
        body.put("model", config.getLlmModel());
        body.put("temperature", config.getLlmTemperature());
        body.put("messages", messages);

        // 透传 options 中可识别的字段
        if (options != null) {
            Object maxTokens = options.get("maxTokens");
            if (maxTokens instanceof Number) {
                body.put("max_tokens", ((Number) maxTokens).intValue());
            }
            Object topP = options.get("topP");
            if (topP instanceof Number) {
                body.put("top_p", ((Number) topP).doubleValue());
            }
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getLlmApiUrl()))
                    .timeout(Duration.ofMillis(config.getLlmTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getLlmApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new LLMException(PROVIDER, "无效的 LLM API URL: " + config.getLlmApiUrl(), e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpConnectTimeoutException e) {
            throw new LLMException(PROVIDER, "LLM 连接超时: " + e.getMessage(), e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LLMException(PROVIDER, "LLM 响应超时: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new LLMException(PROVIDER, "LLM 网络异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException(PROVIDER, "LLM 调用被中断", e);
        }

        int status = response.statusCode();
        if (status / 100 != 2) {
            String body0 = response.body();
            String snippet = body0 == null ? "" : body0.substring(0, Math.min(body0.length(), 200));
            log.warn("[LLM] 调用失败 status={} body={}", status, snippet);
            throw new LLMException(PROVIDER, status, "LLM 返回非 2xx 状态: " + status);
        }

        return extractContent(response.body());
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return config == null ? "" : config.getLlmModel();
    }

    /**
     * 从 OpenAI 兼容响应中提取 content 字段
     */
    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "";
        }
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("[LLM] 响应中无 choices: {}", responseBody);
                return "";
            }
            JSONObject first = choices.getJSONObject(0);
            if (first == null) {
                return "";
            }
            JSONObject message = first.getJSONObject("message");
            if (message == null) {
                // 兼容某些代理返回 delta 字段
                JSONObject delta = first.getJSONObject("delta");
                if (delta != null) {
                    return delta.getString("content");
                }
                return "";
            }
            return message.getString("content");
        } catch (Exception e) {
            log.warn("[LLM] 响应解析失败: {}", e.getMessage());
            throw new LLMException(PROVIDER, "LLM 响应解析失败: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> msg(String role, String content) {
        Map<String, String> m = new HashMap<>(2);
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
