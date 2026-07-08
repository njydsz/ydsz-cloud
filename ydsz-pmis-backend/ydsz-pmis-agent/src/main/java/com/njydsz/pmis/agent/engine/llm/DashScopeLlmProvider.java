package com.njydsz.pmis.agent.engine.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 阿里云通义千问 (DashScope) Provider（批次 22 P1-2 落地）
 *
 * <p>直接 HTTP 调用 DashScope OpenAI-兼容 API, 不依赖 spring-ai-dashscope starter
 * (后者在 1.0.0-M6 仍不稳定). 优势:
 * <ul>
 *   <li>国内访问, 网络稳定</li>
 *   <li>价格低 (qwen-turbo ¥0.0008/千token)</li>
 *   <li>支持中文 PMIS 业务术语</li>
 * </ul>
 *
 * <p>配置示例 (Nacos dataId=pmis-agent.yaml):
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: dashscope
 *       api-key: sk-xxxxxxxxxxxxxxxxxxxx
 *       model: qwen-turbo
 *       base-url: https://dashscope.aliyuncs.com/compatible-mode
 *
 * spring:
 *   http:
 *     client:
 *       connect-timeout: 3s
 *       read-timeout: 10s
 * </pre>
 *
 * <p><b>P0-5 修复</b>：原实现 {@code http.post().retrieve().body(...)} 未处理 4xx/5xx
 * 响应体，{@code RestClient.retrieve()} 默认抛出的 {@code HttpClientErrorException}/
 * {@code HttpServerErrorException} 消息只含状态码不含响应体（如 DashScope 的
 * {@code {"code":"InvalidApiKey","message":"..."}}）。现通过 {@code .onStatus()}
 * 显式解析错误响应体并抛出带语义的异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次22)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "dashscope")
public class DashScopeLlmProvider extends AbstractHttpLlmProvider {

    /** DashScope API Key */
    private final String apiKey;
    /** 模型名称（如 qwen-turbo） */
    private final String model;
    /** HTTP 客户端 */
    private final RestClient http;
    /** Base URL（用于流式调用时构造完整 URL，P4-1） */
    private final String baseUrl;

