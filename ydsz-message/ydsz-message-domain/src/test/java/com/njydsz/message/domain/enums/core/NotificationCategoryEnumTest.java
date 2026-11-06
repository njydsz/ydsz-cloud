package com.njydsz.message.domain.enums.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NotificationCategoryEnum} 单元测试。
 *
 * <p>覆盖：fromString 安全解析（大小写/空白/非法回退 SYSTEM）、全量枚举值。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("通知分类枚举解析")
class NotificationCategoryEnumTest {

  @Nested
  @DisplayName("fromString 合法值")
  class FromStringValid {

    @ParameterizedTest(name = "fromString(\"{0}\") 应返回 {1}")
    @CsvSource({
        "SYSTEM, SYSTEM",
        "WORKFLOW, WORKFLOW",
        "ALERT, ALERT",
        "TO_DO, TO_DO",
        "ANNOUNCE, ANNOUNCE"
    })
    @DisplayName("全量枚举名均可解析")
    void should_parse_all_categories(String input, NotificationCategoryEnum expected) {
      assertThat(NotificationCategoryEnum.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 大小写无关")
    @CsvSource({
        "system, SYSTEM",
        "Alert, ALERT",
        "to_do, TO_DO",
        "announce, ANNOUNCE"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, NotificationCategoryEnum expected) {
      assertThat(NotificationCategoryEnum.fromString(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("前后空白被裁剪")
    void should_trim_whitespace() {
      assertThat(NotificationCategoryEnum.fromString("  ALERT  ")).isEqualTo(NotificationCategoryEnum.ALERT);
    }
  }

  @Nested
  @DisplayName("fromString 非法值回退")
  class FromStringInvalid {

    @Test
    @DisplayName("null 回退为 SYSTEM")
    void should_fallback_for_null() {
      assertThat(NotificationCategoryEnum.fromString(null)).isEqualTo(NotificationCategoryEnum.SYSTEM);
    }

    @Test
    @DisplayName("空串回退为 SYSTEM")
    void should_fallback_for_empty() {
      assertThat(NotificationCategoryEnum.fromString("")).isEqualTo(NotificationCategoryEnum.SYSTEM);
    }

    @Test
    @DisplayName("纯空白回退为 SYSTEM")
    void should_fallback_for_blank() {
      assertThat(NotificationCategoryEnum.fromString("   ")).isEqualTo(NotificationCategoryEnum.SYSTEM);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 未知值回退 SYSTEM")
    @ValueSource(strings = {"FAKE", "ALERTX", "info_alert", "1"})
    @DisplayName("未知值回退 SYSTEM")
    void should_fallback_for_unknown(String input) {
      assertThat(NotificationCategoryEnum.fromString(input)).isEqualTo(NotificationCategoryEnum.SYSTEM);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 5 个分类")
    void should_have_five_categories() {
      assertThat(NotificationCategoryEnum.values()).hasSize(5);
    }
  }
}
