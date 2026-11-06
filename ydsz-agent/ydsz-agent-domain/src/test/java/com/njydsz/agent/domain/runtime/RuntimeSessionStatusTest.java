package com.njydsz.agent.domain.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuntimeSessionStatus} 单元测试。
 *
 * <p>覆盖：编码解析、终态判定、状态流转规则（含 WAITING 分支）、全量状态列表。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("RuntimeSessionStatus 运行时会话状态枚举")
class RuntimeSessionStatusTest {

  @Nested
  @DisplayName("编码解析")
  class FromCodeTest {

    @ParameterizedTest(name = "fromCode(\"{0}\") 应返回 {1}")
    @CsvSource({
      "pending, PENDING",
      "running, RUNNING",
      "waiting, WAITING",
      "completed, COMPLETED",
      "failed, FAILED",
      "cancelled, CANCELLED",
      "timeout, TIMEOUT",
    })
    @DisplayName("合法编码应解析为对应枚举")
    void shouldParseValidCode(String code, RuntimeSessionStatus expected) {
      assertThat(RuntimeSessionStatus.fromCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("未知编码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(RuntimeSessionStatus.fromCode("NOT_EXIST")).isNull();
    }

    @Test
    @DisplayName("null 编码应返回 null")
    void shouldReturnNullForNullCode() {
      assertThat(RuntimeSessionStatus.fromCode(null)).isNull();
    }
  }

  @Nested
  @DisplayName("终态判定")
  class TerminalTest {

    @ParameterizedTest(name = "isTerminal({0}) 应为 {1}")
    @CsvSource({
      "COMPLETED, true",
      "FAILED, true",
      "CANCELLED, true",
      "TIMEOUT, true",
      "PENDING, false",
      "RUNNING, false",
      "WAITING, false",
    })
    @DisplayName("终态判定应正确")
    void shouldJudgeTerminal(RuntimeSessionStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class TransitTest {

    @ParameterizedTest(name = "{0} → {1} 应允许")
    @CsvSource({
      "PENDING, RUNNING",
      "PENDING, CANCELLED",
      "PENDING, TIMEOUT",
      "RUNNING, WAITING",
      "RUNNING, COMPLETED",
      "RUNNING, FAILED",
      "RUNNING, CANCELLED",
      "RUNNING, TIMEOUT",
      "WAITING, RUNNING",
      "WAITING, COMPLETED",
      "WAITING, FAILED",
      "WAITING, CANCELLED",
      "WAITING, TIMEOUT",
      "PENDING, PENDING",
      "COMPLETED, COMPLETED",
    })
    @DisplayName("合法流转应被允许")
    void shouldAllowValidTransit(RuntimeSessionStatus from, RuntimeSessionStatus to) {
      assertThat(from.canTransitTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 应拒绝")
    @CsvSource({
      "PENDING, COMPLETED",
      "PENDING, FAILED",
      "PENDING, WAITING",
      "RUNNING, PENDING",
      "WAITING, PENDING",
      "COMPLETED, RUNNING",
      "FAILED, RUNNING",
      "CANCELLED, PENDING",
      "TIMEOUT, RUNNING",
    })
    @DisplayName("非法流转应被拒绝")
    void shouldRejectInvalidTransit(RuntimeSessionStatus from, RuntimeSessionStatus to) {
      assertThat(from.canTransitTo(to)).isFalse();
    }

    @Test
    @DisplayName("目标为 null 应拒绝")
    void shouldRejectNullTarget() {
      assertThat(RuntimeSessionStatus.PENDING.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("全量状态")
  class AllStatesTest {

    @Test
    @DisplayName("allStates 应返回全部 7 个状态")
    void shouldReturnAllStates() {
      assertThat(RuntimeSessionStatus.PENDING.allStates())
          .containsExactly(RuntimeSessionStatus.values());
    }
  }
}
