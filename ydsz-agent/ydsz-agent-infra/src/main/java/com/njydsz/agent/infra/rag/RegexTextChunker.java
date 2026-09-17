package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.TextChunker;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 正则分隔符分块器
 *
 * <p>按用户指定的正则表达式分隔符将文本切分为块，适用于具有明显分隔标记的文档类型（如 Markdown 标题、日志条目、法律条款等）。
 *
 * <h3>常用 separator 示例</h3>
 *
 * <ul>
 *   <li>{@code "\\n#{1,3}\\s"} — 按 Markdown H1-H3 标题分块
 *   <li>{@code "\\n#{2}\\s"} — 按 Markdown H2 标题分块（章节级别）
 *   <li>{@code "\\n-+\\s"} — 按分隔线分块
 *   <li>{@code "\\n\\d+\\.\\s"} — 按编号列表分块（如"1. "、"2. "）
 *   <li>{@code "\\n第[一二三四五六七八九十百千]+[章节]\\s"} — 按中文"第一章"等标题分块
 * </ul>
 *
 * <p>分块后，若单个块的字符数超过 {@code chunkSize}，则按段落进一步细分，确保每块大小合理。
 *
 * <p><b>线程安全</b>：{@link Pattern} 编译后不可变，可并发使用。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class RegexTextChunker implements TextChunker {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 最小段落长度（字符），低于此值与相邻段落合并 */
  private static final int MIN_SECTION_LENGTH = 50;

  /** 分隔符正则 Pattern */
  private final Pattern separatorPattern;

  /** 分块大小上限（字符数） */
  private final int chunkSize;

  /** 重叠字符数 */
  private final int overlap;

  /** 每 Token 对应的字符数估算 */
  private static final double CHARS_PER_TOKEN = 1.5;

  /**
   * 构造正则分隔符分块器。
   *
   * @param chunkSize 分块大小上限（字符数）
   * @param overlap 重叠字符数
   * @param separatorRegex 分隔符正则表达式字符串
   */
  public RegexTextChunker(int chunkSize, int overlap, String separatorRegex) {
    this.separatorPattern = Pattern.compile(separatorRegex != null ? separatorRegex : "\\n#{1,3}\\s");
    this.chunkSize = chunkSize > 100 ? chunkSize : 500;
    this.overlap = Math.min(overlap >= 0 ? overlap : 100, this.chunkSize / 2);
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
    List<TextChunk> chunks = new ArrayList<>(COLLECTION_CAPACITY);

    // 1. 按分隔符切分为若干段落
    List<String> sections = splitBySeparator(text);

    // 2. 合并过小段落、细分超大段落
    List<String> normalizedSections = normalizeSections(sections);

    // 3. 为每个最终段落生成 TextChunk
    int chunkIndex = 0;
    StringBuilder carryOver = new StringBuilder(); // 重叠携带
    for (String section : normalizedSections) {
      String content = carryOver + section;
      carryOver = new StringBuilder(extractOverlapTail(content));
      chunks.add(createChunk(content, documentId, documentTitle, source, chunkIndex++));
    }

    log.debug("[Regex-Chunker] 分块完成: docId={}, chunks={}, separator={}", documentId, chunks.size(), separatorPattern.pattern());
    return chunks;
  }

  /**
   * 按切分点找到所有分隔符位置（不实际分割，仅计算切点）。
   *
   * @return 各段落字符串列表
   */
  private List<String> splitBySeparator(String text) {
    List<String> sections = new ArrayList<>(COLLECTION_CAPACITY);
    Matcher matcher = separatorPattern.matcher(text);
    int lastEnd = 0;
    while (matcher.find()) {
      int matchStart = matcher.start();
      if (matchStart > lastEnd) {
        String section = text.substring(lastEnd, matchStart).trim();
        if (!section.isEmpty()) {
          sections.add(section);
        }
      }
      lastEnd = matcher.start(); // 从分隔符开始下一段（包含分隔符本身在下一段头部）
    }
    // 追加尾段
    if (lastEnd < text.length()) {
      String tail = text.substring(lastEnd).trim();
      if (!tail.isEmpty()) {
        sections.add(tail);
      }
    }
    // 无切分时整体返回
    if (sections.isEmpty()) {
      sections.add(text.trim());
    }
    return sections;
  }

  /**
   * 规范化段落：合并不及下限的小段落、细分超大段落。
   *
   * @param sections 原始段落列表
   * @return 规范化后的段落列表
   */
  private List<String> normalizeSections(List<String> sections) {
    List<String> result = new ArrayList<>(sections.size());
    StringBuilder buffer = new StringBuilder();
    for (String section : sections) {
      if (section.length() > chunkSize) {
        // 先刷入 buffer 中累积的内容
        if (buffer.length() > 0) {
          result.add(buffer.toString());
          buffer = new StringBuilder();
        }
        // 超大段落按 chunkSize 硬切（保留重叠）
        for (int i = 0; i < section.length(); i += chunkSize - overlap) {
          int end = Math.min(i + chunkSize, section.length());
          result.add(section.substring(i, end));
          if (end >= section.length()) {
            break;
          }
        }
      } else if (buffer.length() + section.length() > chunkSize && buffer.length() > 0) {
        // buffer 已满，刷入
        result.add(buffer.toString());
        buffer = new StringBuilder(section);
      } else {
        if (buffer.length() > 0) {
          buffer.append("\n");
        }
        buffer.append(section);
      }
    }
    if (buffer.length() > 0) {
      result.add(buffer.toString());
    }
    return result;
  }

  /**
   * 提取内容尾部的重叠区域，用于携带至下一块。
   *
   * @param content 当前块内容
   * @return 尾部 overlap 长度的字符串
   */
  private String extractOverlapTail(String content) {
    if (content.length() <= overlap) {
      return content;
    }
    return content.substring(content.length() - overlap);
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
        Map.of("chunkType", "regex"),
        null);
  }

  private int estimateTokens(String text) {
    return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
  }
}
