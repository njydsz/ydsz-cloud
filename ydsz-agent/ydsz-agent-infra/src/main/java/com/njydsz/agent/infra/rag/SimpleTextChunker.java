package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.TextChunker;

import java.util.Map;
/**
 * 固定大小分块器（带重叠）
 *
 * <p>将文本按固定字符数切分，相邻块之间有重叠（overlap），确保语义连续性。
 *
 * <h3>策略</h3>
 * <ul>
 *   <li>按段落（双换行）自然分块</li>
 *   <li>段落超过 chunkSize 时按句子分割</li>
 *   <li>相邻块之间保留 overlap 比例的重叠</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SimpleTextChunker implements TextChunker {

    private static final Logger log = LoggerFactory.getLogger(SimpleTextChunker.class);
    /** 默认分块大小（字符数） */
    private static final int DEFAULT_CHUNK_SIZE = 500;
    /** 默认重叠大小（字符数） */
    private static final int DEFAULT_OVERLAP = 50;

    /** 分块大小（字符数） */
    private final int chunkSize;
    /** 重叠大小（字符数） */
    private final int overlap;

    public SimpleTextChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public SimpleTextChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize > 100 ? chunkSize : DEFAULT_CHUNK_SIZE;
        this.overlap = Math.min(overlap >= 0 ? overlap : DEFAULT_OVERLAP, chunkSize / 2);
    }

    @Override
    public List<TextChunk> chunk(String text, String documentId) {
        return chunk(text, documentId, null, null);
    }

    @Override
    public List<TextChunk> chunk(String text, String documentId, String documentTitle, String source) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<TextChunk> chunks = new ArrayList<>();
        List<String> segments = splitByParagraph(text);
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;

        for (String segment : segments) {
            if (buffer.length() + segment.length() > chunkSize && buffer.length() > 0) {
                String content = buffer.toString().trim();
                chunks.add(createChunk(content, documentId, documentTitle, source, chunkIndex++));
                String overlapText = buffer.substring(Math.max(0, buffer.length() - overlap));
                buffer = new StringBuilder(overlapText);
            }
            buffer.append(segment);
            if (!segment.endsWith("\n")) {
                buffer.append("\n\n");
            }
        }
        if (buffer.length() > 0) {
            String content = buffer.toString().trim();
            if (!content.isEmpty()) {
                chunks.add(createChunk(content, documentId, documentTitle, source, chunkIndex));
            }
        }
        log.debug("[Chunker] 分块完成: docId={}, chunks={}", documentId, chunks.size());
        return chunks;
    }

    private TextChunk createChunk(String content, String documentId, String documentTitle,
                                   String source, int chunkIndex) {
        return new TextChunk(
                UUID.randomUUID().toString(),
                content,
                documentId,
                documentTitle,
                source,
                chunkIndex,
                estimateTokens(content),
                Map.of(),
                null);
    }

    private List<String> splitByParagraph(String text) {
        List<String> segments = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");
        for (String para : paragraphs) {
            if (para.length() > chunkSize) {
                String[] sentences = para.split("(?<=[。！？.!?;；])");
                for (String sentence : sentences) {
                    if (!sentence.isBlank()) {
                        segments.add(sentence.trim());
                    }
                }
            } else if (!para.isBlank()) {
                segments.add(para.trim());
            }
        }
        return segments;
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 1.5);
    }
}
