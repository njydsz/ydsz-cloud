package com.remisoft.agent.server.rag;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.remisoft.agent.domain.rag.EmbeddingClient;
import com.remisoft.agent.domain.rag.TextChunk;
import com.remisoft.agent.domain.rag.TextChunker;
import com.remisoft.agent.domain.rag.VectorStore;

/**
 * 文档摄入服务
 *
 * <p>将文档内容分块、向量化并存储到向量库中，供 RAG 检索使用。
 *
 * <p>摄入流程：
 * <ol>
 *   <li>文本分块（{@link TextChunker}）</li>
 *   <li>向量化（{@link EmbeddingClient}）</li>
 *   <li>存储到向量库（{@link VectorStore}）</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    // Embedding 批量调用大小：单次最多 20 条，平衡吞吐与单次请求超时风险
    private static final int EMBED_BATCH_SIZE = 20;

    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public DocumentIngestionService(TextChunker textChunker, EmbeddingClient embeddingClient,
                                     VectorStore vectorStore) {
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    /**
     * 摄入文档
     *
     * @param documentId    文档 ID
     * @param content       文档文本内容
     * @param documentTitle 文档标题
     * @param source        来源（nextwiki/project/contract）
     * @return 摄入的文本块数
     */
    public int ingest(String documentId, String content, String documentTitle, String source) {
        log.info("[RAG-Ingest] 开始摄入: docId={}, title={}, contentLen={}",
                documentId, documentTitle, content != null ? content.length() : 0);

        vectorStore.deleteByDocument(documentId);

        List<TextChunk> chunks = textChunker.chunk(content, documentId, documentTitle, source);
        if (chunks.isEmpty()) {
            log.warn("[RAG-Ingest] 分块结果为空: docId={}", documentId);
            return 0;
        }

        for (int i = 0; i < chunks.size(); i += EMBED_BATCH_SIZE) {
            int end = Math.min(i + EMBED_BATCH_SIZE, chunks.size());
            List<TextChunk> batch = chunks.subList(i, end);
            List<String> texts = batch.stream()
                    .map(TextChunk::getContent)
                    .collect(Collectors.toList());
            List<List<Float>> embeddings = embeddingClient.embedBatch(texts);
            for (int j = 0; j < batch.size(); j++) {
                TextChunk embedded = batch.get(j).withEmbedding(embeddings.get(j));
                vectorStore.store(embedded);
            }
        }

        log.info("[RAG-Ingest] 摄入完成: docId={}, chunks={}", documentId, chunks.size());
        return chunks.size();
    }

    /**
     * 删除文档的所有向量索引
     *
     * @param documentId 文档 ID
     */
    public void delete(String documentId) {
        vectorStore.deleteByDocument(documentId);
        log.info("[RAG-Ingest] 删除文档索引: docId={}", documentId);
    }

    /**
     * 获取向量存储统计
     */
    public VectorStoreStats getStats() {
        return new VectorStoreStats(vectorStore.count(), vectorStore.getType(),
                embeddingClient.getModel(), embeddingClient.getDimension());
    }

    /**
     * 向量存储的统计快照。
     *
     * @param totalChunks    存储的文本块总数
     * @param storeType      向量存储类型（pgvector / memory）
     * @param embeddingModel 使用的 Embedding 模型名称
     * @param dimension      向量维度
     */
    public record VectorStoreStats(long totalChunks, String storeType,
                                    String embeddingModel, int dimension) {}
}
