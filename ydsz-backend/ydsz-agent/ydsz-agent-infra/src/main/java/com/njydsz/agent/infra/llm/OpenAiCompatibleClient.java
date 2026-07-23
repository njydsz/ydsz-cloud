package com.njydsz.agent.infra.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.object.YdszJsonArray;
import com.njydsz.common.json.object.YdszJsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * OpenAI 兼容 LLM 客户端实现
 *
 * <p>覆盖所有兼容 OpenAI Chat Completions API 的 Provider：
 * <ul>
 *   <li>OpenAI（GPT-4o / GPT-4o-mini）</li>
 *   <li>DeepSeek（deepseek-chat / deepseek-coder）</li>
 *   <li>通义千问（qwen-plus / qwen-max）</li>
 *   <li>Moonshot（moonshot-v1-8k / moonshot-v1-32k）</li>
 *   <li>智谱 GLM（glm-4 / glm-4-flash）</li>
 *   <li>Ollama 本地模型（llama3 / qwen2）</li>
 * </ul>
 *
 * <h3>同步调用</h3>
 * <p>使用 {@link RestClient} 发送 POST 请求，解析 JSON 响应。
 *
 * <h3>流式调用</h3>
 * <p>使用 {@link WebClient} 接收 SSE 流，逐行解析 {@code data:} 前缀的 JSON 片段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final int timeoutSeconds;
    private final RestClient restClient;
    private final WebClient webClient;

    public OpenAiCompatibleClient(String provider, String baseUrl, String apiKey, int timeoutSeconds) {
        this.provider = provider;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        ConnectionProvider connectionProvider = ConnectionProvider.builder("agent-llm-" + this.provider)
                .maxConnections(100)
                .maxIdleTime(Duration.ofSeconds(30))
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(this.timeoutSeconds));
        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Map<String, Object> requestBody = buildRequestBody(request, false);
        try {
            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .body(YdszJson.toJson(requestBody))
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseJson);
        } catch (LlmException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            LlmException.ErrorType errorType = mapHttpError(e.getStatusCode().value());
            log.error("[LLM-{}] 同步调用 HTTP 错误: status={}", provider, e.getStatusCode().value());
            throw new LlmException("LLM 调用失败 (HTTP " + e.getStatusCode().value() + ")",
                    errorType, e);
        } catch (ResourceAccessException e) {
            log.error("[LLM-{}] 同步调用网络异常: {}", provider, e.getMessage());
            throw new LlmException("LLM 网络超时或连接拒绝: " + e.getMessage(),
                    LlmException.ErrorType.NETWORK_TIMEOUT, e);
        } catch (Exception e) {
            log.error("[LLM-{}] 同步调用失败: {}", provider, e.getMessage(), e);
            throw new LlmException("LLM 调用失败: " + e.getMessage(),
                    LlmException.ErrorType.PROVIDER_ERROR, e);
        }
    }

    @Override
    public void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
        Map<String, Object> requestBody = buildRequestBody(request, true);
        try {
            webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(YdszJson.toJson(requestBody))
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(line -> {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equals(data)) {
                                return;
                            }
                            ChatChunk chunk = parseChunk(data);
                            if (chunk != null) {
                                chunkConsumer.accept(chunk);
                            }
                        }
                    })
                    .doOnError(e -> log.error("[LLM-{}] 流式调用失败: {}", provider, e.getMessage(), e))
                    .blockLast();
            chunkConsumer.accept(ChatChunk.finish("", request.getModel(), "stop", null));
        } catch (LlmException e) {
            throw e;
        } catch (WebClientResponseException e) {
            LlmException.ErrorType errorType = mapHttpError(e.getStatusCode().value());
            log.error("[LLM-{}] 流式调用 HTTP 错误: status={}", provider, e.getStatusCode().value());
            throw new LlmException("LLM 流式调用失败 (HTTP " + e.getStatusCode().value() + ")",
                    errorType, e);
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                log.error("[LLM-{}] 流式调用超时: {}", provider, e.getMessage());
                throw new LlmException("LLM 流式调用超时",
                        LlmException.ErrorType.NETWORK_TIMEOUT, e);
            }
            log.error("[LLM-{}] 流式调用异常: {}", provider, e.getMessage(), e);
            throw new LlmException("LLM 流式调用失败: " + e.getMessage(),
                    LlmException.ErrorType.PROVIDER_ERROR, e);
        }
    }

    @Override
    public boolean supports(String modelId) {
        return modelId != null && !modelId.isBlank();
    }

    @Override
    public String getProvider() {
        return provider;
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("temperature", request.getTemperature());
        body.put("max_tokens", request.getMaxTokens());
        body.put("top_p", request.getTopP());
        body.put("stream", stream);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (!request.getStop().isEmpty()) {
            body.put("stop", request.getStop());
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : request.getMessages()) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole().getApiValue());
            m.put("content", msg.getContent() != null ? msg.getContent() : "");
            if (msg.hasToolCalls()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> call = new HashMap<>();
                    call.put("id", tc.getId());
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", tc.getName());
                    function.put("arguments", YdszJson.toJson(tc.getArguments()));
                    call.put("type", "function");
                    call.put("function", function);
                    calls.add(call);
                }
                m.put("tool_calls", calls);
            }
            if (msg.getToolCallId() != null) {
                m.put("tool_call_id", msg.getToolCallId());
            }
            messages.add(m);
        }
        body.put("messages", messages);
        if (!request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (var tool : request.getTools()) {
                Map<String, Object> t = new HashMap<>();
                Map<String, Object> function = new HashMap<>();
                function.put("name", tool.getName());
                function.put("description", tool.getDescription() != null ? tool.getDescription() : "");
                function.put("parameters", tool.getParametersSchema());
                t.put("type", "function");
                t.put("function", function);
                tools.add(t);
            }
            body.put("tools", tools);
        }
        if (request.getToolChoice() != null) {
            body.put("tool_choice", request.getToolChoice());
        }
        return body;
    }

    private ChatResponse parseResponse(String json) {
        YdszJsonObject obj = YdszJson.parseObjectToJsonObject(json);
        String id = obj.getString("id");
        String model = obj.getString("model");
        YdszJsonArray choices = obj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("LLM 响应无 choices", LlmException.ErrorType.INVALID_RESPONSE);
        }
        YdszJsonObject choice = choices.getJSONObject(0);
        YdszJsonObject message = choice.getJSONObject("message");
        String finishReason = choice.getString("finish_reason");
        String content = message != null ? message.getString("content") : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null && message.containsKey("tool_calls")) {
            YdszJsonArray calls = message.getJSONArray("tool_calls");
            for (int i = 0; i < calls.size(); i++) {
                YdszJsonObject call = calls.getJSONObject(i);
                String callId = call.getString("id");
                YdszJsonObject function = call.getJSONObject("function");
                String name = function.getString("name");
                String argsStr = function.getString("arguments");
                Map<String, Object> args = YdszJson.toObject(argsStr, Map.class);
                toolCalls.add(new ToolCall(callId, name, args));
            }
        }

        TokenUsage usage = TokenUsage.zero();
        if (obj.containsKey("usage")) {
            YdszJsonObject usageObj = obj.getJSONObject("usage");
            usage = new TokenUsage(
                    usageObj.getIntValue("prompt_tokens"),
                    usageObj.getIntValue("completion_tokens"));
        }

        ChatMessage chatMessage = toolCalls.isEmpty()
                ? ChatMessage.assistant(content, null, usage)
                : ChatMessage.assistantWithTools(content, null, toolCalls, usage);

        return new ChatResponse(id, model, chatMessage, usage, finishReason, toolCalls);
    }

    private ChatChunk parseChunk(String data) {
        try {
            YdszJsonObject obj = YdszJson.parseObjectToJsonObject(data);
            String id = obj.getString("id");
            String model = obj.getString("model");
            YdszJsonArray choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            YdszJsonObject choice = choices.getJSONObject(0);
            YdszJsonObject delta = choice.getJSONObject("delta");
            String finishReason = choice.getString("finish_reason");
            String content = delta != null ? delta.getString("content") : null;

            TokenUsage usage = null;
            if (obj.containsKey("usage") && obj.get("usage") != null) {
                YdszJsonObject usageObj = obj.getJSONObject("usage");
                usage = new TokenUsage(
                        usageObj.getIntValue("prompt_tokens"),
                        usageObj.getIntValue("completion_tokens"));
            }

            if (finishReason != null) {
                return ChatChunk.finish(id, model, finishReason, usage);
            }
            if (content != null) {
                return ChatChunk.content(id, model, content);
            }
            return null;
        } catch (Exception e) {
            log.warn("[LLM-{}] 解析 chunk 失败: {}", provider, e.getMessage());
            return null;
        }
    }

    /**
     * HTTP 状态码映射到 LLM 错误类型
     */
    private LlmException.ErrorType mapHttpError(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return LlmException.ErrorType.AUTH_FAILED;
        }
        if (statusCode == 404) {
            return LlmException.ErrorType.MODEL_NOT_FOUND;
        }
        if (statusCode == 429) {
            return LlmException.ErrorType.RATE_LIMITED;
        }
        if (statusCode >= 500) {
            return LlmException.ErrorType.PROVIDER_ERROR;
        }
        return LlmException.ErrorType.INVALID_RESPONSE;
    }

    /**
     * 判断异常链中是否包含超时异常
     */
    private boolean isTimeoutException(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
