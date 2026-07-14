package com.njydsz.pmis.agent.infra.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.pmis.common.util.json.JsonUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import com.njydsz.pmis.agent.domain.gateway.LlmException;
import com.njydsz.pmis.agent.domain.rag.EmbeddingClient;

/**
 * OpenAI 兼容 Embedding 客户端
 *
 * <p>覆盖 OpenAI text-embedding-3-small/large、DeepSeek、通义千问等兼容 API。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimension;
    private final RestClient restClient;

    public OpenAiEmbeddingClient(String baseUrl, String apiKey, String model, int dimension) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.model = model != null ? model : "text-embedding-3-small";
        this.dimension = dimension > 0 ? dimension : 1536;
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);
        body.put("dimensions", dimension);

        try {
            String responseJson = restClient.post()
                    .uri("/embeddings")
                    .body(JsonUtils.toJson(body))
                    .retrieve()
                    .body(String.class);
            return parseEmbeddings(responseJson);
        } catch (Exception e) {
            log.error("[Embedding] 调用失败: {}", e.getMessage(), e);
            throw new LlmException("Embedding 调用失败: " + e.getMessage(),
                    LlmException.ErrorType.PROVIDER_ERROR, e);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getModel() {
        return model;
    }

    private List<List<Float>> parseEmbeddings(String json) {
        Map<String, Object> obj = JsonUtils.parseMap(json);
        List<Object> data = obj.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new LlmException("Embedding 响应无 data", LlmException.ErrorType.INVALID_RESPONSE);
        }
        List<List<Float>> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.getJSONObject(i);
            List<Object> embedding = item.getJSONArray("embedding");
            List<Float> vector = new ArrayList<>(embedding.size());
            for (int j = 0; j < embedding.size(); j++) {
                vector.add(embedding.getFloatValue(j));
            }
            result.add(vector);
        }
        return result;
    }
}
