package com.njydsz.workflow.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FlowTimeoutStrategy} 单元测试。
 *
 * <p>覆盖：of 解析（null/未知回退 REMIND）、code 与 desc 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("超时处理策略枚举")
class FlowTimeoutStrategyTest {

  @Nested
  @DisplayName("of 解析")
  class Of {

    @ParameterizedTest
    @CsvSource({
        "AUTO_PASS, AUTO_PASS",
        "TRANSFER_ADMIN, TRANSFER_ADMIN",
        "TRANSFER_SUPERIOR, TRANSFER_SUPERIOR",
        "REMIND, REMIND"
    })
    @DisplayName("合法 code 解析正确")
    void validCodes(String code, FlowTimeoutStrategy expected) {
      assertThat(FlowTimeoutStrategy.of(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 回退 REMIND")
    void nullFallsBackToRemind() {
      assertThat(FlowTimeoutStrategy.of(null)).isEqualTo(FlowTimeoutStrategy.REMIND);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "auto_pass", "TRANSFER"})
    @DisplayName("未知/空 code 回退 REMIND")
    void unknownFallsBackToRemind(String code) {
      assertThat(FlowTimeoutStrategy.of(code)).isEqualTo(FlowTimeoutStrategy.REMIND);
    }

    @Test
    @DisplayName("大小写敏感，小写不匹配")
    void caseSensitive() {
      assertThat(FlowTimeoutStrategy.of("auto_pass")).isEqualTo(FlowTimeoutStrategy.REMIND);
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 code 与 desc 非空")
    void allHaveCodeAndDesc() {
      for (FlowTimeoutStrategy strategy : FlowTimeoutStrategy.values()) {
        assertThat(strategy.getCode()).isNotBlank();
        assertThat(strategy.getDesc()).isNotBlank();
      }
    }

    @Test
    @DisplayName("code 与枚举名称一致")
    void codeMatchesName() {
      for (FlowTimeoutStrategy strategy : FlowTimeoutStrategy.values()) {
        assertThat(strategy.getCode()).isEqualTo(strategy.name());
      }
    }
  }
}
