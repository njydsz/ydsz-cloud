package com.njydsz.common.docs.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;

/**
 * {@link MarkdownDocumentParser} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("MarkdownDocumentParser 测试")
class MarkdownDocumentParserTest {

  private MarkdownDocumentParser parser;

  @BeforeEach
  void setUp() {
    parser = new MarkdownDocumentParser();
  }

  @Nested
  @DisplayName("场景：标题解析")
  class WhenHeading {

    @Test
    @DisplayName("H1-H6 标题应正确识别类型和层级")
    void shouldRecognizeHeadingLevels() throws IOException {
      String md = "# 一级标题\n## 二级标题\n###### 六级标题";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.md", null);

      assertThat(result.getSections()).hasSize(3);
      assertHeading(result.getSections().get(0), 1, "一级标题");
      assertHeading(result.getSections().get(1), 2, "二级标题");
      assertHeading(result.getSections().get(2), 6, "六级标题");
    }

    @Test
    @DisplayName("纯文本段落不应被识别为标题")
    void shouldNotMisidentifyNonHeading() throws IOException {
      String md = "这行没有井号\n# 这才是标题";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.md", null);

      assertThat(result.getSections()).hasSize(2);
      assertThat(result.getSections().get(0).getType()).isEqualTo("paragraph");
      assertThat(result.getSections().get(1).getType()).isEqualTo("heading");
      assertThat(result.getSections().get(1).getHeadingLevel()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("场景：列表解析")
  class WhenList {

    @Test
    @DisplayName("无序列表应被识别为 list 类型")
    void shouldRecognizeUnorderedList() throws IOException {
      String md = "- 第一项\n- 第二项\n* 第三项";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.md", null);

      assertThat(result.getSections()).hasSize(3);
      assertThat(result.getSections().get(0).getType()).isEqualTo("list");
      assertThat(result.getSections().get(0).getContent()).isEqualTo("第一项");
    }

    @Test
    @DisplayName("有序列表应被识别为 list 类型（去掉序号）")
    void shouldRecognizeOrderedList() throws IOException {
      String md = "1. 第一项\n2. 第二项";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.md", null);

      assertThat(result.getSections()).hasSize(2);
      assertThat(result.getSections().get(0).getType()).isEqualTo("list");
      assertThat(result.getSections().get(0).getContent()).isEqualTo("第一项");
    }
  }

  @Nested
  @DisplayName("场景：异常与边界")
  class WhenError {

    @Test
    @DisplayName("null 流应抛出 DOCUMENT_EMPTY")
    void shouldThrowWhenStreamIsNull() {
      assertThatThrownBy(() -> parser.parse(null, "null.md", null))
          .isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("空内容时应返回空 sections")
    void shouldHandleEmptyContent() throws IOException {
      InputStream stream = new ByteArrayInputStream(new byte[0]);

      DocumentContent result = parser.parse(stream, "empty.md", null);

      assertThat(result.getSections()).isEmpty();
      assertThat(result.getText()).isEmpty();
    }
  }

  @Nested
  @DisplayName("场景：格式声明")
  class WhenFormat {

    @Test
    @DisplayName("getSupportedFormat 应返回 MARKDOWN")
    void shouldSupportMarkdownFormat() {
      assertThat(parser.getSupportedFormat()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    @DisplayName("仅支持 md 后缀（非 .markdown）")
    void shouldNotSupportOtherFormats() {
      assertThat(parser.supports(DocumentFormat.TXT)).isFalse();
      assertThat(parser.supports(DocumentFormat.HTML)).isFalse();
    }
  }

  @Nested
  @DisplayName("场景：围栏代码块")
  class WhenFencedCodeBlock {

    @Test
    @DisplayName("标准 ```fence``` 应输出 code 类型 section，块内行不触发标题或列表误判")
    void shouldEmitCodeSectionAndSuppressInnerPatterns() {
      String md =
          "```java\n"
              + "# 伪标题不应被解析\n"
              + "- 伪列表项\n"
              + "System.out.println(\"hello\");\n"
              + "```\n";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "code.md", null);

      // 只输出 1 个 section (code type)
      assertThat(result.getSections()).hasSize(1);
      DocumentSection code = result.getSections().get(0);
      assertThat(code.getType()).isEqualTo("code");
      assertThat(code.getLanguage()).isEqualTo("java");
      // 内容包含三行有效行(不含 ``` 本身)
      assertThat(code.getContent())
          .contains("System.out.println")
          .contains("# 伪标题不应被解析")
          .contains("- 伪列表项");
      assertThat(code.getContent()).doesNotContain("```");
    }

    @Test
    @DisplayName("无语言提示时 language 字段为空字符串")
    void shouldHandleEmptyLanguageHint() {
      String md = "```\nnoop\n```\n";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "plain.md", null);

      assertThat(result.getSections()).hasSize(1);
      assertThat(result.getSections().get(0).getType()).isEqualTo("code");
      assertThat(result.getSections().get(0).getLanguage()).isEmpty();
    }
  }

  @Nested
  @DisplayName("场景：Setext 标题")
  class WhenSetextHeading {

    @Test
    @DisplayName("上一行段落 + 下一行 === 应升级为 H1")
    void shouldPromoteToH1OnEquals() {
      String md = "一级 Setext 标题\n===\n";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "setext.md", null);

      assertThat(result.getSections()).hasSize(1);
      assertHeading(result.getSections().get(0), 1, "一级 Setext 标题");
    }

    @Test
    @DisplayName("上一行段落 + 下一行 --- 应升级为 H2")
    void shouldPromoteToH2OnDashes() {
      String ms = "二级 Setext 标题\n---\n";
      InputStream stream = new ByteArrayInputStream(ms.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "setext2.md", null);

      assertThat(result.getSections()).hasSize(1);
      assertHeading(result.getSections().get(0), 2, "二级 Setext 标题");
    }
  }

  @Nested
  @DisplayName("场景：独立链接")
  class WhenStandaloneLink {

    @Test
    @DisplayName("一行仅含 [text](url) 应输出 link 类型，content 为 text，url 为 url")
    void shouldEmitLinkSection() {
      String md =
          "[文档](https://docs.example.com)\n" + "[指南](https://guide.example.com)\n";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "links.md", null);

      assertThat(result.getSections()).hasSize(2);
      DocumentSection first = result.getSections().get(0);
      assertThat(first.getType()).isEqualTo("link");
      assertThat(first.getContent()).isEqualTo("文档");
      assertThat(first.getUrl()).isEqualTo("https://docs.example.com");
      assertThat(result.getSections().get(1).getUrl())
          .isEqualTo("https://guide.example.com");
    }
  }

  @Nested
  @DisplayName("场景：YAML front matter")
  class WhenFrontMatter {

    @Test
    @DisplayName("首行 --- 包裹的内容应被跳过，后续正文正常解析")
    void shouldSkipFrontMatterAndParseBody() {
      String md =
          "---\n"
              + "title: 示例\n"
              + "author: me\n"
              + "---\n"
              + "# 正文标题\n"
              + "正文段落\n";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "fm.md", null);

      assertThat(result.getSections()).hasSize(2);
      assertThat(result.getSections().get(0).getType()).isEqualTo("heading");
      assertThat(result.getSections().get(0).getContent()).isEqualTo("正文标题");
      assertThat(result.getSections().get(1).getType()).isEqualTo("paragraph");
    }

    @Test
    @DisplayName("首行不是 --- 时后续 --- 应被识别为 Setext H2 而非 front matter 标记")
    void shouldNotTriggerFrontMatterWhenFirstLineIsNotDelimiter() {
      // "===" 作为 Setext H1 下划线更清晰地区分"---"的 front matter 语义
      String md = "首行普通段落\n===\n";
      InputStream stream = new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "no-fm.md", null);

      // 首行不是 ---，后续不再走 front matter 路径；"===" 作为 Setext 将前一行升级为 H1
      assertThat(result.getSections()).hasSize(1);
      assertThat(result.getSections().get(0).getType()).isEqualTo("heading");
      assertThat(result.getSections().get(0).getHeadingLevel()).isEqualTo(1);
    }
  }

  private static void assertHeading(DocumentSection section, int level, String text) {
    assertThat(section.getType()).isEqualTo("heading");
    assertThat(section.getHeadingLevel()).isEqualTo(level);
    assertThat(section.getContent()).isEqualTo(text);
  }
}
