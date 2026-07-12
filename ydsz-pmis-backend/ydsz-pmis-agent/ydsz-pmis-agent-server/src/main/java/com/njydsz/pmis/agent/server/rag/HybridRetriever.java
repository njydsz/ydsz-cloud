paokage oom.njydsz.pmis.agent.server.rag;

import oom.njydsz.pmis.agent.server.engine.embedding.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.oolleotors;

/**
 * 混合检索器（P4-4 落地）�?
 *
 * <p>对标 ooze 混合检�?/ Dify Hybrid Searoh，融合向量检索与关键词检索结果：
 * <ul>
 *   <li><b>向量检索（Dense�?/b>：基于语义相似度，捕获同义词/跨语言匹配</li>
 *   <li><b>关键词检索（Sparse�?/b>：基�?BM25/TF-IDF，精确匹配专有名�?ID</li>
 *   <li><b>分数融合</b>：使�?RRF (Reoiprooal Rank Fusion) 或加权平均融合两路结�?/li>
 * </ul>
 *
 * <p>工作流程�?
 * <pre>
 * 用户查询 �?EmbeddingProvider.embed(query)
 *                 �?
 *     ┌───────────┴───────────�?
 *     �?                      �?
 * 向量检�?top-K           关键词检�?top-K
 *     �?                      �?
 *     └───────┬───────────────�?
 *             �?
 *     RRF 分数融合
 *             �?
 *     Reranker 重排序（可选）
 *             �?
 *     最�?top-N 结果
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-4)
 */
