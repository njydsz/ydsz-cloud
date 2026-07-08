package com.njydsz.pmis.agent.rag;

import java.util.List;

/**
 * 向量存储抽象接口（P3-1 落地）。
 *
 * <p>对标 Spring AI VectorStore / LangChain VectorStore，封装向量存储与检索能力。
 * 实现可选择：
 * <ul>
 *   <li>{@link InMemoryVectorStore} - 内存存储（单元测试用）</li>
 *   <li>{@code PgVectorStore} - PostgreSQL + pgvector（生产用，通过 DocumentChunkMapper 实现）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
public interface VectorStore {

    /**
     * 存储向量。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId       文档 ID
     * @param chunkIndex       分块序号
     * @param content          文本内容
     * @param embedding        向量
     * @param tokenCount       token 数
     * @return 分块 ID
     */
    String store(String knowledgeBaseId, String documentId, int chunkIndex,
                 String content, float[] embedding, int tokenCount);

    /**
     * 向量检索：按余弦相似度降序返回 top-k 分块。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param queryVector      查询向量
     * @param topK             返回条数
     * @return 匹配的分块列表
     */
    List<RetrievedChunk> search(String knowledgeBaseId, float[] queryVector, int topK);

    /**
     * 删除指定文档的所有分块。
     *
     * @param documentId 文档 ID
     * @return 删除的分块数
     */
    int deleteByDocument(String documentId);

    /**
     * 删除指定知识库的所有分块。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 删除的分块数
     */
    int deleteByKnowledgeBase(String knowledgeBaseId);

    /**
     * 统计指定知识库的分块数。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 分块数
     */
    int countByKnowledgeBase(String knowledgeBaseId);
}
