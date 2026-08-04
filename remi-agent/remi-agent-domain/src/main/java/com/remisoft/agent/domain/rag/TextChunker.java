package com.remisoft.agent.domain.rag;

import java.util.List;

/**
 * 文本分块器接口
 *
 * <p>将长文本切分为合适大小的块（chunk），以便向量化处理。
 *
 * <p><b>线程安全</b>：分块器通常为无状态单例，实现须保证并发 chunk 调用的线程安全（不依赖可变实例字段）。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface TextChunker {

    /**
     * 将文本分块
     *
     * @param text       原始文本
     * @param documentId 文档 ID（用于关联）
     * @return 文本块列表
     */
    List<TextChunk> chunk(String text, String documentId);

    /**
     * 将文本分块（含文档元信息）
     *
     * @param text           原始文本
     * @param documentId     文档 ID
     * @param documentTitle  文档标题
     * @param source         来源（如 "nextwiki"、"project"）
     * @return 文本块列表
     */
    List<TextChunk> chunk(String text, String documentId, String documentTitle, String source);
}
