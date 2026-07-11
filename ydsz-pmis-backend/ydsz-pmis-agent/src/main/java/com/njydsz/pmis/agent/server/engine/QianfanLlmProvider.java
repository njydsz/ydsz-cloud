package com.njydsz.pmis.agent.server.engine.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.server.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 百度千帆 (Qianfan) Provider（批次 22 P1-3 落地）
 *
 * <p>直接 HTTP 调用千帆 ERNIE 系列模型 API, 支持 ERNIE-3.5-8K / ERNIE-4.0-8K / ERNIE-Speed.
 * 特点: 中文理解强, 多轮对话稳定性高, 适合 PMIS 工时异常/风险预警场景.
 *
 * <p>配置示例 (Nacos dataId=pmis-agent.yaml):
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: qianfan
 *       api-key: bce-v3/xxxxxxxx
 *       model: ernie-3.5-8k
 *       base-url: https://qianfan.baidubce.com
 * </pre>
 *
 * <p>鉴权: 使用 API Key 直接放 Authorization 头 (Bearer), 千帆 v2 API 简化了鉴权流程.
 *
 * <p><b>P0-5 修复</b>：原实现 {@code http.post().retrieve().body(...)} 未处理 4xx/5xx
 * 响应体，{@code RestClient.retrieve()} 默认抛出的 {@code HttpClientErrorException}/
 * {@code HttpServerErrorException} 消息只含状态码不含响应体（如千帆的
 * {@code {"error_code":110,"error_msg":"..."}}）。现通过 {@code .onStatus()}
 * 显式解析错误响应体并抛出带语义的异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次22)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "qianfan")
public class QianfanLlmProvider extends AbstractHttpLlmProvider {

    /** 千帆 API Key */
    private final String apiKey;
    /** 模型名称（如 ernie-3.5-8k） */
    private final String model;
    /** HTTP 客户端 */
    private final RestClient http;

    public QianfanLlmProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.llm.model:ernie-3.5-8k}") String model,
            @Value("${pmis.agent.llm.base-url:https://qianfan.baidubce.com}") String baseUrl,
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
     * {@link #QianfanLlmProvider(String, String, String, long, int, boolean)}。
     *
     * @param apiKey        API Key
     * @param model         模型名称
     * @param timeoutMillis 调用超时
     * @param maxRetries    最大重试次数
     * @param fallback      是否降级到 mock
     * @param http          注入的 RestClient 实例
     */
    QianfanLlmProvider(String apiKey, String model, long timeoutMillis,
                        int maxRetries, boolean fallback, RestClient http) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbackToMockOnError = fallback;
        this.http = http;
    }

    @Override
    public String name() {
        return "qianfan";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[Qianfan] API Key 未配置, 降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        Callable<String> call = () -> invokeQianfan(systemPrompt, userPrompt);
        return executeWithGuard(call, context);
    }

    /**
     * 调用千帆 v2 chat/completions API 进行推理。
     *
     * <p>P0-5 修复：通过 {@code .onStatus()} 显式处理 4xx/5xx 错误响应，
     * 解析千帆标准错误结构 {@code {"error_code":...,"error_msg":"..."}}，
     * 抛出带错误码的语义异常，便于上层重试/降级决策。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 推理结果文本
     */
    private String invokeQianfan(String systemPrompt, String userPrompt) {
        // 千帆 v2 chat/completions 格式 (OpenAI 兼容)
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
        ));
        body.put("temperature", 0.3);
        body.put("top_p", 0.9);
        body.put("penalty_score", 1.0);

        Map<String, Object> response = http.post()
                .uri("/v2/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                // P0-5 修复：显式处理 4xx/5xx 错误响应，解析千帆错误码并抛出带语义的异常
                .onStatus(HttpStatusCode::is4xxClientError, this::handleErrorResponse)
                .onStatus(HttpStatusCode::is5xxServerError, this::handleErrorResponse)
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) return "";
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
     * 解析千帆错误响应体并抛出带语义的异常（P0-5 修复）。
     *
     * <p>千帆错误响应结构：
     * <pre>
     * {
     *   "error_code": 110,
     *   "error_msg": "Access token invalid"
     * }
     * </pre>
     *
     * <p>抛出的 RuntimeException message 格式：
     * {@code Qianfan HTTP <status> [<error_code>]: <error_msg>}
     *
     * @param req  HTTP 请求
     * @param resp HTTP 响应
     * @throws IOException 读取响应体失败时抛出
     */
    private void handleErrorResponse(org.springframework.http.HttpRequest req,
                                      ClientHttpResponse resp) throws IOException {
        String respBody = readResponseBody(resp);
        HttpStatusCode status = resp.getStatusCode();
        String snippet = respBody.length() > 200 ? respBody.substring(0, 200) : respBody;
        log.warn("[Qianfan] HTTP {}: {}", status.value(), snippet);

        // 解析千帆标准错误结构
        String errCode = "UNKNOWN";
        String errMsg = respBody;
        try {
            JSONObject err = JSON.parseObject(respBody);
            if (err != null) {
                Integer code = err.getInteger("error_code");
                if (code != null) {
                    errCode = String.valueOf(code);
                }
                String message = err.getString("error_msg");
                if (message != null && !message.isEmpty()) {
                    errMsg = message;
                }
            }
        } catch (Exception parseEx) {
            // 非 JSON 响应，保留原始 respBody 作为 errMsg
            log.debug("[Qianfan] 响应体非 JSON 格式, 保留原始内容");
        }
        throw new RuntimeException(
                "Qianfan HTTP " + status.value() + " [" + errCode + "]: " + errMsg);
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
            log.warn("[Qianfan] 读取错误响应体失败: {}", ex.getMessage());
            return "";
        }
    }
}
