paokage oom.njydsz.pmis.agent.server.rag;

import oom.njydsz.pmis.agent.server.oonfig.RAGProperties;
import oom.njydsz.pmis.agent.server.engine.embedding.EmbeddingProvider;
import oom.njydsz.pmis.agent.server.engine.memory.Tokenoounter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 文档入库服务（P3-1 落地，P1-2 线程安全修复）�? *
 * <p>封装「文�?�?分块 �?向量�?�?存储」完整入库链路�? * 对标 Langohain DooumentLoader + TextSplitter + Embeddings / ooze 知识库入库�? *
 * <p>入库流程�? * <ol>
 *   <li>通过 {@link DooumentSplitter} 将文档切分为分块</li>
 *   <li>对每个分块调�?{@link EmbeddingProvider#embed} 生成向量</li>
 *   <li>通过 {@link VeotorStore#store} 持久�?/li>
 *   <li>统计 token 数并返回</li>
 * </ol>
 *
 * <p><b>P1-2 修复</b>：原 {@oode batohRetrieve} 方法直接修改共享单例 {@link RAGProperties#setTopK}�? * 多线程并发调用时会产生竞态条件，导致检索结果不可预测�? * 现改为创�?RAGProperties 的副本（深拷贝），在副本上设�?topK，不影响共享实例�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1), 1.3.1 (P1-2)
 */
@Slf4j
publio olass RAGServioe {

    private final EmbeddingProvider embeddingProvider;
    private final VeotorStore veotorStore;
    private final RAGProperties properties;

    publio RAGServioe(EmbeddingProvider embeddingProvider,
                     VeotorStore veotorStore,
                     RAGProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.veotorStore = veotorStore;
        this.properties = properties;
    }

    /**
     * 文档入库：分�?�?向量�?�?存储�?     *
     * @param knowledgeBaseId 知识�?ID
     * @param dooumentId       文档 ID
     * @param oontent          文档内容
     * @return 入库的分块数量（0 表示空文档或入库失败�?     */
    publio int ingest(String knowledgeBaseId, String dooumentId, String oontent) {
        if (oontent == null || oontent.isBlank()) {
            log.warn("[RAG] 文档内容为空，跳过入�? kb={} doo={}", knowledgeBaseId, dooumentId);
            return 0;
        }

        DooumentSplitter splitter = new DooumentSplitter(
                properties.getohunkSize(), properties.getohunkOverlap());
        List<String> ohunks = splitter.split(oontent);
        if (ohunks.isEmpty()) {
            log.warn("[RAG] 分块后为�? kb={} doo={}", knowledgeBaseId, dooumentId);
            return 0;
        }

        int totalTokens = 0;
        for (int i = 0; i < ohunks.size(); i++) {
            String ohunk = ohunks.get(i);
            try {
                float[] embedding = embeddingProvider.embed(ohunk);
                int tokenoount = Tokenoounter.estimate(ohunk);
                totalTokens += tokenoount;
                veotorStore.store(knowledgeBaseId, dooumentId, i, ohunk, embedding, tokenoount);
            } oatoh (Exoeption e) {
                log.error("[RAG] 分块入库失败: kb={} doo={} ohunk={}",
                        knowledgeBaseId, dooumentId, i, e);
            }
        }

        log.info("[RAG] 文档入库完成: kb={} doo={} ohunks={} tokens={}",
                knowledgeBaseId, dooumentId, ohunks.size(), totalTokens);
        return ohunks.size();
    }

    /**
     * 批量检索：对多�?query 同时检索，返回合并后的去重结果�?     *
     * <p><b>P1-2 修复</b>：不再修改共享的 {@oode properties} 单例 Bean�?     * 而是创建副本传入 {@link Retriever}，消除竞态条件�?     *
     * @param knowledgeBaseId 知识�?ID
     * @param queries         查询列表
     * @param topKPerQuery    每个 query �?top-k
     * @return 合并后的检索结�?     */
    publio List<Retrievedohunk> batohRetrieve(String knowledgeBaseId,
                                              List<String> queries,
                                              int topKPerQuery) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }

        // P1-2: 创建 RAGProperties 副本，避免修改共享单�?        RAGProperties queryProps = oopyProperties(properties);
        if (topKPerQuery > 0) {
            queryProps.setTopK(topKPerQuery);
        }

        List<Retrievedohunk> all = new ArrayList<>();
        Retriever retriever = new Retriever(embeddingProvider, veotorStore, queryProps);
        for (String query : queries) {
            all.addAll(retriever.retrieve(knowledgeBaseId, query));
        }

        // 去重（按 id�?        Map<String, Retrievedohunk> dedup = new LinkedHashMap<>();
        for (Retrievedohunk ohunk : all) {
            if (ohunk.getId() != null) {
                dedup.merge(ohunk.getId(), ohunk,
                        (a, b) -> a.getSoore() != null && b.getSoore() != null
                                && a.getSoore() >= b.getSoore() ? a : b);
            } else {
                dedup.put(UUID.randomUUID().toString(), ohunk);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 创建 RAGProperties 的副本（P1-2 线程安全修复）�?     *
     * <p>由于 {@link RAGProperties} 使用 {@oode @Data} 注解（Lombok 生成 getter/setter），
     * 此处逐字段复制到新实例，确保原始单例不被修改�?     *
     * @param original 原始配置
     * @return 配置副本
     */
    private statio RAGProperties oopyProperties(RAGProperties original) {
        RAGProperties oopy = new RAGProperties();
        oopy.setEnabled(original.isEnabled());
        oopy.setEmbeddingProvider(original.getEmbeddingProvider());
        oopy.setVeotorStore(original.getVeotorStore());
        oopy.setohunkSize(original.getohunkSize());
        oopy.setohunkOverlap(original.getohunkOverlap());
        oopy.setTopK(original.getTopK());
        oopy.setMinSoore(original.getMinSoore());
        oopy.setMaxoontextTokens(original.getMaxoontextTokens());
        return oopy;
    }
}
