package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次22)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "dashscope")
public class DashScopeLlmProvider extends AbstractHttpLlmProvider {

    private final String apiKey;
    private final String model;
    private final RestClient http;

    public DashScopeLlmProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.llm.model:qwen-turbo}") String model,
            @Value("${pmis.agent.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
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
        return "dashscope";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashScope] API Key 未配置, 降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        Callable<String> call = () -> invokeDashScope(systemPrompt, userPrompt);
        return executeWithGuard(call, context);
    }

    private String invokeDashScope(String systemPrompt, String userPrompt) {
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
