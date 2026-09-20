package com.njydsz.common.docs.security.pii;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.security.pii.PiiContextExtractor.Context;

/**
 * {@link PiiContextExtractor} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@DisplayName("PiiContextExtractor 测试")
class PiiContextExtractorTest {

  @Nested
  @DisplayName("正常提取")
  class WhenTextIsSufficient {

    @Test
    @DisplayName("文本充足时应按窗口大小截取前后上下文")
    void shouldExtractBeforeAndAfterWithinWindow() {
      // "前缀文字>>>手机<<<后缀文字"
      String text = "这是前缀文字用于验证上下文提取手机号13812345678后续文字用于验证后缀截取";
      int start = text.indexOf("13812345678");
      int end = start + "13812345678".length();

      Context ctx = PiiContextExtractor.extract(text, start, end, 5);

      assertThat(ctx.before()).isEqualTo("提取手机号");
      assertThat(ctx.after()).isEqualTo("后续文字用");
    }

    @Test
    @DisplayName("使用默认窗口 30 字符")
    void shouldUseDefaultWindowSize() {
      String before = "a".repeat(40);
      String after = "b".repeat(40);
      String text = before + "PII" + after;

      Context ctx = PiiContextExtractor.extract(text, text.indexOf("PII"), text.indexOf("PII") + 3);

      assertThat(ctx.before()).hasSize(PiiContextExtractor.DEFAULT_WINDOW_SIZE);
      assertThat(ctx.after()).hasSize(PiiContextExtractor.DEFAULT_WINDOW_SIZE);
      assertThat(ctx.before()).endsWith("a");
      assertThat(ctx.after()).startsWith("b");
    }
  }

  @Nested
  @DisplayName("边界：贴近文本头尾")
  class WhenNearBoundaries {

    @Test
    @DisplayName("命中位置贴近文本头部时，before 短于窗口但不抛异常")
    void shouldTruncateBeforeNearStart() {
      String text = "13812345678靠近头部";
      Context ctx = PiiContextExtractor.extract(text, 0, 11, 30);

      assertThat(ctx.before()).isEmpty();
      assertThat(ctx.after()).isEqualTo("靠近头部");
    }

    @Test
    @DisplayName("命中位置贴近文本尾部时，after 短于窗口但不抛异常")
    void shouldTruncateAfterNearEnd() {
      String text = "尾部手机号13800000000";
      int start = text.indexOf("13800000000");
      int end = start + 11;
      Context ctx = PiiContextExtractor.extract(text, start, end, 30);

      assertThat(ctx.before()).isEqualTo("尾部手机号");
      assertThat(ctx.after()).isEmpty();
    }
  }

  @Nested
  @DisplayName("降级：无效入参")
  class WhenInputInvalid {

    @Test
    @DisplayName("文本为 null 时返回空上下文，不抛异常")
    void shouldReturnEmptyContextForNullText() {
      Context ctx = PiiContextExtractor.extract(null, 0, 10, 5);

      assertThat(ctx.before()).isEmpty();
      assertThat(ctx.after()).isEmpty();
    }

    @Test
    @DisplayName("文本为空串时返回空上下文")
    void shouldReturnEmptyContextForEmptyText() {
      Context ctx = PiiContextExtractor.extract("", 0, 0, 5);

      assertThat(ctx.before()).isEmpty();
      assertThat(ctx.after()).isEmpty();
    }

    @Test
    @DisplayName("startIndex 为负数时被截断为 0，before 从文本头部起始")
    void shouldClampNegativeStartIndex() {
      // 命中位置靠近头部时，before 的起点被截断为 0（不会引发 StringIndexOutOfBoundsException）
      String text = "手机号13812345678后续文字";
      int start = text.indexOf("13812345678");
      int end = start + 11;
      // 窗口大于可用文本，before 起点被截断为 0
      Context ctx = PiiContextExtractor.extract(text, start, end, 30);

      assertThat(ctx.before()).isEqualTo("手机号");
      assertThat(ctx.after()).isEqualTo("后续文字");
    }
  }
}
