package com.njydsz.cronjob.domain.dag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DagNodeStatus} 单元测试。
 *
 * <p>覆盖：解析（大小写/空值/非法）、终态判定、状态流转规则（含审批与重试分支）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("DAG 节点状态枚举")
class DagNodeStatusTest {

  @Nested
  @DisplayName("parse 解析")
  class Parse {

    @ParameterizedTest
    @CsvSource({
      "PENDING, PENDING",
      "pending, PENDING",
      "Running, RUNNING",
      "WAITING_FOR_APPROVAL, WAITING_FOR_APPROVAL",
      "APPROVAL_REJECTED, APPROVAL_REJECTED",
      "RETRYING, RETRYING",
      "SUCCESS, SUCCESS",
      "FAILED, FAILED",
      "SKIPPED, SKIPPED",
      "TIMEOUT, TIMEOUT"
    })
    @DisplayName("大小写不敏感解析")
    void caseInsensitive(String input, DagNodeStatus expected) {
      assertThat(DagNodeStatus.parse(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(DagNodeStatus.parse(null)).isNull();
    }

    @Test
    @DisplayName("空白字符串返回 null")
    void blankReturnsNull() {
      assertThat(DagNodeStatus.parse("   ")).isNull();
    }

    @Test
    @DisplayName("非法值返回 null（不抛异常）")
    void illegalReturnsNull() {
      assertThat(DagNodeStatus.parse("NOT_A_STATUS")).isNull();
    }
  }

  @Nested
  @DisplayName("isTerminal 终态判定")
  class Terminal {

    @Test
    @DisplayName("终态为 SUCCESS/FAILED/SKIPPED/TIMEOUT/APPROVAL_REJECTED")
    void terminalStates() {
      assertThat(DagNodeStatus.SUCCESS.isTerminal()).isTrue();
      assertThat(DagNodeStatus.FAILED.isTerminal()).isTrue();
      assertThat(DagNodeStatus.SKIPPED.isTerminal()).isTrue();
      assertThat(DagNodeStatus.TIMEOUT.isTerminal()).isTrue();
      assertThat(DagNodeStatus.APPROVAL_REJECTED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("非终态为 PENDING/RUNNING/WAITING_FOR_APPROVAL/RETRYING")
    void nonTerminalStates() {
      assertThat(DagNodeStatus.PENDING.isTerminal()).isFalse();
      assertThat(DagNodeStatus.RUNNING.isTerminal()).isFalse();
      assertThat(DagNodeStatus.WAITING_FOR_APPROVAL.isTerminal()).isFalse();
      assertThat(DagNodeStatus.RETRYING.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("canTransitTo 状态流转")
  class Transition {

    @Test
    @DisplayName("相同状态允许（幂等）")
    void sameStateAllowed() {
      for (DagNodeStatus s : DagNodeStatus.values()) {
        assertThat(s.canTransitTo(s)).isTrue();
      }
    }

    @Test
    @DisplayName("PENDING → RUNNING/WAITING_FOR_APPROVAL/SKIPPED 允许")
    void pendingTransitions() {
      assertThat(DagNodeStatus.PENDING.canTransitTo(DagNodeStatus.RUNNING)).isTrue();
      assertThat(DagNodeStatus.PENDING.canTransitTo(DagNodeStatus.WAITING_FOR_APPROVAL)).isTrue();
      assertThat(DagNodeStatus.PENDING.canTransitTo(DagNodeStatus.SKIPPED)).isTrue();
    }

    @Test
    @DisplayName("PENDING → SUCCESS/FAILED 拒绝")
    void pendingToTerminalRejected() {
      assertThat(DagNodeStatus.PENDING.canTransitTo(DagNodeStatus.SUCCESS)).isFalse();
      assertThat(DagNodeStatus.PENDING.canTransitTo(DagNodeStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("WAITING_FOR_APPROVAL → APPROVAL_REJECTED/RUNNING 允许")
    void waitingTransitions() {
      assertThat(DagNodeStatus.WAITING_FOR_APPROVAL.canTransitTo(DagNodeStatus.APPROVAL_REJECTED)).isTrue();
      assertThat(DagNodeStatus.WAITING_FOR_APPROVAL.canTransitTo(DagNodeStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("WAITING_FOR_APPROVAL → SUCCESS/SKIPPED 拒绝")
    void waitingToOtherRejected() {
      assertThat(DagNodeStatus.WAITING_FOR_APPROVAL.canTransitTo(DagNodeStatus.SUCCESS)).isFalse();
      assertThat(DagNodeStatus.WAITING_FOR_APPROVAL.canTransitTo(DagNodeStatus.SKIPPED)).isFalse();
    }

    @Test
    @DisplayName("RUNNING → WAITING_FOR_APPROVAL/RETRYING/SUCCESS/FAILED/TIMEOUT 允许")
    void runningTransitions() {
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.WAITING_FOR_APPROVAL)).isTrue();
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.RETRYING)).isTrue();
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.SUCCESS)).isTrue();
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.FAILED)).isTrue();
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.TIMEOUT)).isTrue();
    }

    @Test
    @DisplayName("RUNNING → PENDING/SKIPPED 拒绝")
    void runningToPendingOrSkippedRejected() {
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.PENDING)).isFalse();
      assertThat(DagNodeStatus.RUNNING.canTransitTo(DagNodeStatus.SKIPPED)).isFalse();
    }

    @Test
    @DisplayName("RETRYING → RUNNING/FAILED/TIMEOUT 允许")
    void retryingTransitions() {
      assertThat(DagNodeStatus.RETRYING.canTransitTo(DagNodeStatus.RUNNING)).isTrue();
      assertThat(DagNodeStatus.RETRYING.canTransitTo(DagNodeStatus.FAILED)).isTrue();
      assertThat(DagNodeStatus.RETRYING.canTransitTo(DagNodeStatus.TIMEOUT)).isTrue();
    }

    @Test
    @DisplayName("RETRYING → SUCCESS/PENDING 拒绝")
    void retryingToSuccessOrPendingRejected() {
      assertThat(DagNodeStatus.RETRYING.canTransitTo(DagNodeStatus.SUCCESS)).isFalse();
      assertThat(DagNodeStatus.RETRYING.canTransitTo(DagNodeStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("终态 → 任意状态拒绝")
    void terminalToAnyRejected() {
      for (DagNodeStatus terminal : DagNodeStatus.values()) {
        if (!terminal.isTerminal()) {
          continue;
        }
        for (DagNodeStatus target : DagNodeStatus.values()) {
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
