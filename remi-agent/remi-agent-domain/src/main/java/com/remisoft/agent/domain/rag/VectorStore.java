package com.remisoft.agent.domain.rag;

import java.util.List;

/**
 * 向量存储接口
 *
 * <p>抽象向量数据的存储与相似度检索。实现可选择：
 * <ul>
 *   <li>PostgreSQL pgvector（复用现有 PG 基础设施）</li>
 *   <li>Milvus / Qdrant / Weaviate（专用向量数据库）</li>
 *   <li>内存（测试/降级）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：向量库通常为多请求共享的远程服务或单例，实现须保证 store/search/delete 的并发安全，
 * search 返回列表不应暴露内部可变引用。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface VectorStore {

    /**
     * 存储一个文本块（含嵌入向量）
     *
     * @param chunk 文本块
     */
    void store(TextChunk chunk);

    /**
     * 批量存储文本块
     *
     * @param chunks 文本块列表
     */
    void storeBatch(List<TextChunk> chunks);

    /**
     * 相似度检索
     *
     * @param query   查询文本（将自动生成嵌入）
     * @param topK    返回前 K 条
     * @param minScore 最小相似度阈值（0-1）
     * @return 相似文本块列表（按相似度降序）
     */
    List<TextChunk> search(String query, int topK, double minScore);

    /**
     * 按向量相似度检索
     *
     * @param embedding 查询向量
     * @param topK      返回前 K 条
     * @param minScore  最小相似度阈值
     * @return 相似文本块列表
     */
    List<TextChunk> searchByVector(List<Float> embedding, int topK, double minScore);

    /**
     * 删除指定文档的所有文本块
     *
     * @param documentId 文档 ID
     */
    void deleteByDocument(String documentId);

    /**
     * 获取已存储的文本块总数
     *
     * @return 文本块数
     */
    long count();

    /**
     * 存储类型标识
     *
     * @return 如 "pgvector"、"milvus"、"memory"
     */
    String getType();

    /**
     * 检查存储是否可用
     *
     * @return true=可用
     */
    boolean isAvailable();
}
