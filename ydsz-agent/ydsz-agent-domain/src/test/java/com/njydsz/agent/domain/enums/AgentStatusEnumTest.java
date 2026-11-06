package com.njydsz.agent.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentStatusEnum} 单元测试。
 *
 * <p>覆盖：大小写不敏感解析、空值/非法值兜底、启用判定、双向状态流转。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("AgentStatusEnum Agent 定义状态枚举")
class AgentStatusEnumTest {

  @Nested
  @DisplayName("解析")
  class ParseTest {

    @ParameterizedTest(name = "parse(\"{0}\") 应返回 {1}")
    @CsvSource({
      "ENABLED, ENABLED",
      "DISABLED, DISABLED",
      "enabled, ENABLED",
      "Disabled, DISABLED",
      "EnAbLeD, ENABLED",
    })
    @DisplayName("大小写不敏感解析应成功")
    void shouldParseCaseInsensitive(String value, AgentStatusEnum expected) {
      assertThat(AgentStatusEnum.parse(value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parse(\"{0}\") 应返回 null")
    @ValueSource(strings = {"UNKNOWN", "1", "ENABLED "})
    @DisplayName("非法值应返回 null")
    void shouldReturnNullForInvalid(String value) {
      assertThat(AgentStatusEnum.parse(value)).isNull();
    }

    @Test
    @DisplayName("null 应返回 null")
    void shouldReturnNullForNull() {
      assertThat(AgentStatusEnum.parse(null)).isNull();
    }

    @Test
    @DisplayName("空白字符串应返回 null")
    void shouldReturnNullForBlank() {
      assertThat(AgentStatusEnum.parse("   ")).isNull();
    }
  }

  @Nested
  @DisplayName("启用判定")
  class EnabledTest {

    @Test
    @DisplayName("ENABLED 应启用，DISABLED 应停用")
    void shouldJudgeEnabled() {
      assertThat(AgentStatusEnum.ENABLED.isEnabled()).isTrue();
      assertThat(AgentStatusEnum.DISABLED.isEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("状态流转")
  class TransitTest {

    @ParameterizedTest(name = "{0} → {1} 应允许")
    @CsvSource({
      "ENABLED, ENABLED",
      "DISABLED, DISABLED",
      "ENABLED, DISABLED",
      "DISABLED, ENABLED",
    })
    @DisplayName("启用/停用双向流转应被允许")
    void shouldAllowTransit(AgentStatusEnum from, AgentStatusEnum to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @Test
    @DisplayName("目标为 null 应拒绝")
    void shouldRejectNullTarget() {
      assertThat(AgentStatusEnum.ENABLED.canTransitTo(null)).isFalse();
    }
  }
}
