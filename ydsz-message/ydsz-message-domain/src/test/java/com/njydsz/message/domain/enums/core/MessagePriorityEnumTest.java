package com.njydsz.message.domain.enums.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MessagePriorityEnum} 单元测试。
 *
 * <p>覆盖：fromString 安全解析（大小写/空白/非法回退 NORMAL）、优先级数值、限流倍率、是否可跳过限流。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("消息优先级枚举解析")
class MessagePriorityEnumTest {

  @Nested
  @DisplayName("fromString 合法值")
  class FromStringValid {

    @ParameterizedTest(name = "fromString(\"{0}\") 应返回 {1}")
    @CsvSource({
        "LOW, LOW",
        "NORMAL, NORMAL",
        "HIGH, HIGH",
        "URGENT, URGENT"
    })
    @DisplayName("全量枚举名均可解析")
    void should_parse_all_names(String input, MessagePriorityEnum expected) {
      assertThat(MessagePriorityEnum.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 大小写无关")
    @CsvSource({
        "low, LOW",
        "Normal, NORMAL",
        "urgent, URGENT"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, MessagePriorityEnum expected) {
      assertThat(MessagePriorityEnum.fromString(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("前后空白被裁剪")
    void should_trim_whitespace() {
      assertThat(MessagePriorityEnum.fromString("  HIGH  ")).isEqualTo(MessagePriorityEnum.HIGH);
    }
  }

  @Nested
  @DisplayName("fromString 非法值回退")
  class FromStringInvalid {

    @Test
    @DisplayName("null 回退为 NORMAL")
    void should_fallback_for_null() {
      assertThat(MessagePriorityEnum.fromString(null)).isEqualTo(MessagePriorityEnum.NORMAL);
    }

    @Test
    @DisplayName("空串回退为 NORMAL")
    void should_fallback_for_empty() {
      assertThat(MessagePriorityEnum.fromString("")).isEqualTo(MessagePriorityEnum.NORMAL);
    }

    @Test
    @DisplayName("纯空白回退为 NORMAL")
    void should_fallback_for_blank() {
      assertThat(MessagePriorityEnum.fromString("   ")).isEqualTo(MessagePriorityEnum.NORMAL);
    }

    @ParameterizedTest(name = "fromString(\"{0}\") 未知值回退 NORMAL")
    @ValueSource(strings = {"CRITICAL", "LOWX", "1", "normal_high"})
    @DisplayName("未知值回退 NORMAL")
    void should_fallback_for_unknown(String input) {
      assertThat(MessagePriorityEnum.fromString(input)).isEqualTo(MessagePriorityEnum.NORMAL);
    }
  }

  @Nested
  @DisplayName("优先级数值")
  class PriorityValue {

    @ParameterizedTest(name = "{0} 的数值应为 {1}")
    @CsvSource({"LOW, 1", "NORMAL, 5", "HIGH, 8", "URGENT, 10"})
    @DisplayName("数值随优先级递增")
    void should_have_expected_value(MessagePriorityEnum priority, int expected) {
      assertThat(priority.getValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("数值越大优先级越高")
    void should_be_ordered_by_value() {
      assertThat(MessagePriorityEnum.URGENT.getValue()).isGreaterThan(MessagePriorityEnum.HIGH.getValue());
      assertThat(MessagePriorityEnum.HIGH.getValue()).isGreaterThan(MessagePriorityEnum.NORMAL.getValue());
      assertThat(MessagePriorityEnum.NORMAL.getValue()).isGreaterThan(MessagePriorityEnum.LOW.getValue());
    }
  }

  @Nested
  @DisplayName("限流倍率")
  class RateLimitMultiplier {

    @ParameterizedTest(name = "{0} 的限流倍率应为 {1}")
    @CsvSource({"LOW, 0.5", "NORMAL, 1.0", "HIGH, 2.0", "URGENT, 10.0"})
    @DisplayName("倍率与优先级匹配")
    void should_have_expected_multiplier(MessagePriorityEnum priority, double expected) {
      assertThat(priority.rateLimitMultiplier()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("跳过限流")
  class SkipRateLimit {

    @Test
    @DisplayName("仅 URGENT 可跳过限流")
    void only_urgent_can_skip() {
      assertThat(MessagePriorityEnum.URGENT.canSkipRateLimit()).isTrue();
      assertThat(MessagePriorityEnum.HIGH.canSkipRateLimit()).isFalse();
      assertThat(MessagePriorityEnum.NORMAL.canSkipRateLimit()).isFalse();
      assertThat(MessagePriorityEnum.LOW.canSkipRateLimit()).isFalse();
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 4 个优先级")
    void should_have_four_priorities() {
      assertThat(MessagePriorityEnum.values()).hasSize(4);
    }
  }
}
