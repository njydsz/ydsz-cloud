package com.njydsz.pmis.agent.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 重排序器（P4-4 落地）。
 *
 * <p>对标 Coze Rerank / Dify Rerank Model，对向量检索的 top-K 候选结果进行二次排序：
 * <ul>
 *   <li>使用 LLM 或专用 Rerank 模型对 query-chunk 对进行相关性评分</li>
 *   <li>按评分重新排序，取 top-N 返回</li>
 *   <li>显著提升检索精度，减少幻觉</li>
 * </ul>
 *
 * <p>当前实现提供两种策略：
 * <ol>
 *   <li>{@link Strategy#LLM} - 使用 LLM 对每个 chunk 评分（通用，无需额外模型）</li>
 *   <li>{@link Strategy#CROSS_ENCODER} - 调用专用 Rerank API（如 DashScope gte-rerank）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-4)
 */
@Slf4j
public class Reranker {

    private final String apiKey;
    private final String baseUrl;
    private final String rerankModel;
    private final Strategy strategy;
    private final long timeoutMillis;
    private final HttpClient httpClient;

    /**
     * LLM Provider（P0-3 落地）。
     *
     * <p>当 strategy=LLM 时，使用此 Provider 调用 LLM 对 chunk 进行评分。
     * 可为 null，此时降级为原始分数排序。
     */
    private LlmProvider llmProvider;

    /**
     * 重排序策略。
     */
    public enum Strategy {
        /** 使用 LLM 评分（通用） */
        LLM,
        /** 使用专用 Cross-Encoder Rerank API */
        CROSS_ENCODER
    }

    public Reranker(String apiKey, String baseUrl, String rerankModel,
                    Strategy strategy, long timeoutMillis) {
        this(apiKey, baseUrl, rerankModel, strategy, timeoutMillis, null);
    }

    /**
     * 构造 Reranker（P0-3：支持注入 LlmProvider）。
     *
     * @param apiKey      API Key（Cross-Encoder 策略使用）
     * @param baseUrl     API Base URL
     * @param rerankModel Rerank 模型名
     * @param strategy    重排序策略
     * @param timeoutMillis 超时
     * @param llmProvider LLM Provider（LLM 策略使用，可为 null）
     */
    public Reranker(String apiKey, String baseUrl, String rerankModel,
                    Strategy strategy, long timeoutMillis, LlmProvider llmProvider) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://dashscope.aliyuncs.com/api/v1";
        this.rerankModel = rerankModel != null ? rerankModel : "gte-rerank";
        this.strategy = strategy != null ? strategy : Strategy.CROSS_ENCODER;
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : 10000;
        this.llmProvider = llmProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        log.info("[Reranker] 初始化, strategy={}, model={}, llmProvider={}",
                this.strategy, this.rerankModel, this.llmProvider != null ? "set" : "null");
    }

    /**
     * 设置 LLM Provider（P0-3 落地）。
     *
     * @param llmProvider LLM Provider
     */
    public void setLlmProvider(LlmProvider llmProvider) {
        this.llmProvider = llmProvider;
        log.info("[Reranker] LLM Provider 已设置: {}", llmProvider != null ? llmProvider.name() : "null");
    }

    /**
     * 对检索结果进行重排序。
     *
     * @param query    用户查询
     * @param candidates 候选分块列表（向量检索 top-K）
     * @param topN     重排序后返回的数量
     * @return 重排序后的分块列表（按相关性降序）
     */
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        if (query == null || query.isBlank()) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(
                            (RetrievedChunk c) -> c.getScore() == null ? 0 : -c.getScore())
                            .reversed())
                    .limit(topN)
                    .collect(Collectors.toList());
        }

        try {
            if (strategy == Strategy.CROSS_ENCODER) {
                return rerankWithCrossEncoder(query, candidates, topN);
            } else {
                return rerankWithLlm(query, candidates, topN);
            }
        } catch (Exception e) {
            log.warn("[Reranker] 重排序失败, 降级为原始排序: {}", e.getMessage());
            return candidates.stream()
                    .limit(topN)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 使用 DashScope Rerank API 进行 Cross-Encoder 重排序。
     */
    private List<RetrievedChunk> rerankWithCrossEncoder(String query,
                                                          List<RetrievedChunk> candidates,
                                                          int topN) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", rerankModel);
        body.put("query", query);
        JSONArray documents = new JSONArray();
        for (RetrievedChunk chunk : candidates) {
            documents.add(chunk.getContent() == null ? "" : chunk.getContent());
        }
        body.put("documents", documents);
        body.put("top_n", topN);
        body.put("return_documents", false);

        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url += "/services/rerank/text-rerank/text-rerank";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("Rerank HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        JSONObject resp = JSON.parseObject(response.body());
        JSONObject output = resp.getJSONObject("output");
        if (output == null) {
            throw new RuntimeException("Rerank 响应缺少 output 字段");
        }
        JSONArray results = output.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return candidates.stream().limit(topN).collect(Collectors.toList());
        }

        List<RetrievedChunk> reranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject r = results.getJSONObject(i);
            int index = r.getIntValue("index");
            double score = r.getDoubleValue("relevance_score");
            if (index >= 0 && index < candidates.size()) {
                RetrievedChunk chunk = candidates.get(index);
                // 更新分数为 rerank 分数
                chunk.setScore(score);
                reranked.add(chunk);
            }
        }
        return reranked;
    }

    /** LLM 评分系统提示词（P0-3 落地） */
    private static final String LLM_RERANK_SYSTEM_PROMPT = """
            你是一个信息检索评分专家。请根据用户查询，对每个文档片段的相关性进行评分。

            评分规则：
            - 10分：完全相关，直接回答了用户查询
            - 7-9分：高度相关，包含用户查询所需的关键信息
            - 4-6分：部分相关，包含一些有用信息但不完整
            - 1-3分：弱相关，仅提及相关话题但未提供有效信息
            - 0分：完全不相关

            请输出 JSON 数组，每个元素包含 index（片段序号）和 score（评分）：
            [{"index": 0, "score": 9}, {"index": 1, "score": 3}]

            请严格输出 JSON 格式（不要使用 markdown 代码块包裹）。""";

    /**
     * 使用 LLM 评分进行重排序（P0-3 完善实现）。
     *
     * <p>将所有候选 chunk 与用户查询一起发送给 LLM，让 LLM 对每个 chunk 的相关性
     * 打分（0-10），然后按分数重新排序取 top-N。
     *
     * <p>优势：相比 Cross-Encoder，LLM 评分能理解深层语义关系，
     * 适用于复杂查询和跨领域检索。
     *
     * <p>降级策略：当 llmProvider 为 null 或 LLM 调用失败时，
     * 降级为按原始向量相似度分数排序。
     */
    private List<RetrievedChunk> rerankWithLlm(String query,
                                                 List<RetrievedChunk> candidates,
                                                 int topN) {
        if (llmProvider == null) {
            log.warn("[Reranker] LLM 策略但 llmProvider 为 null, 降级为原始分数排序");
            return fallbackSort(candidates, topN);
        }

        try {
            // 构建 LLM 输入：列出所有候选 chunk
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("用户查询：").append(query).append("\n\n");
            userPrompt.append("文档片段列表：\n");
            for (int i = 0; i < candidates.size(); i++) {
                RetrievedChunk chunk = candidates.get(i);
                String content = chunk.getContent() == null ? "" : chunk.getContent();
                // 截断过长的 chunk 内容，避免 token 爆炸
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                userPrompt.append("[片段 ").append(i).append("] ")
                        .append(content).append("\n\n");
            }
            userPrompt.append("请对每个片段评分并输出 JSON 数组。");

            // 调用 LLM
            String llmRaw = llmProvider.chat(LLM_RERANK_SYSTEM_PROMPT,
                    userPrompt.toString(), null);
            String json = LlmProvider.stripMarkdownCodeFence(llmRaw);

            // 解析评分结果
            JSONArray scores = JSON.parseArray(json);
            if (scores == null || scores.isEmpty()) {
                log.warn("[Reranker] LLM 评分结果为空, 降级为原始排序");
                return fallbackSort(candidates, topN);
            }

            // 将评分映射到候选 chunk
            for (int i = 0; i < scores.size(); i++) {
                JSONObject item = scores.getJSONObject(i);
                if (item == null) continue;
                int index = item.getIntValue("index", -1);
                double score = item.containsKey("score") ? item.getDoubleValue("score") : -1;
                if (index >= 0 && index < candidates.size() && score >= 0) {
                    // 归一化到 0-1
                    candidates.get(index).setScore(score / 10.0);
                }
            }

            // 按新分数排序
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(
                            (RetrievedChunk c) -> c.getScore() == null ? 0 : -c.getScore())
                            .reversed())
                    .limit(topN)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("[Reranker] LLM 评分失败, 降级为原始排序: {}", e.getMessage());
            return fallbackSort(candidates, topN);
        }
    }

    /**
     * 降级排序：按原始分数降序排列取 top-N。
     */
    private List<RetrievedChunk> fallbackSort(List<RetrievedChunk> candidates, int topN) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (RetrievedChunk c) -> c.getScore() == null ? 0 : -c.getScore())
                        .reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }
}
