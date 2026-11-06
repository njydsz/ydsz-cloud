package com.njydsz.workflow.domain.enums;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FlowInstanceStatus} 单元测试。
 *
 * <p>覆盖：终态判定、状态流转规则（canTransitTo）、isFinished 与 isTerminal 一致性。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("流程实例状态枚举")
class FlowInstanceStatusTest {

  @Nested
  @DisplayName("isFinished / isTerminal 终态判定")
  class Terminal {

    // 26.11.06：isFinished()（流程已结束，含 COMPLETED）与 isTerminal()（不可再流转，
    // 不含 COMPLETED）语义已拆分——canTransitTo 允许 COMPLETED → ROLLED_BACK。
    @ParameterizedTest
    @CsvSource({
        "COMPLETED, true",
        "TERMINATED, true",
        "REJECTED, true",
        "ROLLED_BACK, true",
        "RUNNING, false",
        "SUSPENDED, false",
        "ERROR, false",
        "DRAFT, false"
    })
    @DisplayName("isFinished：流程已结束判定")
    void finishedStatus(FlowInstanceStatus status, boolean expected) {
      assertThat(status.isFinished()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "COMPLETED, false",
        "TERMINATED, true",
        "REJECTED, true",
        "ROLLED_BACK, true",
        "RUNNING, false",
        "SUSPENDED, false",
        "ERROR, false",
        "DRAFT, false"
    })
    @DisplayName("isTerminal：不可再流转判定（COMPLETED 可撤销，非终态）")
    void terminalStatus(FlowInstanceStatus status, boolean expected) {
      assertThat(status.isTerminal()).isEqualTo(expected);
    }

    @Test
    @DisplayName("isTerminal 与 canTransitTo 不冲突")
    void terminalConsistentWithCanTransitTo() {
      for (FlowInstanceStatus status : FlowInstanceStatus.values()) {
        for (FlowInstanceStatus target : FlowInstanceStatus.values()) {
          if (target == status) {
            continue;
          }
          if (status.canTransitTo(target)) {
            assertThat(status.isTerminal())
                .as("%s 可流转到 %s，不应为终态", status, target)
                .isFalse();
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("canTransitTo 状态流转")
  class Transition {

    @ParameterizedTest
    @CsvSource({
        "RUNNING, SUSPENDED, true",
        "RUNNING, COMPLETED, true",
        "RUNNING, TERMINATED, true",
        "RUNNING, REJECTED, true",
        "RUNNING, ERROR, true",
        "RUNNING, ROLLED_BACK, true",
        "RUNNING, DRAFT, false",
        "SUSPENDED, RUNNING, true",
        "SUSPENDED, TERMINATED, true",
        "SUSPENDED, REJECTED, true",
        "SUSPENDED, ERROR, true",
        "SUSPENDED, COMPLETED, false",
        "SUSPENDED, ROLLED_BACK, false",
        "ERROR, RUNNING, true",
        "ERROR, TERMINATED, true",
        "ERROR, REJECTED, true",
        "ERROR, ROLLED_BACK, true",
        "ERROR, COMPLETED, false",
        "ERROR, SUSPENDED, false",
        "COMPLETED, ROLLED_BACK, true",
        "COMPLETED, RUNNING, false",
        "COMPLETED, TERMINATED, false",
        "TERMINATED, RUNNING, false",
        "TERMINATED, COMPLETED, false",
        "REJECTED, RUNNING, false",
        "ROLLED_BACK, RUNNING, false",
        "DRAFT, RUNNING, true",
        "DRAFT, TERMINATED, true",
        "DRAFT, COMPLETED, false",
        "DRAFT, SUSPENDED, false"
    })
    @DisplayName("流转规则正确")
    void transitionRules(FlowInstanceStatus from, FlowInstanceStatus to, boolean expected) {
      assertThat(from.canTransitTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("相同状态返回 true（幂等）")
    void sameStateIsAllowed() {
      for (FlowInstanceStatus status : FlowInstanceStatus.values()) {
        assertThat(status.canTransitTo(status)).isTrue();
      }
    }

    @Test
    @DisplayName("target 为 null 返回 false")
    void nullTargetReturnsFalse() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("requireTransitTo 非法流转抛异常")
  class RequireTransit {

    @Test
    @DisplayName("非法流转抛 IllegalStateException")
    void illegalTransitionThrows() {
      assertThatThrownBy(() -> FlowInstanceStatus.TERMINATED.requireTransitTo(FlowInstanceStatus.RUNNING))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("合法流转不抛异常")
    void legalTransitionPasses() {
      FlowInstanceStatus.RUNNING.requireTransitTo(FlowInstanceStatus.COMPLETED);
    }
  }

  @Nested
  @DisplayName("枚举完整性")
  class Completeness {

    @Test
    @DisplayName("共 8 个枚举值")
    void hasEightValues() {
      assertThat(FlowInstanceStatus.values()).hasSize(8);
    }
  }
}
