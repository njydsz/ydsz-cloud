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

  private static void assertHeading(DocumentSection section, int level, String text) {
    assertThat(section.getType()).isEqualTo("heading");
    assertThat(section.getHeadingLevel()).isEqualTo(level);
    assertThat(section.getContent()).isEqualTo(text);
  }
}
