package com.njydsz.pmis.agent.rag;

import com.njydsz.pmis.agent.engine.embedding.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器（P4-4 落地）。
 *
 * <p>对标 Coze 混合检索 / Dify Hybrid Search，融合向量检索与关键词检索结果：
 * <ul>
 *   <li><b>向量检索（Dense）</b>：基于语义相似度，捕获同义词/跨语言匹配</li>
 *   <li><b>关键词检索（Sparse）</b>：基于 BM25/TF-IDF，精确匹配专有名词/ID</li>
 *   <li><b>分数融合</b>：使用 RRF (Reciprocal Rank Fusion) 或加权平均融合两路结果</li>
 * </ul>
 *
 * <p>工作流程：
 * <pre>
 * 用户查询 → EmbeddingProvider.embed(query)
 *                 ↓
 *     ┌───────────┴───────────┐
 *     ↓                       ↓
 * 向量检索 top-K           关键词检索 top-K
 *     ↓                       ↓
 *     └───────┬───────────────┘
 *             ↓
 *     RRF 分数融合
 *             ↓
 *     Reranker 重排序（可选）
 *             ↓
 *     最终 top-N 结果
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-4)
 */
@Slf4j
public class HybridRetriever {

    /** 默认向量检索候选数 */
    private static final int DEFAULT_VECTOR_TOP_K = 20;

    /** 默认关键词检索候选数 */
    private static final int DEFAULT_KEYWORD_TOP_K = 20;

    /** 默认最终返回数 */
    private static final int DEFAULT_FINAL_TOP_N = 5;

    /** RRF 参数 k（避免高分项过度主导） */
    private static final int RRF_K = 60;

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final Reranker reranker;
    private final boolean enableRerank;

    /**
     * 构造混合检索器。
     *
     * @param embeddingProvider Embedding 提供者
     * @param vectorStore       向量存储（用于向量检索）
     * @param reranker          重排序器（null 表示不重排序）
     */
    public HybridRetriever(EmbeddingProvider embeddingProvider,
                           VectorStore vectorStore,
                           Reranker reranker) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.reranker = reranker;
        this.enableRerank = reranker != null;
        log.info("[HybridRetriever] 初始化, rerank={}", enableRerank);
    }

    /**
     * 混合检索（使用默认参数）。
     */
    public List<RetrievedChunk> retrieve(String knowledgeBaseId, String query) {
        return retrieve(knowledgeBaseId, query, DEFAULT_FINAL_TOP_N);
    }

    /**
     * 混合检索（指定返回数量）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           用户查询
     * @param topN            最终返回数量
     * @return 检索结果列表（按相关性降序）
     */
    public List<RetrievedChunk> retrieve(String knowledgeBaseId, String query, int topN) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 向量检索
        float[] queryVector = embeddingProvider.embed(query);
        List<RetrievedChunk> vectorResults = vectorStore.search(
                knowledgeBaseId, queryVector, DEFAULT_VECTOR_TOP_K);

        // 2. 关键词检索（简化实现：基于向量检索结果中的内容做 BM25 风格匹配）
        List<RetrievedChunk> keywordResults = keywordSearch(knowledgeBaseId, query, DEFAULT_KEYWORD_TOP_K);

        // 3. RRF 分数融合
        List<RetrievedChunk> fused = rrfFusion(vectorResults, keywordResults);

        // 4. Rerank（可选）
        if (enableRerank && !fused.isEmpty()) {
            int candidateK = Math.min(fused.size(), DEFAULT_VECTOR_TOP_K);
            fused = reranker.rerank(query, fused.subList(0, candidateK), topN);
        } else {
            fused = fused.stream().limit(topN).collect(Collectors.toList());
        }

        log.info("[HybridRetriever] 检索完成, query='{}', vector={}, keyword={}, fused={}, returned={}",
                query, vectorResults.size(), keywordResults.size(), fused.size(),
                Math.min(fused.size(), topN));
        return fused;
    }

    /**
     * 关键词检索（简化版 BM25）。
     *
     * <p>当前实现基于向量检索结果重排序，后续可替换为全文检索引擎（如 Elasticsearch）。
     */
    private List<RetrievedChunk> keywordSearch(String knowledgeBaseId, String query, int topK) {
        // 简化实现：从向量检索结果中按关键词匹配重排序
        // 完整实现应使用 PostgreSQL ts_vector 或 Elasticsearch
        List<RetrievedChunk> all = vectorStore.search(knowledgeBaseId,
                embeddingProvider.embed(query), topK * 2);
        if (all.isEmpty()) return Collections.emptyList();

        // 提取查询关键词（简单分词）
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return all.stream().limit(topK).collect(Collectors.toList());

        // 计算每个 chunk 的关键词匹配分数
        return all.stream()
                .map(chunk -> {
                    if (chunk.getContent() == null) return chunk;
                    Set<String> chunkTerms = tokenize(chunk.getContent());
                    long matchCount = queryTerms.stream()
                            .filter(chunkTerms::contains)
                            .count();
                    double matchScore = (double) matchCount / queryTerms.size();
                    // 保留原始 score，设置 keyword 匹配分数
                    RetrievedChunk copy = new RetrievedChunk();
                    copy.setId(chunk.getId());
                    copy.setDocumentId(chunk.getDocumentId());
                    copy.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
                    copy.setChunkIndex(chunk.getChunkIndex());
                    copy.setContent(chunk.getContent());
                    copy.setTokenCount(chunk.getTokenCount());
                    copy.setScore(matchScore);
                    return copy;
                })
                .sorted(Comparator.comparingDouble(
                        (RetrievedChunk c) -> c.getScore() == null ? 0 : -c.getScore())
                        .reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * RRF (Reciprocal Rank Fusion) 分数融合。
     *
     * <p>公式：score(d) = Σ 1/(k + rank_i(d))，其中 k=60
     * <p>优点：不需要校准两路检索的分数尺度，仅依赖排名
     */
    private List<RetrievedChunk> rrfFusion(List<RetrievedChunk> vectorResults,
                                            List<RetrievedChunk> keywordResults) {
        Map<String, RetrievedChunk> chunkMap = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // 向量检索排名贡献
        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievedChunk chunk = vectorResults.get(i);
            String id = chunk.getId() == null ? String.valueOf(i) : chunk.getId();
            chunkMap.putIfAbsent(id, chunk);
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
        }

        // 关键词检索排名贡献
        for (int i = 0; i < keywordResults.size(); i++) {
            RetrievedChunk chunk = keywordResults.get(i);
            String id = chunk.getId() == null ? "kw-" + i : chunk.getId();
            chunkMap.putIfAbsent(id, chunk);
            rrfScores.merge(id, 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
        }

        // 按 RRF 分数排序
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    RetrievedChunk chunk = chunkMap.get(entry.getKey());
                    chunk.setScore(entry.getValue());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    /**
     * 简单分词（按空格和标点切分，转小写）。
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        return Arrays.stream(text.toLowerCase()
                        .split("[\\s\\p{Punct}]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }
}
