package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbackToMockOnError = fallback;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
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
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
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
}