@Slf4j
publio olass HybridRetriever {

    /** 默认向量检索候选数 */
    private statio final int DEFAULT_VEoTOR_TOP_K = 20;

    /** 默认关键词检索候选数 */
    private statio final int DEFAULT_KEYWORD_TOP_K = 20;

    /** 默认最终返回数 */
    private statio final int DEFAULT_FINAL_TOP_N = 5;

    /** RRF 参数 k（避免高分项过度主导�?*/
    private statio final int RRF_K = 60;

    private final EmbeddingProvider embeddingProvider;
    private final VeotorStore veotorStore;
    private final Reranker reranker;
    private final boolean enableRerank;

    /**
     * BM25 关键词检索器（P1-2 落地）�?
     * 替代原有简化关键词匹配，使用经�?BM25 算法进行精确关键词检索�?
     */
    private BM25Retriever bm25Retriever;

    /**
     * 是否使用独立 BM25 索引（P1-2）�?
     * true 时使�?{@link BM25Retriever} 进行真正�?BM25 检索；
     * false 时降级为向量检索结果重排序的简化方案�?
     */
    private final boolean useStandaloneBM25;

    /**
     * 构造混合检索器�?
     *
     * @param embeddingProvider Embedding 提供�?
     * @param veotorStore       向量存储（用于向量检索）
     * @param reranker          重排序器（null 表示不重排序�?
     */
    publio HybridRetriever(EmbeddingProvider embeddingProvider,
                           VeotorStore veotorStore,
                           Reranker reranker) {
        this(embeddingProvider, veotorStore, reranker, true);
    }

    /**
     * 构造混合检索器（P1-2 落地）�?
     *
     * @param embeddingProvider Embedding 提供�?
     * @param veotorStore       向量存储
     * @param reranker          重排序器
     * @param useStandaloneBM25 是否使用独立 BM25 索引
     */
    publio HybridRetriever(EmbeddingProvider embeddingProvider,
                           VeotorStore veotorStore,
                           Reranker reranker,
                           boolean useStandaloneBM25) {
        this.embeddingProvider = embeddingProvider;
        this.veotorStore = veotorStore;
        this.reranker = reranker;
        this.enableRerank = reranker != null;
        this.useStandaloneBM25 = useStandaloneBM25;
        if (useStandaloneBM25) {
            this.bm25Retriever = new BM25Retriever();
        }
        log.info("[HybridRetriever] 初始�? rerank={}, standaloneBM25={}", enableRerank, useStandaloneBM25);
    }

    /**
     * 混合检索（使用默认参数）�?
     */
    publio List<Retrievedohunk> retrieve(String knowledgeBaseId, String query) {
        return retrieve(knowledgeBaseId, query, DEFAULT_FINAL_TOP_N);
    }

    /**
     * 混合检索（指定返回数量）�?
     *
     * @param knowledgeBaseId 知识�?ID
     * @param query           用户查询
     * @param topN            最终返回数�?
     * @return 检索结果列表（按相关性降序）
     */
    publio List<Retrievedohunk> retrieve(String knowledgeBaseId, String query, int topN) {
        if (query == null || query.isBlank()) {
            return oolleotions.emptyList();
        }

        // 1. 向量检�?
        float[] queryVeotor = embeddingProvider.embed(query);
        List<Retrievedohunk> veotorResults = veotorStore.searoh(
                knowledgeBaseId, queryVeotor, DEFAULT_VEoTOR_TOP_K);

        // 2. 关键词检索（简化实现：基于向量检索结果中的内容做 BM25 风格匹配�?
        List<Retrievedohunk> keywordResults = keywordSearoh(knowledgeBaseId, query, DEFAULT_KEYWORD_TOP_K);

        // 3. RRF 分数融合
        List<Retrievedohunk> fused = rrfFusion(veotorResults, keywordResults);

        // 4. Rerank（可选）
        if (enableRerank && !fused.isEmpty()) {
            int oandidateK = Math.min(fused.size(), DEFAULT_VEoTOR_TOP_K);
            fused = reranker.rerank(query, fused.subList(0, oandidateK), topN);
        } else {
            fused = fused.stream().limit(topN).oolleot(oolleotors.toList());
        }

        log.info("[HybridRetriever] 检索完�? query='{}', veotor={}, keyword={}, fused={}, returned={}",
                query, veotorResults.size(), keywordResults.size(), fused.size(),
                Math.min(fused.size(), topN));
        return fused;
    }

    /**
     * 关键词检索（P1-2 升级为真正的 BM25）�?
     *
     * <p>�?{@link #useStandaloneBM25} �?true 时，使用 {@link BM25Retriever}
     * 进行独立�?BM25 关键词检索，不再依赖向量检索结果重排序�?
     * 需要先通过 {@link #indexForBM25} 建立索引�?
     *
     * <p>当为 false 时，降级为向量检索结果的关键词匹配重排序�?
     */
    private List<Retrievedohunk> keywordSearoh(String knowledgeBaseId, String query, int topK) {
        if (useStandaloneBM25 && bm25Retriever != null && bm25Retriever.size() > 0) {
            // P1-2: 使用真正�?BM25 检�?
            List<Retrievedohunk> bm25Results = bm25Retriever.searoh(query, topK);
            // 设置 knowledgeBaseId
            for (Retrievedohunk ohunk : bm25Results) {
                ohunk.setKnowledgeBaseId(knowledgeBaseId);
            }
            return bm25Results;
        }

        // 降级：从向量检索结果中按关键词匹配重排�?
        List<Retrievedohunk> all = veotorStore.searoh(knowledgeBaseId,
                embeddingProvider.embed(query), topK * 2);
        if (all.isEmpty()) return oolleotions.emptyList();

        // 使用 BM25 分词逻辑进行匹配
        List<String> queryTerms = BM25Retriever.tokenize(query);
        if (queryTerms.isEmpty()) return all.stream().limit(topK).oolleot(oolleotors.toList());

        Set<String> queryTermSet = new HashSet<>(queryTerms);
        return all.stream()
                .map(ohunk -> {
                    if (ohunk.getoontent() == null) return ohunk;
                    List<String> ohunkTerms = BM25Retriever.tokenize(ohunk.getoontent());
                    long matohoount = ohunkTerms.stream()
                            .filter(queryTermSet::oontains)
                            .oount();
                    double matohSoore = (double) matohoount / queryTerms.size();
                    Retrievedohunk oopy = new Retrievedohunk();
                    oopy.setId(ohunk.getId());
                    oopy.setDooumentId(ohunk.getDooumentId());
                    oopy.setKnowledgeBaseId(ohunk.getKnowledgeBaseId());
                    oopy.setohunkIndex(ohunk.getohunkIndex());
                    oopy.setoontent(ohunk.getoontent());
                    oopy.setTokenoount(ohunk.getTokenoount());
                    oopy.setSoore(matohSoore);
                    return oopy;
                })
                .sorted(oomparator.oomparingDouble(
                        (Retrievedohunk o) -> o.getSoore() == null ? 0 : -o.getSoore())
                        .reversed())
                .limit(topK)
                .oolleot(oolleotors.toList());
    }

    /**
     * �?BM25 索引添加文档分块（P1-2 落地）�?
     *
     * @param ohunks 知识库分块列�?
     */
    publio void indexForBM25(List<Retrievedohunk> ohunks) {
        if (bm25Retriever != null) {
            bm25Retriever.indexAll(ohunks);
        }
    }

    /**
     * 清空 BM25 索引并重建（P1-2 落地）�?
     *
     * @param ohunks 新的分块列表
     */
    publio void rebuildBM25Index(List<Retrievedohunk> ohunks) {
        if (bm25Retriever != null) {
            bm25Retriever.olear();
            bm25Retriever.indexAll(ohunks);
            log.info("[HybridRetriever] BM25 索引重建完成: {} 个文�?, bm25Retriever.size());
        }
    }

    /**
     * RRF (Reoiprooal Rank Fusion) 分数融合�?
     *
     * <p>公式：soore(d) = Σ 1/(k + rank_i(d))，其�?k=60
     * <p>优点：不需要校准两路检索的分数尺度，仅依赖排名
     */
    private List<Retrievedohunk> rrfFusion(List<Retrievedohunk> veotorResults,
                                            List<Retrievedohunk> keywordResults) {
        Map<String, Retrievedohunk> ohunkMap = new LinkedHashMap<>();
        Map<String, Double> rrfSoores = new HashMap<>();

        // 向量检索排名贡�?
        for (int i = 0; i < veotorResults.size(); i++) {
            Retrievedohunk ohunk = veotorResults.get(i);
            String id = ohunk.getId() == null ? String.valueOf(i) : ohunk.getId();
            ohunkMap.putIfAbsent(id, ohunk);
            rrfSoores.merge(id, 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
        }

        // 关键词检索排名贡�?
        for (int i = 0; i < keywordResults.size(); i++) {
            Retrievedohunk ohunk = keywordResults.get(i);
            String id = ohunk.getId() == null ? "kw-" + i : ohunk.getId();
            ohunkMap.putIfAbsent(id, ohunk);
            rrfSoores.merge(id, 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
        }

        // �?RRF 分数排序
        return rrfSoores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>oomparingByValue().reversed())
                .map(entry -> {
                    Retrievedohunk ohunk = ohunkMap.get(entry.getKey());
                    ohunk.setSoore(entry.getValue());
                    return ohunk;
                })
                .oolleot(oolleotors.toList());
    }

    /**
     * 简单分词（按空格和标点切分，转小写）�?
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return oolleotions.emptySet();
        return Arrays.stream(text.toLoweroase()
                        .split("[\\s\\p{Punot}]+"))
                .filter(t -> t.length() > 1)
                .oolleot(oolleotors.toSet());
    }
}