    public DashScopeLlmProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.llm.model:qwen-turbo}") String model,
            @Value("${pmis.agent.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis,
            @Value("${pmis.agent.llm.max-retries:2}") int maxRetries,
            @Value("${pmis.agent.llm.fallback-to-mock:true}") boolean fallback) {
        this(apiKey, model, timeoutMillis, maxRetries, fallback,
                RestClient.builder().baseUrl(baseUrl).build());
    }

    /**
     * 测试用构造函数（注入 RestClient，便于单测 mock 网络层）。
     *
     * <p>仅用于单元测试，生产环境应使用
     * {@link #DashScopeLlmProvider(String, String, String, long, int, boolean)}。
     *
     * @param apiKey        API Key
     * @param model         模型名称
     * @param timeoutMillis 调用超时
     * @param maxRetries    最大重试次数
     * @param fallback      是否降级到 mock
     * @param http          注入的 RestClient 实例
     */
    DashScopeLlmProvider(String apiKey, String model, long timeoutMillis,
                          int maxRetries, boolean fallback, RestClient http) {
        this(apiKey, model, timeoutMillis, maxRetries, fallback, http,
                "https://dashscope.aliyuncs.com/compatible-mode");
    }

    /**
     * 测试用构造函数（注入 RestClient + baseUrl，便于单测 mock）。
     *
     * @param apiKey        API Key
     * @param model         模型名称
     * @param timeoutMillis 调用超时
     * @param maxRetries    最大重试次数
     * @param fallback      是否降级到 mock
     * @param http          注入的 RestClient 实例
     * @param baseUrl       base URL（用于流式调用）
     */
    DashScopeLlmProvider(String apiKey, String model, long timeoutMillis,
                          int maxRetries, boolean fallback, RestClient http, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbackToMockOnError = fallback;
        this.http = http;
        this.baseUrl = baseUrl != null ? baseUrl : "https://dashscope.aliyuncs.com/compatible-mode";
    }

    @Override
    public String name() {
        return "dashscope";
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean supportsFunctionCalling() {
        return true;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashScope] API Key 未配置, 降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        Callable<String> call = () -> invokeDashScope(systemPrompt, userPrompt, context);
        return executeWithGuard(call, context);
    }

    /**
     * 带工具的 LLM 调用（原生 Function Calling，P4-2 落地）。
     *
     * <p>使用 DashScope OpenAI 兼容模式的 tools 参数，LLM 原生理解工具 schema
     * 并自主决定是否调用工具。支持单轮并行多工具调用。
     */
    @Override
    public LlmToolCallResponse chatWithTools(String systemPrompt, String userPrompt,
                                              List<Map<String, Object>> tools,
                                              AgentContext context) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashScope] API Key 未配置, 降级到 mock");
            return null;
        }
        Callable<LlmToolCallResponse> call = () -> invokeDashScopeWithTools(systemPrompt, userPrompt, tools, context);
        try {
            return executeWithGuardCallable(call, context);
        } catch (Exception e) {
            log.warn("[DashScope] chatWithTools 异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行带 tools 参数的 DashScope 调用。
     */
    private LlmToolCallResponse invokeDashScopeWithTools(String systemPrompt, String userPrompt,
                                                          List<Map<String, Object>> tools,
                                                          AgentContext context) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));
        body.put("temperature", 0.3);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        Map<String, Object> response = http.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleErrorResponse)
                .onStatus(HttpStatusCode::is5xxServerError, this::handleErrorResponse)
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) return null;

        if (context != null) {
            Object requestId = response.get("request_id");
            if (requestId != null) context.setProviderTraceId(requestId.toString());
        }

        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> msg)) return null;
        Object message = msg.get("message");
        if (!(message instanceof Map<?, ?> m)) return null;

        LlmToolCallResponse result = new LlmToolCallResponse();
        Object content = m.get("content");
        result.setContent(content == null ? "" : content.toString());

        // 解析 tool_calls（可能并行多个）
        Object toolCallsObj = m.get("tool_calls");
        if (toolCallsObj instanceof List<?> tcList && !tcList.isEmpty()) {
            List<LlmToolCallResponse.ToolCall> toolCalls = new java.util.ArrayList<>();
            for (int i = 0; i < tcList.size(); i++) {
                Object tcObj = tcList.get(i);
                if (!(tcObj instanceof Map<?, ?> tcMap)) continue;
                LlmToolCallResponse.ToolCall tc = new LlmToolCallResponse.ToolCall();
                tc.setId(tcMap.get("id") == null ? "" : tcMap.get("id").toString());
                tc.setIndex(tcMap.get("index") == null ? i : Integer.parseInt(tcMap.get("index").toString()));
                tc.setType(tcMap.get("type") == null ? "function" : tcMap.get("type").toString());
                Object fnObj = tcMap.get("function");
                if (fnObj instanceof Map<?, ?> fnMap) {
                    LlmToolCallResponse.ToolCall.FunctionCall fn = new LlmToolCallResponse.ToolCall.FunctionCall();
                    fn.setName(fnMap.get("name") == null ? "" : fnMap.get("name").toString());
                    fn.setArguments(fnMap.get("arguments") == null ? "{}" : fnMap.get("arguments").toString());
                    tc.setFunction(fn);
                }
                toolCalls.add(tc);
            }
            result.setToolCalls(toolCalls);
        }
        return result;
    }

    /**
     * 带返回值类型的 guard 执行（P4-2 辅助方法）。
     */
    private <T> T executeWithGuardCallable(Callable<T> call, AgentContext context) throws Exception {
        String result = executeWithGuard(() -> {
            T r = call.call();
            return r == null ? "" : com.alibaba.fastjson2.JSON.toJSONString(r);
        }, context);
        if (result == null || result.isEmpty()) return null;
        return com.alibaba.fastjson2.JSON.parseObject(result, new com.alibaba.fastjson2.TypeReference<T>() {});
    }

    /**
     * SSE 流式调用 DashScope（P4-1 落地）。
     *
     * <p>使用 DashScope OpenAI 兼容模式的 stream=true 参数，服务端以 SSE 格式
     * 逐 chunk 推送 choices[0].delta.content，每收到一个 chunk 即回调 tokenConsumer。
     *
     * <p>对标 Coze / Dify 的 token-level 流式推送，用户在 LLM 生成过程中
     * 即可看到内容逐步展现。
     */
    @Override
    public String chatStream(String systemPrompt, String userPrompt,
                             AgentContext context,
                             java.util.function.Consumer<String> tokenConsumer) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashScope] API Key 未配置, 降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        if (tokenConsumer == null) {
            return chat(systemPrompt, userPrompt, context);
        }
        Callable<String> call = () -> invokeDashScopeStream(systemPrompt, userPrompt, context, tokenConsumer);
        return executeWithGuard(call, context);
    }

    /**
     * 调用 DashScope OpenAI-兼容 API 进行推理。
     *
     * <p>P0-5 修复：通过 {@code .onStatus()} 显式处理 4xx/5xx 错误响应，
     * 解析 DashScope 标准错误结构 {@code {"code":"...","message":"..."}}，
     * 抛出带错误码的语义异常，便于上层重试/降级决策。
     *
     * <p>P1-4 增强：从响应体中提取 {@code request_id}，写入 {@link AgentContext#setProviderTraceId}，
     * 用于审计/账单核对。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param context      Agent 上下文（用于写入 providerTraceId）
     * @return 推理结果文本
     */
    private String invokeDashScope(String systemPrompt, String userPrompt, AgentContext context) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                        Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
                ),
                "temperature", 0.3,
                "top_p", 0.9
        );
        // 调 DashScope OpenAI-兼容模式
        Map<String, Object> response = http.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                // P0-5 修复：显式处理 4xx/5xx 错误响应，解析 DashScope 错误码并抛出带语义的异常
                .onStatus(HttpStatusCode::is4xxClientError, this::handleErrorResponse)
                .onStatus(HttpStatusCode::is5xxServerError, this::handleErrorResponse)
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) return "";
        // P1-4: 提取 DashScope request_id 写入 AgentContext，用于审计/账单核对
        if (context != null) {
            Object requestId = response.get("request_id");
            if (requestId != null && !requestId.toString().isEmpty()) {
                context.setProviderTraceId(requestId.toString());
            }
        }
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return "";
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> msg)) return "";
        Object message = msg.get("message");
        if (!(message instanceof Map<?, ?> m)) return "";
        Object content = m.get("content");
        return content == null ? "" : content.toString();
    }

    /**
     * 解析 DashScope 错误响应体并抛出带语义的异常（P0-5 修复）。
     *
     * <p>DashScope 错误响应结构：
     * <pre>
     * {
     *   "code": "InvalidApiKey",
     *   "message": "The API key provided is invalid.",
     *   "request_id": "xxx"
     * }
     * </pre>
     *
     * <p>抛出的 RuntimeException message 格式：
     * {@code DashScope HTTP <status> [<code>]: <message>}
     *
     * @param req      HTTP 请求
     * @param resp     HTTP 响应
     * @throws IOException 读取响应体失败时抛出
     */
    private void handleErrorResponse(org.springframework.http.HttpRequest req,
                                      ClientHttpResponse resp) throws IOException {
        String respBody = readResponseBody(resp);
        HttpStatusCode status = resp.getStatusCode();
        String snippet = respBody.length() > 200 ? respBody.substring(0, 200) : respBody;
        log.warn("[DashScope] HTTP {}: {}", status.value(), snippet);

        // 解析 DashScope 标准错误结构
        String errCode = "UNKNOWN";
        String errMsg = respBody;
        try {
            JSONObject err = JSON.parseObject(respBody);
            if (err != null) {
                String code = err.getString("code");
                if (code != null && !code.isEmpty()) {
                    errCode = code;
                }
                String message = err.getString("message");
                if (message != null && !message.isEmpty()) {
                    errMsg = message;
                }
            }
        } catch (Exception parseEx) {
            // 非 JSON 响应，保留原始 respBody 作为 errMsg
            log.debug("[DashScope] 响应体非 JSON 格式, 保留原始内容");
        }
        throw new RuntimeException(
                "DashScope HTTP " + status.value() + " [" + errCode + "]: " + errMsg);
    }

    /**
     * 读取 HTTP 响应体为 UTF-8 字符串。
     *
     * @param resp HTTP 响应
     * @return 响应体字符串；读取失败返回空字符串
     */
    private String readResponseBody(ClientHttpResponse resp) {
        try {
            byte[] bytes = resp.getBody().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("[DashScope] 读取错误响应体失败: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * SSE 流式调用 DashScope OpenAI 兼容 API（P4-1 落地）。
     *
     * <p>设置 stream=true，服务端以 SSE 格式推送：
     * <pre>
     * data: {"choices":[{"delta":{"content":"hello"}}]}
     * data: {"choices":[{"delta":{"content":" world"}}]}
     * data: [DONE]
     * </pre>
     *
     * <p>逐行解析，提取 delta.content 并回调 tokenConsumer，
     * 同时累积完整文本作为返回值。
     *
     * @param systemPrompt  系统提示词
     * @param userPrompt    用户提示词
     * @param context       Agent 上下文
     * @param tokenConsumer token 增量消费者
     * @return 完整推理结果（所有 delta 拼接后的全文）
     */
    private String invokeDashScopeStream(String systemPrompt, String userPrompt,
                                          AgentContext context,
                                          java.util.function.Consumer<String> tokenConsumer) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));
        body.put("temperature", 0.3);
        body.put("top_p", 0.9);
        body.put("stream", true);

        StringBuilder fullText = new StringBuilder();
        String streamUrl = baseUrl;
        if (streamUrl.endsWith("/")) streamUrl = streamUrl.substring(0, streamUrl.length() - 1);
        streamUrl += "/v1/chat/completions";

        // 使用 java.net.http.HttpClient 逐行读取 SSE 流
        java.net.http.HttpClient streamClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(streamUrl))
                .timeout(java.time.Duration.ofMillis(timeoutMillis))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        com.alibaba.fastjson2.JSON.toJSONString(body)))
                .build();

        java.net.http.HttpResponse<java.util.stream.Stream<String>> response =
                streamClient.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("DashScope stream HTTP " + response.statusCode());
        }

        // 逐行处理 SSE 数据
        for (String line : response.body().toList()) {
            if (line == null || line.isBlank()) continue;
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObject chunk = JSON.parseObject(data);
                    // 提取 request_id
                    if (context != null && chunk.containsKey("request_id")) {
                        String rid = chunk.getString("request_id");
                        if (rid != null && !rid.isEmpty()) {
                            context.setProviderTraceId(rid);
                        }
                    }
                    JSONArray choices = chunk.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject first = choices.getJSONObject(0);
                        JSONObject delta = first.getJSONObject("delta");
                        if (delta != null) {
                            String content = delta.getString("content");
                            if (content != null && !content.isEmpty()) {
                                fullText.append(content);
                                tokenConsumer.accept(content);
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    log.debug("[DashScope-Stream] 解析 chunk 失败: {}", parseEx.getMessage());
                }
            }
        }

        log.debug("[DashScope-Stream] 流式完成, totalLen={}", fullText.length());
        return fullText.toString();
    }
}
