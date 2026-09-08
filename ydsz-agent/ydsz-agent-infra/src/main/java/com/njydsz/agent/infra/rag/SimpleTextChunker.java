package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.TextChunker;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 固定大小分块器（带重叠）
 *
 * <p>将文本按固定字符数切分，相邻块之间有重叠（overlap），确保语义连续性。
 *
 * <h3>策略</h3>
 *
 * <ul>
 *   <li>按段落（双换行）自然分块
 *   <li>段落超过 chunkSize 时按句子分割
 *   <li>相邻块之间保留 overlap 比例的重叠
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SimpleTextChunker implements TextChunker {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /** 默认分块大小（字符数） */
  private static final int DEFAULT_CHUNK_SIZE = 500;

  /** 默认重叠大小（字符数） */
  private static final int DEFAULT_OVERLAP = 50;

  /** 平均每个 Token 对应的字符数（用于估算 Token 数） */
  private static final double CHARS_PER_TOKEN = 1.5;

  /** 分块大小（字符数） */
  private final int chunkSize;

  /** 重叠大小（字符数） */
  private final int overlap;

  /**
   * 构造默认参数的分块器（chunkSize=500, overlap=50）。
   */
  public SimpleTextChunker() {
    this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
  }

  /**
   * 构造指定参数的分块器。
   *
   * @param chunkSize 分块大小（字符数，<100 时使用默认值）
   * @param overlap 重叠字符数（超过 chunkSize/2 时取半）
   */
  public SimpleTextChunker(int chunkSize, int overlap) {
    this.chunkSize = chunkSize > 100 ? chunkSize : DEFAULT_CHUNK_SIZE;
    this.overlap = Math.min(overlap >= 0 ? overlap : DEFAULT_OVERLAP, chunkSize / 2);
  }

  /**
   * 将标题/来源为空的分块委托给四参数版本。
   *
   * @param text 原始文本
   * @param documentId 文档 ID
   * @return 分块列表
   */
  @Override
  public List<TextChunk> chunk(String text, String documentId) {
    return chunk(text, documentId, null, null);
  }

  /**
   * 将文本切分为固定大小、带重叠的文本块。
   *
   * <p>按段落自然分块，超长段落按句子分割；相邻块之间保留 overlap 字符的重叠区域，确保语义连续性。
   *
   * @param text 待切分的原始文本（为 {@code null} 或空白时返回空列表）
   * @param documentId 文档 ID（写入每个 chunk）
   * @param documentTitle 文档标题（可 {@code null}）
   * @param source 来源标识（可 {@code null}）
   * @return 按原文顺序排列的文本块列表
   */
  @Override
  public List<TextChunk> chunk(
      String text, String documentId, String documentTitle, String source) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<TextChunk> chunks = new ArrayList<>(COLLECTION_CAPACITY);
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

  private TextChunk createChunk(
      String content, String documentId, String documentTitle, String source, int chunkIndex) {
    return new TextChunk(
        IdGenerator.nextIdStr(),
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
    List<String> segments = new ArrayList<>(COLLECTION_CAPACITY);
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
    return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
  }
}
