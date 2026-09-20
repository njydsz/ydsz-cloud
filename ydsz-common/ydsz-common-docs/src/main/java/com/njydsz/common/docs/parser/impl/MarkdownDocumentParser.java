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
 * Markdown 文档解析器 — 基于流式状态机的轻量结构提取。
 *
 * <p>输出 {@link DocumentSection} 类型包括 {@code heading / paragraph / list / code / link}， 类型字符串与 {@link
 * com.njydsz.common.docs.domain.DocumentSection#getType()} 约定一致。
 *
 * <p><b>识别范围（自 26.09.01 升级）：</b>
 *
 * <ul>
 *   <li>ATX 标题：行首 1–6 个 {@code #} + 空格
 *   <li>Setext 标题：其后紧跟 {@code ===}（H1）或 {@code ---}（H2）下划线的段落
 *   <li>围栏代码块：以 {@code ```} 开闭，进入时提取语言提示（如 {@code ```java}），块内内容 <b>不</b>再经
 *       markdown 行首语法匹配
 *   <li>无序列表：行首 {@code - / * / +} + 空格
 *   <li>有序列表：行首数字 + {@code .} + 空格；保留原始序号，以 {@link DocumentSection#getOrdered()} = true 区分
 *   <li>行内/独立链接：当一行（去空白后）恰好为 {@code [text](url)} 形式时，产出 {@code link} 类型 section， {@link
 *       DocumentSection#getUrl()} 保存 {@code url}，{@link DocumentSection#getContent()} 保存 {@code text}
 *   <li>YAML front matter：首行 {@code ---} 后跳过至第二个 {@code ---}
 * </ul>
 *
 * <p><b>不做：</b>强调/行内代码保留在 {@link DocumentSection#getContent()} 原文中；表格、Setext 中的多行前驱
 * （两行以上段落）直接按段落输出；任务列表未支持。
 *
 * <p><b>流式：</b>状态机逐行扫描，仅跟踪最近一行段落用于 Setext 判定，内存占用与文件大小无关。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

  // ======================== Patterns ========================

  /** Markdown ATX 标题正则：行首 1-6 个 # + 空格。 */
  private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

  /** Markdown 无序列表项正则：行首 - 或 *。 */
  private static final Pattern LIST_PATTERN = Pattern.compile("^[*\\-]\\s+(.+)$");

  /** Markdown 有序列表正则：行首数字 + . + 空格。 */
  private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.+)$");

  /** Markdown 围栏代码块边界：仅含 ``` 及可选语言提示。 */
  private static final Pattern FENCED_CODE_PATTERN = Pattern.compile("^```(\\w*)\\s*$");

  /** Setext 下划线：H1 (===) 或 H2 (---)。 */
  private static final Pattern SETEXT_PATTERN = Pattern.compile("^([-=])\\1*\\s*$");

  /** 独立的行内链接：[text](url)，去整行首尾空白后匹配。 */
  private static final Pattern LINK_PATTERN = Pattern.compile("^\\s*\\[[^\\]]+]\\([^)]+\\)\\s*$");

  /** YAML front matter 边界：单独的 ---。 */
  private static final String FRONT_MATTER_DELIMITER = "---";

  // ======================== Parser ========================

  /**
   * 解析 Markdown 字节流。
   *
   * <p>采用<b>流式状态机</b>而非构建完整 AST：目标只是提取粗粒度结构（标题/列表/代码块/链接）以供检索与 PII 扫描， 不需还原完整语法树，使内存占用与文件大小无关。
   *
   * <p><b>向后兼容说明：</b>本实现保留原有 {@code heading / list / paragraph} 行为；新增 {@code code / link} 类型只对相应输入产生，不会改变既有段落输出。
   *
   * @param inputStream Markdown 字节流（UTF-8 解码），由调用方负责关闭；可传空但不建议传 {@code null}
   * @param fileName 原始文件名，用作元数据标题
   * @param options 解析选项，本实现未使用，可传 {@code null}
   * @return 解析结果；sections 按原文件顺序排列
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@link DocumentExceptionCode#DOCUMENT_EMPTY}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    List<DocumentSection> sections = new ArrayList<>(16);
    StringBuilder fullText = new StringBuilder();
    MutableParseState state = new MutableParseState();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        processLine(line, sections, fullText, state);
      }
    } catch (IOException e) {
      log.error("[MarkdownDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    }

    // 若文件以 Open code-block 结尾，内容仍在当前 section 中，无需额外处理。

    String text = fullText.toString();
    return DocumentContent.builder()
        .text(text)
        .sections(sections)
        .metadata(DocumentMetadata.builder().title(fileName).charCount(text.length()).build())
        .totalChars(text.length())
        .totalPages(1)
        .build();
  }

  /** 解析单行并更新段落列表、全文累计器、解析状态机。 */
  private void processLine(
      String rawLine,
      List<DocumentSection> sections,
      StringBuilder fullText,
      MutableParseState state) {
    String trimmed = rawLine.trim();

    // 1. YAML front matter — 仅在第一行触发
    if (!state.frontMatterConsumed) {
      if (!state.inFrontMatter && FRONT_MATTER_DELIMITER.equals(trimmed) && sections.isEmpty()) {
        state.inFrontMatter = true;
        return;
      }
      if (state.inFrontMatter) {
        if (FRONT_MATTER_DELIMITER.equals(trimmed)) {
          state.inFrontMatter = false;
          state.frontMatterConsumed = true;
        }
        return;
      }
      // 首行不是 ---，后续不再走 front matter 路径
      state.frontMatterConsumed = true;
    }

    // 2. 围栏代码块状态
    Matcher fencedMatcher = FENCED_CODE_PATTERN.matcher(trimmed);
    if (fencedMatcher.matches()) {
      if (!state.inCodeBlock) {
        // 进入代码块
        state.inCodeBlock = true;
        sections.add(
            DocumentSection.builder()
                .type("code")
                .content("")
                .language(fencedMatcher.group(1))
                .pageNumber(1)
                .build());
        fullText.append(rawLine).append('\n');
      } else {
        // 退出代码块
        state.inCodeBlock = false;
        fullText.append(rawLine).append('\n');
      }
      return;
    }
    if (state.inCodeBlock) {
      appendToLastSection(sections, rawLine);
      fullText.append(rawLine).append('\n');
      return;
    }

    // 3. 空行 — 中断 Setext 累计，不输出段落
    if (trimmed.isEmpty()) {
      state.paragraphPreview = null;
      return;
    }

    // 4. Setext 下划线 — 将上一段 paragraph 升级为 heading
    Matcher setextMatcher = SETEXT_PATTERN.matcher(trimmed);
    if (setextMatcher.matches() && state.paragraphPreview != null) {
      char underlineChar = setextMatcher.group(1).charAt(0);
      int level = (underlineChar == '=') ? 1 : 2;
      String preview = state.paragraphPreview;
      // 替换上一个 paragraph section 为 heading
      if (!sections.isEmpty()) {
        DocumentSection last = sections.get(sections.size() - 1);
        last.setType("heading");
        last.setHeadingLevel(level);
        state.paragraphPreview = null;
      } else {
        sections.add(
            DocumentSection.builder()
                .type("heading")
                .headingLevel(level)
                .content(preview)
                .pageNumber(1)
                .build());
      }
      fullText.append(preview).append('\n');
      return;
    }
    // 下一行不是 setext，前一段 paragraphPreview 保留，继续作为普通 paragraph 输出

    // 5. ATX 标题
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
      state.paragraphPreview = null;
      fullText.append(text).append('\n');
      return;
    }

    // 6. 独立行链接
    Matcher linkMatcher = LINK_PATTERN.matcher(trimmed);
    if (linkMatcher.matches()) {
      String text = linkMatcher.group(1);
      String url = linkMatcher.group(2);
      sections.add(
          DocumentSection.builder()
              .type("link")
              .content(text)
              .url(url)
              .pageNumber(1)
              .build());
      state.paragraphPreview = null;
      fullText.append(text).append('\n');
      return;
    }

    // 7. 无序列表
    Matcher listMatcher = LIST_PATTERN.matcher(trimmed);
    if (listMatcher.matches()) {
      String text = listMatcher.group(1).trim();
      sections.add(
          DocumentSection.builder()
              .type("list")
              .content(text)
              .ordered(false)
              .pageNumber(1)
              .build());
      state.paragraphPreview = null;
      fullText.append("- ").append(text).append('\n');
      return;
    }

    // 8. 有序列表
    Matcher orderedListMatcher = ORDERED_LIST_PATTERN.matcher(trimmed);
    if (orderedListMatcher.matches()) {
      String text = orderedListMatcher.group(2).trim();
      sections.add(
          DocumentSection.builder()
              .type("list")
              .content(text)
              .ordered(true)
              .pageNumber(1)
              .build());
      state.paragraphPreview = null;
      fullText.append(text).append('\n');
      return;
    }

    // 9. 普通段落
    state.paragraphPreview = trimmed;
    sections.add(
        DocumentSection.builder()
            .type("paragraph")
            .content(trimmed)
            .pageNumber(1)
            .build());
    fullText.append(trimmed).append('\n');
  }

  /** 将一行原文追加到最后一个 section 内容末尾（用于代码块内容累积）。 */
  private void appendToLastSection(List<DocumentSection> sections, String line) {
    if (sections.isEmpty()) {
      return;
    }
    DocumentSection last = sections.get(sections.size() - 1);
    if ("code".equals(last.getType())) {
      String updated = last.getContent().isEmpty() ? line : last.getContent() + '\n' + line;
      last.setContent(updated);
    }
  }

  /**
   * 声明本解析器在注册中心占据的格式槽位。
   *
   * @return 恒为 {@link DocumentFormat#MARKDOWN}
   */
  @Override
  public DocumentFormat getSupportedFormat() {
    return DocumentFormat.MARKDOWN;
  }

  /** 解析中的可变状态机。使用独立对象避免方法参数过多、便于后续重构。 */
  private static final class MutableParseState {
    /** 进入/退出 YAML front matter 状态。 */
    private boolean inFrontMatter = false;

    /** 首行 front matter 判定完成后置 true。 */
    private boolean frontMatterConsumed = false;

    /** 围栏代码块开关。 */
    private boolean inCodeBlock = false;

    /** 最近一行普通段落预览，用于 Setext 判定。遇空行/Code/heading/list/link 等方式清空。 */
    private String paragraphPreview = null;
  }
}
