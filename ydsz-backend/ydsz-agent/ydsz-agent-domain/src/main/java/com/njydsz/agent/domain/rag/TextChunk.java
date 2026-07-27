package com.njydsz.agent.domain.rag;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文本块值对象
 *
 * <p>文档分块后的最小检索单元，包含文本内容、来源信息和向量嵌入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TextChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文本块唯一标识 */
    private final String id;
    /** 文本内容 */
    private final String content;
    /** 所属文档 ID */
    private final String documentId;
    /** 所属文档标题 */
    private final String documentTitle;
    /** 来源标识（nextwiki/project/contract） */
    private final String source;
    /** 分块索引（在文档中的序号） */
    private final int chunkIndex;
    /** Token 数量 */
    private final int tokenCount;
    /** 元数据（自定义键值对） */
    private final Map<String, Object> metadata;
    /** 向量嵌入（null 表示未向量化） */
    private final List<Float> embedding;

    public TextChunk(String id, String content, String documentId, String documentTitle,
                     String source, int chunkIndex, int tokenCount,
                     Map<String, Object> metadata, List<Float> embedding) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.content = Objects.requireNonNull(content, "content 不能为 null");
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.source = source;
        this.chunkIndex = chunkIndex;
        this.tokenCount = tokenCount;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.embedding = embedding != null ? List.copyOf(embedding) : null;
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public String getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public String getSource() { return source; }
    public int getChunkIndex() { return chunkIndex; }
    public int getTokenCount() { return tokenCount; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<Float> getEmbedding() { return embedding; }

    public boolean hasEmbedding() {
        return embedding != null && !embedding.isEmpty();
    }

    public TextChunk withEmbedding(List<Float> newEmbedding) {
        return new TextChunk(id, content, documentId, documentTitle, source,
                chunkIndex, tokenCount, metadata, newEmbedding);
    }

    @Override
    public String toString() {
        return "TextChunk{id='" + id + "', docId='" + documentId + "', idx=" + chunkIndex +
                ", tokens=" + tokenCount + ", embedded=" + hasEmbedding() + "}";
    }
}
