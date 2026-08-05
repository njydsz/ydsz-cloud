package com.remisoft.agent.infra.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.tree.ArrayNode;
import com.remisoft.common.json.tree.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import com.remisoft.agent.domain.gateway.LlmException;
import com.remisoft.agent.domain.rag.EmbeddingClient;

/**
 * OpenAI 兼容 Embedding 客户端
 *
 * <p>覆盖 OpenAI text-embedding-3-small/large、DeepSeek、通义千问等兼容 API。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    /** API 基础地址 */
    private final String baseUrl;
    /** API Key */
    private final String apiKey;
    /** 模型名称 */
    private final String model;
    /** 向量维度 */
    private final int dimension;
    /** HTTP 客户端 */
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
                    .body(RemiJson.toJson(body))
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
        ObjectNode obj = RemiJson.parseObject(json);
        ArrayNode data = obj.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new LlmException("Embedding 响应无 data", LlmException.ErrorType.INVALID_RESPONSE);
        }
        List<List<Float>> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            ObjectNode item = data.getJSONObject(i);
            ArrayNode embedding = item.getJSONArray("embedding");
            List<Float> vector = new ArrayList<>(embedding.size());
            for (int j = 0; j < embedding.size(); j++) {
                vector.add(embedding.getFloatValue(j));
            }
            result.add(vector);
        }
        return result;
    }
}
