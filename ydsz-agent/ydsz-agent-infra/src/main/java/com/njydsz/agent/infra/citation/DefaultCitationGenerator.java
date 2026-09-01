package com.njydsz.agent.infra.citation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.citation.Citation;
import com.njydsz.agent.domain.citation.CitationGenerator;
import com.njydsz.agent.domain.rag.TextChunk;

/**
 * 默认引用生成器实现。
 *
 * <p>从 TextChunk 的元数据中提取引用信息，转换为 Citation 对象。
 * 支持从 chunk 的 metadata 中读取 documentId、documentTitle、sourcePath 等信息。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Component
public class DefaultCitationGenerator implements CitationGenerator {

    private static final String METADATA_SOURCE_PATH = "sourcePath";
    private static final String METADATA_SCORE = "score";
    private static final int MAX_EXCERPT_LENGTH = 200;

    @Override
    public List<Citation> generateCitations(List<TextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(this::generateCitation)
                .toList();
    }

    @Override
    public Citation generateCitation(TextChunk chunk) {
        if (chunk == null) {
            return null;
        }

        Map<String, Object> metadata = chunk.getMetadata();
        String sourcePath = extractString(metadata, METADATA_SOURCE_PATH, "");
        double score = extractDouble(metadata, METADATA_SCORE, 0.0);
        String excerpt = truncateExcerpt(chunk.getContent());

        return new Citation(
                chunk.getDocumentId(),
                chunk.getDocumentTitle(),
                chunk.getChunkIndex(),
                excerpt,
                score,
                sourcePath
        );
    }

    /**
     * 从元数据中安全提取字符串值。
     */
    private String extractString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 从元数据中安全提取双精度浮点值。
     */
    private double extractDouble(Map<String, Object> metadata, String key, double defaultValue) {
        if (metadata == null || !metadata.containsKey(key)) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 截断文本摘录到合理长度。
     */
    private String truncateExcerpt(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_EXCERPT_LENGTH) + "...";
    }
}
