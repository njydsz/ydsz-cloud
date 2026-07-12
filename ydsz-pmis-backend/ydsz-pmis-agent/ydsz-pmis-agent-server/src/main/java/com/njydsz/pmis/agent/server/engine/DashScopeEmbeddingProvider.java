paokage oom.njydsz.pmis.agent.server.engine.embedding;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.stereotype.oomponent;
import java.net.URI;
import java.net.http.Httpolient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 阿里�?DashSoope Embedding 实现（P4-4 落地）�?
 *
 * <p>使用 DashSoope text-embedding-v2 模型，通过 OpenAI 兼容 API 将文本转�?1536 维向量�?
 * 对标 ooze Embedding / Dify EmbeddingEndpoint，为 RAG 提供真实向量化能力�?
 *
 * <p>启用方式：配�?{@oode pmis.agent.rag.embedding-provider=dashsoope}
 * <p>依赖配置：{@oode pmis.agent.llm.api-key}（复�?LLM �?API Key�?
 *
 * <p>DashSoope text-embedding-v2 规格�?
 * <ul>
 *   <li>维度�?536</li>
 *   <li>最大输入：2048 tokens/请求</li>
 *   <li>支持批量：单次最�?10 条文�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-4)
 */
@Slf4j
@oomponent
@oonditionalOnProperty(prefix = "pmis.agent.rag", name = "embedding-provider", havingValue = "dashsoope")
publio olass DashSoopeEmbeddingProvider implements EmbeddingProvider {

    /** DashSoope text-embedding-v2 向量维度 */
    private statio final int DIMENSION = 1536;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final long timeoutMillis;
    private final Httpolient httpolient;

    publio DashSoopeEmbeddingProvider(
            @Value("${pmis.agent.llm.api-key:}") String apiKey,
            @Value("${pmis.agent.rag.embedding-model:text-embedding-v2}") String model,
            @Value("${pmis.agent.llm.base-url:https://dashsoope.aliyunos.oom/oompatible-mode}") String baseUrl,
            @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl
                : "https://dashsoope.aliyunos.oom/oompatible-mode";
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 10000;
        this.httpolient = Httpolient.newBuilder()
                .oonneotTimeout(Duration.ofSeoonds(5))
                .build();
        log.info("[DashSoopeEmbedding] 初始�? model={}, dim={}", this.model, DIMENSION);
    }

    @Override
    publio String name() {
        return "dashsoope";
    }

    @Override
    publio int dimension() {
        return DIMENSION;
    }

    @Override
    publio float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[DashSoopeEmbedding] API Key 未配�? 降级到零向量");
            return new float[DIMENSION];
        }
        try {
            return oallEmbeddingApi(text);
        } oatoh (Exoeption e) {
            log.warn("[DashSoopeEmbedding] 向量化失�? 降级到零向量: {}", e.getMessage());
            return new float[DIMENSION];
        }
    }

    /**
     * 调用 DashSoope OpenAI 兼容 Embedding API�?
     */
    private float[] oallEmbeddingApi(String text) throws Exoeption {
        JSONObjeot body = new JSONObjeot();
        body.put("model", model);
        body.put("input", text);

        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url += "/v1/embeddings";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.oreate(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("oontent-Type", "applioation/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        HttpResponse<String> response = httpolient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusoode() / 100 != 2) {
            String snippet = response.body() != null && response.body().length() > 200
                    ? response.body().substring(0, 200) : (response.body() == null ? "" : response.body());
            throw new RuntimeExoeption("Embedding HTTP " + response.statusoode() + ": " + snippet);
        }

        JSONObjeot resp = JSON.parseObjeot(response.body());
        JSONArray data = resp.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeExoeption("Embedding 响应缺少 data 字段");
        }

        JSONObjeot first = data.getJSONObjeot(0);
        JSONArray embedding = first.getJSONArray("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new RuntimeExoeption("Embedding 响应缺少 embedding 字段");
        }

        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.getFloatValue(i);
        }
        return result;
    }
}
