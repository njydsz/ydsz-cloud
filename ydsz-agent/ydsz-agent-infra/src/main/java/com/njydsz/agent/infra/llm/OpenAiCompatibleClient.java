package com.njydsz.agent.infra.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.JsonMapper;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.ObjectNode;

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
    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;
    /** 重试基础延迟（毫秒，指数退避） */
    private static final long RETRY_DELAY_BASE_MS = 1000L;
    /** 默认最大并发请求数 */
    private static final int DEFAULT_MAX_CONCURRENT = 50;

    /** Provider 标识 */
    private final String provider;
    /** API 基础地址 */
    private final String baseUrl;
    /** API Key */
    private final String apiKey;
    /** 调用超时时间（秒） */
    private final int timeoutSeconds;
    /** 同步调用 HTTP 客户端 */
    private final RestClient restClient;
    /** 流式调用 HTTP 客户端 */
    private final WebClient webClient;
    /** 并发限流信号量 */
    private final Semaphore concurrencyLimiter;
    /** 最大重试次数 */
    private final int maxRetries;
    /** SNAKE_CASE 命名策略的 Mapper（OpenAI API 要求 snake_case） */
    private final JsonMapper snakeCaseMapper;

    public OpenAiCompatibleClient(String provider, String baseUrl, String apiKey, int timeoutSeconds) {
        this(provider, baseUrl, apiKey, timeoutSeconds, DEFAULT_MAX_RETRIES, DEFAULT_MAX_CONCURRENT);
    }

    public OpenAiCompatibleClient(String provider, String baseUrl, String apiKey, int timeoutSeconds,
                                  int maxRetries, int maxConcurrent) {
        this.provider = provider;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.concurrencyLimiter = new Semaphore(maxConcurrent > 0 ? maxConcurrent : DEFAULT_MAX_CONCURRENT);
        this.snakeCaseMapper = JsonMapper.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build();
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
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                long delay = RETRY_DELAY_BASE_MS * attempt;
                log.info("[LLM-{}] 重试 {}/{}: delay={}ms", provider, attempt, maxRetries, delay);
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            try {
                if (!concurrencyLimiter.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw new LlmException("LLM 并发限流等待超时", LlmException.ErrorType.RATE_LIMITED, null);
                }
                try {
                    String responseJson = restClient.post()
                            .uri("/chat/completions")
                            .body(snakeCaseMapper.toJson(requestBody))
                            .retrieve()
                            .body(String.class);
                    return parseResponse(responseJson);
                } finally {
                    concurrencyLimiter.release();
                }
            } catch (LlmException e) {
                lastException = e;
                if (!isRetryable(e) || attempt >= maxRetries) {
                    throw e;
                }
                log.warn("[LLM-{}] 可重试错误 (attempt={}/{}): type={}, msg={}",
                        provider, attempt + 1, maxRetries, e.getErrorType(), e.getMessage());
            } catch (HttpClientErrorException e) {
                LlmException.ErrorType errorType = mapHttpError(e.getStatusCode().value());
                lastException = new LlmException("LLM 调用失败 (HTTP " + e.getStatusCode().value() + ")",
                        errorType, e);
                if (errorType != LlmException.ErrorType.RATE_LIMITED || attempt >= maxRetries) {
                    log.error("[LLM-{}] 同步调用 HTTP 错误: status={}", provider, e.getStatusCode().value());
                    throw (LlmException) lastException;
                }
                log.warn("[LLM-{}] 限流重试 (attempt={}/{})", provider, attempt + 1, maxRetries);
            } catch (ResourceAccessException e) {
                lastException = new LlmException("LLM 网络超时或连接拒绝: " + e.getMessage(),
                        LlmException.ErrorType.NETWORK_TIMEOUT, e);
                if (attempt >= maxRetries) {
                    log.error("[LLM-{}] 同步调用网络异常: {}", provider, e.getMessage());
                    throw (LlmException) lastException;
                }
                log.warn("[LLM-{}] 网络重试 (attempt={}/{})", provider, attempt + 1, maxRetries);
            } catch (Exception e) {
                lastException = e;
                if (attempt >= maxRetries) {
                    log.error("[LLM-{}] 同步调用失败: {}", provider, e.getMessage(), e);
                    throw new LlmException("LLM 调用失败: " + e.getMessage(),
                            LlmException.ErrorType.PROVIDER_ERROR, e);
                }
                log.warn("[LLM-{}] 未知错误重试 (attempt={}/{})", provider, attempt + 1, maxRetries);
            }
        }
        throw new LlmException("LLM 调用重试耗尽", LlmException.ErrorType.PROVIDER_ERROR, lastException);
    }

    private boolean isRetryable(LlmException e) {
        return e.getErrorType() == LlmException.ErrorType.NETWORK_TIMEOUT
                || e.getErrorType() == LlmException.ErrorType.RATE_LIMITED
                || e.getErrorType() == LlmException.ErrorType.PROVIDER_ERROR;
    }

    @Override
    public void stream(ChatRequest request, Consumer<ChatChunk> chunkConsumer) {
        Map<String, Object> requestBody = buildRequestBody(request, true);
        try {
            webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(snakeCaseMapper.toJson(requestBody))
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
        // 顶层字段保持显式 snake_case key（Map key 在序列化时原样透传，不受命名策略影响）；
        // 嵌套的 messages / tools 直接放入领域对象，由 AgentJsonModule 注册的
        // ChatMessageSerializer / ToolDefinitionSerializer 在全局 toJson 路径中统一产出
        // OpenAI 契约形状（role 用 API 枚举值、tool_calls 结构、arguments 为 JSON 字符串），
        // 替代原先此处手工拼装 Map 的冗余代码。
        Map<String, Object> body = new LinkedHashMap<>();
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
        body.put("messages", request.getMessages());
        if (!request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
        }
        if (request.getToolChoice() != null) {
            body.put("tool_choice", request.getToolChoice());
        }
        return body;
    }

    private ChatResponse parseResponse(String json) {
        ObjectNode obj = (ObjectNode) YdszJson.readTree(json);
        String id = obj.get("id").asText();
        String model = obj.get("model").asText();
        ArrayNode choices = (ArrayNode) obj.get("choices");
        if (choices == null || choices.size() == 0) {
            throw new LlmException("LLM 响应无 choices", LlmException.ErrorType.INVALID_RESPONSE);
        }
        ObjectNode choice = (ObjectNode) choices.get(0);
        ObjectNode message = choice.has("message") ? (ObjectNode) choice.get("message") : null;
        String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText() : null;
        String content = message != null && message.has("content") ? message.get("content").asText() : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null && message.has("tool_calls")) {
            ArrayNode calls = (ArrayNode) message.get("tool_calls");
            for (int i = 0; i < calls.size(); i++) {
                ObjectNode call = (ObjectNode) calls.get(i);
                String callId = call.get("id").asText();
                ObjectNode function = (ObjectNode) call.get("function");
                String name = function.get("name").asText();
                String argsStr = function.get("arguments").asText();
                Map<String, Object> args = YdszJson.toObject(argsStr, Map.class);
                toolCalls.add(new ToolCall(callId, name, args));
            }
        }

        TokenUsage usage = TokenUsage.zero();
        if (obj.has("usage")) {
            ObjectNode usageObj = (ObjectNode) obj.get("usage");
            usage = new TokenUsage(
                    usageObj.get("prompt_tokens").asInt(),
                    usageObj.get("completion_tokens").asInt());
        }

        ChatMessage chatMessage = toolCalls.isEmpty()
                ? ChatMessage.assistant(content, null, usage)
                : ChatMessage.assistantWithTools(content, null, toolCalls, usage);

        return new ChatResponse(id, model, chatMessage, usage, finishReason, toolCalls);
    }

    private ChatChunk parseChunk(String data) {
        try {
            ObjectNode obj = (ObjectNode) YdszJson.readTree(data);
            String id = obj.get("id").asText();
            String model = obj.get("model").asText();
            ArrayNode choices = (ArrayNode) obj.get("choices");
            if (choices == null || choices.size() == 0) {
                return null;
            }
            ObjectNode choice = (ObjectNode) choices.get(0);
            ObjectNode delta = choice.has("delta") ? (ObjectNode) choice.get("delta") : null;
            String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText() : null;
            String content = delta != null && delta.has("content") ? delta.get("content").asText() : null;

            TokenUsage usage = null;
            if (obj.has("usage") && obj.get("usage") != null) {
                ObjectNode usageObj = (ObjectNode) obj.get("usage");
                usage = new TokenUsage(
                        usageObj.get("prompt_tokens").asInt(),
                        usageObj.get("completion_tokens").asInt());
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
