package com.njydsz.cronjob.domain.dag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DagInstanceStatus} 单元测试。
 *
 * <p>覆盖：解析（大小写/空值/非法）、终态判定、状态流转规则。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("DAG 实例状态枚举")
class DagInstanceStatusTest {

  @Nested
  @DisplayName("parse 解析")
  class Parse {

    @ParameterizedTest
    @CsvSource({
      "PENDING, PENDING",
      "pending, PENDING",
      "Running, RUNNING",
      "SUCCESS, SUCCESS",
      "partial_success, PARTIAL_SUCCESS"
    })
    @DisplayName("大小写不敏感解析")
    void caseInsensitive(String input, DagInstanceStatus expected) {
      assertThat(DagInstanceStatus.parse(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(DagInstanceStatus.parse(null)).isNull();
    }

    @Test
    @DisplayName("空白字符串返回 null")
    void blankReturnsNull() {
      assertThat(DagInstanceStatus.parse("  ")).isNull();
    }

    @Test
    @DisplayName("非法值返回 null（不抛异常）")
    void illegalReturnsNull() {
      assertThat(DagInstanceStatus.parse("NOT_A_STATUS")).isNull();
    }
  }

  @Nested
  @DisplayName("isTerminal 终态判定")
  class Terminal {

    @Test
    @DisplayName("终态为 SUCCESS/FAILED/PARTIAL_SUCCESS/CANCELLED/TIMEOUT")
    void terminalStates() {
      assertThat(DagInstanceStatus.SUCCESS.isTerminal()).isTrue();
      assertThat(DagInstanceStatus.FAILED.isTerminal()).isTrue();
      assertThat(DagInstanceStatus.PARTIAL_SUCCESS.isTerminal()).isTrue();
      assertThat(DagInstanceStatus.CANCELLED.isTerminal()).isTrue();
      assertThat(DagInstanceStatus.TIMEOUT.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("非终态为 PENDING/RUNNING/PAUSED")
    void nonTerminalStates() {
      assertThat(DagInstanceStatus.PENDING.isTerminal()).isFalse();
      assertThat(DagInstanceStatus.RUNNING.isTerminal()).isFalse();
      assertThat(DagInstanceStatus.PAUSED.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("canTransitTo 状态流转")
  class Transition {

    @Test
    @DisplayName("相同状态允许（幂等）")
    void sameStateAllowed() {
      for (DagInstanceStatus s : DagInstanceStatus.values()) {
        assertThat(s.canTransitTo(s)).isTrue();
      }
    }

    @Test
    @DisplayName("PENDING → RUNNING/CANCELLED 允许")
    void pendingTransitions() {
      assertThat(DagInstanceStatus.PENDING.canTransitTo(DagInstanceStatus.RUNNING)).isTrue();
      assertThat(DagInstanceStatus.PENDING.canTransitTo(DagInstanceStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("PENDING → SUCCESS 拒绝")
    void pendingToSuccessRejected() {
      assertThat(DagInstanceStatus.PENDING.canTransitTo(DagInstanceStatus.SUCCESS)).isFalse();
    }

    @Test
    @DisplayName("RUNNING → 所有活跃/终态允许")
    void runningTransitions() {
      assertThat(DagInstanceStatus.RUNNING.canTransitTo(DagInstanceStatus.PAUSED)).isTrue();
      assertThat(DagInstanceStatus.RUNNING.canTransitTo(DagInstanceStatus.SUCCESS)).isTrue();
      assertThat(DagInstanceStatus.RUNNING.canTransitTo(DagInstanceStatus.FAILED)).isTrue();
      assertThat(DagInstanceStatus.RUNNING.canTransitTo(DagInstanceStatus.PARTIAL_SUCCESS)).isTrue();
      assertThat(DagInstanceStatus.RUNNING.canTransitTo(DagInstanceStatus.CANCELLED)).isTrue();
      assertThat(DagInstanceStatus.RUNNING.canTransitTo(DagInstanceStatus.TIMEOUT)).isTrue();
    }

    @Test
    @DisplayName("PAUSED → RUNNING/CANCELLED/TIMEOUT 允许")
    void pausedTransitions() {
      assertThat(DagInstanceStatus.PAUSED.canTransitTo(DagInstanceStatus.RUNNING)).isTrue();
      assertThat(DagInstanceStatus.PAUSED.canTransitTo(DagInstanceStatus.CANCELLED)).isTrue();
      assertThat(DagInstanceStatus.PAUSED.canTransitTo(DagInstanceStatus.TIMEOUT)).isTrue();
    }

    @Test
    @DisplayName("终态 → 任意状态拒绝")
    void terminalToAnyRejected() {
      for (DagInstanceStatus terminal : DagInstanceStatus.values()) {
        if (!terminal.isTerminal()) {
          continue;
        }
        for (DagInstanceStatus target : DagInstanceStatus.values()) {
          if (target == terminal) {
            continue;
          }
          assertThat(terminal.canTransitTo(target))
              .as("%s → %s 应拒绝", terminal, target)
              .isFalse();
        }
      }
    }
  }
}
