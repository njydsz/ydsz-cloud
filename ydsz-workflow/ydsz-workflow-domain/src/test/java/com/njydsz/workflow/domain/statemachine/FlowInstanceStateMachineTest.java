package com.njydsz.workflow.domain.statemachine;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.exception.WorkflowException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FlowInstanceStateMachine} 单元测试。
 *
 * <p>覆盖：状态流转合法性、终态不可流转、可用流转集合、活跃/终态集合、空参防御、与枚举规则一致性。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("流程实例状态机")
class FlowInstanceStateMachineTest {

  private final FlowInstanceStateMachine machine = new FlowInstanceStateMachine();

  @Nested
  @DisplayName("validateTransition 状态流转校验")
  class ValidateTransition {

    @ParameterizedTest
    @CsvSource({
        "RUNNING, SUSPENDED, true",
        "RUNNING, COMPLETED, true",
        "RUNNING, TERMINATED, true",
        "RUNNING, REJECTED, true",
        "RUNNING, ERROR, true",
        "RUNNING, ROLLED_BACK, true",
        "SUSPENDED, RUNNING, true",
        "SUSPENDED, TERMINATED, true",
        "ERROR, RUNNING, true",
        "ERROR, ROLLED_BACK, true",
        "COMPLETED, ROLLED_BACK, true",
        "DRAFT, RUNNING, true",
        "DRAFT, TERMINATED, true",
        "TERMINATED, RUNNING, false",
        "REJECTED, RUNNING, false",
        "ROLLED_BACK, RUNNING, false",
        "COMPLETED, RUNNING, false",
        "DRAFT, COMPLETED, false",
        "RUNNING, RUNNING, true"
    })
    @DisplayName("流转规则正确")
    void transitionRules(FlowInstanceStatus from, FlowInstanceStatus to, boolean expected) {
      assertThat(machine.validateTransition(from, to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("与枚举 canTransitTo 语义一致")
    void consistentWithEnum() {
      for (FlowInstanceStatus from : FlowInstanceStatus.values()) {
        for (FlowInstanceStatus to : FlowInstanceStatus.values()) {
          assertThat(machine.validateTransition(from, to))
              .as("%s → %s 应与枚举一致", from, to)
              .isEqualTo(from.canTransitTo(to));
        }
      }
    }

    @Test
    @DisplayName("current 为 null 抛 WorkflowException")
    void nullCurrentThrows() {
      assertThatThrownBy(() -> machine.validateTransition(null, FlowInstanceStatus.RUNNING))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("target 为 null 抛 WorkflowException")
    void nullTargetThrows() {
      assertThatThrownBy(() -> machine.validateTransition(FlowInstanceStatus.RUNNING, null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("requireTransition 强制校验")
  class RequireTransition {

    @Test
    @DisplayName("合法流转不抛异常")
    void validTransitionPasses() {
      machine.requireTransition(FlowInstanceStatus.RUNNING, FlowInstanceStatus.COMPLETED);
    }

    @Test
    @DisplayName("非法流转抛 WorkflowException")
    void invalidTransitionThrows() {
      assertThatThrownBy(() -> machine.requireTransition(FlowInstanceStatus.TERMINATED, FlowInstanceStatus.RUNNING))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("getAvailableTransitions 可用流转集合")
  class AvailableTransitions {

    @Test
    @DisplayName("RUNNING 有 6 个可流转目标")
    void runningTransitions() {
      Set<FlowInstanceStatus> available = machine.getAvailableTransitions(FlowInstanceStatus.RUNNING);
      assertThat(available)
          .contains(
              FlowInstanceStatus.SUSPENDED,
              FlowInstanceStatus.COMPLETED,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR,
              FlowInstanceStatus.ROLLED_BACK)
          .hasSize(6);
    }

    @Test
    @DisplayName("SUSPENDED 有 4 个可流转目标")
    void suspendedTransitions() {
      Set<FlowInstanceStatus> available = machine.getAvailableTransitions(FlowInstanceStatus.SUSPENDED);
      assertThat(available)
          .contains(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR)
          .hasSize(4);
    }

    @Test
    @DisplayName("ERROR 有 4 个可流转目标")
    void errorTransitions() {
      Set<FlowInstanceStatus> available = machine.getAvailableTransitions(FlowInstanceStatus.ERROR);
      assertThat(available)
          .contains(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ROLLED_BACK)
          .hasSize(4);
    }

    @Test
    @DisplayName("COMPLETED 仅可流转到 ROLLED_BACK（终态但有出边）")
    void completedTransitions() {
      assertThat(machine.getAvailableTransitions(FlowInstanceStatus.COMPLETED))
          .containsExactly(FlowInstanceStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("DRAFT 可流转到 RUNNING / TERMINATED")
    void draftTransitions() {
      assertThat(machine.getAvailableTransitions(FlowInstanceStatus.DRAFT))
          .containsExactlyInAnyOrder(FlowInstanceStatus.RUNNING, FlowInstanceStatus.TERMINATED);
    }

    @Test
    @DisplayName("终态 TERMINATED / REJECTED / ROLLED_BACK 返回空集合")
    void terminalReturnsEmpty() {
      assertThat(machine.getAvailableTransitions(FlowInstanceStatus.TERMINATED)).isEmpty();
      assertThat(machine.getAvailableTransitions(FlowInstanceStatus.REJECTED)).isEmpty();
      assertThat(machine.getAvailableTransitions(FlowInstanceStatus.ROLLED_BACK)).isEmpty();
    }

    @Test
    @DisplayName("null 抛 WorkflowException")
    void nullThrows() {
      assertThatThrownBy(() -> machine.getAvailableTransitions(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("状态分类")
  class StatusCategory {

    @Test
    @DisplayName("活跃态集合为 RUNNING/SUSPENDED/ERROR")
    void activeStatuses() {
      assertThat(machine.getActiveStatuses())
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.SUSPENDED,
              FlowInstanceStatus.ERROR);
    }

    @Test
    @DisplayName("终态集合为 TERMINATED/REJECTED/ROLLED_BACK（不含 COMPLETED）")
    void terminalStatuses() {
      assertThat(machine.getTerminalStatuses())
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("isTerminal 与枚举一致")
    void isTerminalConsistent() {
      for (FlowInstanceStatus status : FlowInstanceStatus.values()) {
        assertThat(machine.isTerminal(status)).isEqualTo(status.isTerminal());
      }
    }

    @Test
    @DisplayName("终态语义与 canTransitTo 不冲突（P0 守护）")
    void terminalConsistentWithCanTransitTo() {
      for (FlowInstanceStatus status : FlowInstanceStatus.values()) {
        boolean terminal = status.isTerminal();
        for (FlowInstanceStatus target : FlowInstanceStatus.values()) {
          if (target == status) {
            continue;
          }
          // 任何“可流转到非自身状态”的状态都不应被判定为终态
          if (status.canTransitTo(target)) {
            assertThat(terminal)
                .as("%s 可流转到 %s，不应被判定为终态", status, target)
                .isFalse();
          }
        }
      }
    }

    @Test
    @DisplayName("getTerminalStatuses 与 isTerminal 完全一致")
    void terminalSetConsistentWithIsTerminal() {
      for (FlowInstanceStatus status : FlowInstanceStatus.values()) {
        assertThat(machine.getTerminalStatuses().contains(status))
            .as("getTerminalStatuses 与 isTerminal 对 %s 的判定应一致", status)
            .isEqualTo(status.isTerminal());
      }
    }

    @Test
    @DisplayName("COMPLETED 可撤销（→ ROLLED_BACK），但 isFinished 仍为 true")
    void completedCanBeRolledBack() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isTrue();
      assertThat(FlowInstanceStatus.COMPLETED.isTerminal()).isFalse();
      assertThat(FlowInstanceStatus.COMPLETED.isFinished()).isTrue();
      assertThat(machine.getTerminalStatuses())
          .doesNotContain(FlowInstanceStatus.COMPLETED);
    }

    @Test
    @DisplayName("isActive 仅 RUNNING/SUSPENDED/ERROR")
    void isActiveOnlyActiveStates() {
      assertThat(machine.isActive(FlowInstanceStatus.RUNNING)).isTrue();
      assertThat(machine.isActive(FlowInstanceStatus.SUSPENDED)).isTrue();
      assertThat(machine.isActive(FlowInstanceStatus.ERROR)).isTrue();
      assertThat(machine.isActive(FlowInstanceStatus.COMPLETED)).isFalse();
      assertThat(machine.isActive(FlowInstanceStatus.TERMINATED)).isFalse();
      assertThat(machine.isActive(FlowInstanceStatus.REJECTED)).isFalse();
      assertThat(machine.isActive(FlowInstanceStatus.ROLLED_BACK)).isFalse();
      assertThat(machine.isActive(FlowInstanceStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("isRunning 仅 RUNNING")
    void isRunningOnlyRunning() {
      assertThat(machine.isRunning(FlowInstanceStatus.RUNNING)).isTrue();
      assertThat(machine.isRunning(FlowInstanceStatus.SUSPENDED)).isFalse();
      assertThat(machine.isRunning(FlowInstanceStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("isTerminal(null) 抛异常")
    void isTerminalNullThrows() {
      assertThatThrownBy(() -> machine.isTerminal(null))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("isActive(null) 抛异常")
    void isActiveNullThrows() {
      assertThatThrownBy(() -> machine.isActive(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("getAllStates 全量状态")
  class AllStates {

    @Test
    @DisplayName("返回全部 8 个枚举值")
    void returnsAll() {
      assertThat(machine.getAllStates()).containsExactly(FlowInstanceStatus.values());
    }
  }
}
