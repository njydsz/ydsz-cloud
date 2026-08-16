package com.njydsz.common.docs.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;

/**
 * Markdown 文档解析器
 *
 * <p>解析 Markdown 文件，提取标题层级、段落、列表等结构化内容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

  /** Markdown 标题正则 */
  private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

  /** Markdown 列表项正则 */
  private static final Pattern LIST_PATTERN = Pattern.compile("^[*\\-+]\\s+(.+)$");

  /** Markdown 有序列表项正则 */
  private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+\\.\\s+(.+)$");

  /**
   * 逐行正则匹配 Markdown 语法，抽取标题层级、列表与段落。
   *
   * <p>采用<b>流式逐行</b>而非构建 AST 的方式：目标只是提取纯文本与粗粒度结构以供检索， 不需要还原完整语法树，逐行处理可保持内存占用与文件大小无关。
   * 代价是只识别行首语法，行内的加粗、链接、行内代码等标记会<b>原样保留</b>在文本中。
   *
   * <p><b>已知边界：</b>不做围栏代码块（{@code ```}）状态跟踪， 代码块内以 {@code #} 或 {@code -} 开头的行会被误判为标题或列表； 同理不支持
   * Setext 式下划线标题、表格语法与 YAML front matter。 有序列表项去掉序号后与无序列表统一标记为 {@code list} 类型，原始序号不保留。
   *
   * @param inputStream Markdown 字节流，由调用方负责关闭； 固定按 UTF-8 解码，不受 {@code options.charset} 影响
   * @param fileName 原始文件名，用作元数据标题；<b>不</b>取文中首个一级标题
   * @param options 解析选项，本实现未使用，可传 {@code null}
   * @return 文档内容，分节按原文行序排列（与 HTML 解析器不同，此处顺序可靠）；页数恒为 1
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}； 读取失败时错误码 {@code
   *     PARSE_FAILED}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    List<DocumentSection> sections = new ArrayList<>();
    StringBuilder fullText = new StringBuilder();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();

        // 标题
        Matcher headingMatcher = HEADING_PATTERN.matcher(trimmed);
        if (headingMatcher.matches()) {
          int level = headingMatcher.group(1).length();
          String text = headingMatcher.group(2).trim();
          sections.add(
              DocumentSection.builder()
                  .type("heading")
                  .headingLevel(level)
                  .content(text)
                  .pageNumber(1)
                  .build());
          fullText.append(text).append('\n');
          continue;
        }

        // 无序列表
        Matcher listMatcher = LIST_PATTERN.matcher(trimmed);
        if (listMatcher.matches()) {
          String text = listMatcher.group(1).trim();
          sections.add(DocumentSection.builder().type("list").content(text).pageNumber(1).build());
          fullText.append("- ").append(text).append('\n');
          continue;
        }

        // 有序列表
        Matcher orderedListMatcher = ORDERED_LIST_PATTERN.matcher(trimmed);
        if (orderedListMatcher.matches()) {
          String text = orderedListMatcher.group(1).trim();
          sections.add(DocumentSection.builder().type("list").content(text).pageNumber(1).build());
          fullText.append(text).append('\n');
          continue;
        }

        // 普通段落
        if (!trimmed.isEmpty()) {
          sections.add(
              DocumentSection.builder().type("paragraph").content(trimmed).pageNumber(1).build());
          fullText.append(trimmed).append('\n');
        }
      }
    } catch (IOException e) {
      log.error("[MarkdownDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    }

    String text = fullText.toString();
    return DocumentContent.builder()
        .text(text)
        .sections(sections)
        .metadata(DocumentMetadata.builder().title(fileName).charCount(text.length()).build())
        .totalChars(text.length())
        .totalPages(1)
        .build();
  }

  /**
   * 声明本解析器在注册中心占据的格式槽位。
   *
   * <p>本类未覆写 {@code supports}，仅精确匹配 MARKDOWN。 由于 {@link DocumentFormat} 只为该格式登记了 {@code md} 扩展名，
   * {@code .markdown} 后缀的文件不会被路由到这里。
   *
   * @return 恒为 {@link DocumentFormat#MARKDOWN}
   */
  @Override
  public DocumentFormat getSupportedFormat() {
    return DocumentFormat.MARKDOWN;
  }
}
