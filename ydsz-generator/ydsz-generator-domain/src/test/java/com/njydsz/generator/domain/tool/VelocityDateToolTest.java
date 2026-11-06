package com.njydsz.generator.domain.tool;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VelocityDateTool} 单元测试。
 *
 * <p>覆盖：format（含 null pattern 回退）、year/month/day 范围、compactDate 格式、now/today 非空。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("Velocity 日期工具")
class VelocityDateToolTest {

  private final VelocityDateTool tool = new VelocityDateTool();

  @Nested
  @DisplayName("format(LocalDateTime, pattern)")
  class Format {

    @ParameterizedTest
    @CsvSource({
      "2026-11-06T12:30:45, yyyy-MM-dd, 2026-11-06",
      "2026-11-06T12:30:45, yyyy-MM-dd HH:mm:ss, 2026-11-06 12:30:45",
      "2026-11-06T00:00:00, yyyyMMdd, 20261106"
    })
    @DisplayName("按 pattern 格式化")
    void formats(String iso, String pattern, String expected) {
      LocalDateTime dt = LocalDateTime.parse(iso);
      assertThat(tool.format(dt, pattern)).isEqualTo(expected);
    }

    @Test
    @DisplayName("dateTime=null 返回空串")
    void nullDateTime() {
      assertThat(tool.format(null, "yyyy-MM-dd")).isEmpty();
    }

    @Test
    @DisplayName("pattern=null 回退默认格式")
    void nullPatternFallback() {
      LocalDateTime dt = LocalDateTime.of(2026, 11, 6, 12, 30, 45);
      assertThat(tool.format(dt, null)).isEqualTo("2026-11-06 12:30:45");
    }
  }

  @Nested
  @DisplayName("now/today/compactDate")
  class CurrentDate {

    @Test
    @DisplayName("now 返回 yyyy-MM-dd HH:mm:ss 格式")
    void nowFormat() {
      assertThat(tool.now()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("today 返回 yyyy-MM-dd 格式")
    void todayFormat() {
      assertThat(tool.today()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    @DisplayName("compactDate 返回 yyyyMMdd 格式")
    void compactFormat() {
      assertThat(tool.compactDate()).matches("\\d{8}");
    }
  }

  @Nested
  @DisplayName("year/month/day/timestamp")
  class Parts {

    @Test
    @DisplayName("year 在合理范围")
    void yearRange() {
      int y = tool.year();
      assertThat(y).isBetween(2020, 2100);
    }

    @Test
    @DisplayName("month 在 1-12")
    void monthRange() {
      assertThat(tool.month()).isBetween(1, 12);
    }

    @Test
    @DisplayName("day 在 1-31")
    void dayRange() {
      assertThat(tool.day()).isBetween(1, 31);
    }

    @Test
    @DisplayName("timestamp 为正数且接近当前")
    void timestamp() {
      long ts = tool.timestamp();
      assertThat(ts).isPositive();
      assertThat(ts).isLessThanOrEqualTo(System.currentTimeMillis());
    }
  }
}
