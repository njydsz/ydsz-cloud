package com.njydsz.message.domain.enums.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SendStrategyEnum} 单元测试。
 *
 * <p>覆盖：parse 安全解析（大小写/空白/非法回退 SYNC）、全量枚举值。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("发送策略枚举解析")
class SendStrategyEnumTest {

  @Nested
  @DisplayName("parse 合法值")
  class ParseValid {

    @ParameterizedTest(name = "parse(\"{0}\") 应返回 {1}")
    @CsvSource({
        "SYNC, SYNC",
        "DIRECT, DIRECT",
        "ASYNC, ASYNC",
        "TRANSACTIONAL, TRANSACTIONAL",
        "BATCH, BATCH"
    })
    @DisplayName("全量枚举值均可解析")
    void should_parse_all_strategies(String input, SendStrategyEnum expected) {
      assertThat(SendStrategyEnum.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parse(\"{0}\") 大小写无关")
    @CsvSource({
        "sync, SYNC",
        "Async, ASYNC",
        "transactional, TRANSACTIONAL",
        "batch, BATCH"
    })
    @DisplayName("大小写无关解析")
    void should_be_case_insensitive(String input, SendStrategyEnum expected) {
      assertThat(SendStrategyEnum.parse(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("前后空白被裁剪")
    void should_trim_whitespace() {
      assertThat(SendStrategyEnum.parse("  ASYNC  ")).isEqualTo(SendStrategyEnum.ASYNC);
    }
  }

  @Nested
  @DisplayName("parse 非法值回退")
  class ParseInvalid {

    @Test
    @DisplayName("null 回退为 SYNC")
    void should_fallback_for_null() {
      assertThat(SendStrategyEnum.parse(null)).isEqualTo(SendStrategyEnum.SYNC);
    }

    @Test
    @DisplayName("空串回退为 SYNC")
    void should_fallback_for_empty() {
      assertThat(SendStrategyEnum.parse("")).isEqualTo(SendStrategyEnum.SYNC);
    }

    @Test
    @DisplayName("纯空白回退为 SYNC")
    void should_fallback_for_blank() {
      assertThat(SendStrategyEnum.parse("   ")).isEqualTo(SendStrategyEnum.SYNC);
    }

    @ParameterizedTest(name = "parse(\"{0}\") 未知值回退 SYNC")
    @ValueSource(strings = {"FAKE", "ASYNCX", "aggregated", "1"})
    @DisplayName("未知值回退 SYNC")
    void should_fallback_for_unknown(String input) {
      assertThat(SendStrategyEnum.parse(input)).isEqualTo(SendStrategyEnum.SYNC);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 5 个策略")
    void should_have_five_strategies() {
      assertThat(SendStrategyEnum.values()).hasSize(5);
    }
  }
}
