package com.njydsz.pmis.agent.domain.rag;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文本块值对象
 *
 * <p>文档分块后的最小检索单元，包含文本内容、来源信息和向量嵌入。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public final class TextChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String content;
    private final String documentId;
    private final String documentTitle;
    private final String source;
    private final int chunkIndex;
    private final int tokenCount;
    private final Map<String, Object> metadata;
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
