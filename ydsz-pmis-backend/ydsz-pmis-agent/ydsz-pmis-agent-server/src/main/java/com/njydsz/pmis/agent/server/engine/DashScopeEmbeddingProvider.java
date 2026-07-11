package com.njydsz.pmis.agent.server.engine.embedding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 阿里云 DashScope Embedding 实现（P4-4 落地）。
 *
 * <p>使用 DashScope text-embedding-v2 模型，通过 OpenAI 兼容 API 将文本转为 1536 维向量。
 * 对标 Coze Embedding / Dify EmbeddingEndpoint，为 RAG 提供真实向量化能力。
 *
 * <p>启用方式：配置 {@code pmis.agent.rag.embedding-provider=dashscope}
 * <p>依赖配置：{@code pmis.agent.llm.api-key}（复用 LLM 的 API Key）
 *
 * <p>DashScope text-embedding-v2 规格：
 * <ul>
 *   <li>维度：1536</li>
 *   <li>最大输入：2048 tokens/请求</li>
 *   <li>支持批量：单次最多 10 条文本</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-4)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.agent.rag", name = "embedding-provider", havingValue = "dashscope")
public class DashScopeEmbeddingProvider implements EmbeddingProvider {

    /** DashScope text-embedding-v2 向量维度 */
    private static final int DIMENSION = 1536;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final long timeoutMillis;
    private final HttpClient httpClient;

    public DashScopeEmbeddingProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.rag.embedding-model:text-embedding-v2}") String model,
            @Value("${pmis.agent.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl
                : "https://dashscope.aliyuncs.com/compatible-mode";
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 10000;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        log.info("[DashScopeEmbedding] 初始化, model={}, dim={}", this.model, DIMENSION);
    }

    @Override
    public String name() {
        return "dashscope";
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashScopeEmbedding] API Key 未配置, 降级到零向量");
            return new float[DIMENSION];
        }
        try {
            return callEmbeddingApi(text);
        } catch (Exception e) {
            log.warn("[DashScopeEmbedding] 向量化失败, 降级到零向量: {}", e.getMessage());
            return new float[DIMENSION];
        }
    }

    /**
     * 调用 DashScope OpenAI 兼容 Embedding API。
     */
    private float[] callEmbeddingApi(String text) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", text);

        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url += "/v1/embeddings";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            String snippet = response.body() != null && response.body().length() > 200
                    ? response.body().substring(0, 200) : (response.body() == null ? "" : response.body());
            throw new RuntimeException("Embedding HTTP " + response.statusCode() + ": " + snippet);
        }

        JSONObject resp = JSON.parseObject(response.body());
        JSONArray data = resp.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Embedding 响应缺少 data 字段");
        }

        JSONObject first = data.getJSONObject(0);
        JSONArray embedding = first.getJSONArray("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new RuntimeException("Embedding 响应缺少 embedding 字段");
        }

        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.getFloatValue(i);
        }
        return result;
    }
}
