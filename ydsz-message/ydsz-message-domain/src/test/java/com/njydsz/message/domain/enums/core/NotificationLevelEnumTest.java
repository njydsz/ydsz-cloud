package com.njydsz.message.domain.enums.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NotificationLevelEnum} 单元测试。
 *
 * <p>覆盖：fromString 安全解析（大小写/空白/非法回退 INFO）、全量枚举值。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("通知级别枚举解析")
class NotificationLevelEnumTest {

  @Nested
  @DisplayName("fromString 合法值")
  class FromStringValid {

    @ParameterizedTest(name = "fromString(\"{0}\") 应返回 {1}")
    @CsvSource({
        "INFO, INFO",
        "WARN, WARN",
        "ERROR, ERROR",
        "URGENT, URGENT"
    })
    @DisplayName("全量枚举名均可解析")
    void should_parse_all_levels(String input, NotificationLevelEnum expected) {
      assertThat(NotificationLevelEnum.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 大小写无关")
    @CsvSource({
        "info, INFO",
        "Warn, WARN",
        "urgent, URGENT"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, NotificationLevelEnum expected) {
      assertThat(NotificationLevelEnum.fromString(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("前后空白被裁剪")
    void should_trim_whitespace() {
      assertThat(NotificationLevelEnum.fromString("  WARN  ")).isEqualTo(NotificationLevelEnum.WARN);
    }
  }

  @Nested
  @DisplayName("fromString 非法值回退")
  class FromStringInvalid {

    @Test
    @DisplayName("null 回退为 INFO")
    void should_fallback_for_null() {
      assertThat(NotificationLevelEnum.fromString(null)).isEqualTo(NotificationLevelEnum.INFO);
    }

    @Test
    @DisplayName("空串回退为 INFO")
    void should_fallback_for_empty() {
      assertThat(NotificationLevelEnum.fromString("")).isEqualTo(NotificationLevelEnum.INFO);
    }

    @Test
    @DisplayName("纯空白回退为 INFO")
    void should_fallback_for_blank() {
      assertThat(NotificationLevelEnum.fromString("   ")).isEqualTo(NotificationLevelEnum.INFO);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 未知值回退 INFO")
    @ValueSource(strings = {"CRITICAL", "SILENT", "1", "info_warn"})
    @DisplayName("未知值回退 INFO")
    void should_fallback_for_unknown(String input) {
      assertThat(NotificationLevelEnum.fromString(input)).isEqualTo(NotificationLevelEnum.INFO);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 4 个级别")
    void should_have_four_levels() {
      assertThat(NotificationLevelEnum.values()).hasSize(4);
    }
  }
}
