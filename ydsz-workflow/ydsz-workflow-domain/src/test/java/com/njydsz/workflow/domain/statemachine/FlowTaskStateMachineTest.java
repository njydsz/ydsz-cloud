package com.njydsz.workflow.domain.statemachine;


import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.exception.WorkflowException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FlowTaskStateMachine} 单元测试。
 *
 * <p>覆盖：状态流转合法性、终态不可流转、活跃/暂停态集合、签收判定、空参防御。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("任务状态机")
class FlowTaskStateMachineTest {

  private final FlowTaskStateMachine machine = new FlowTaskStateMachine();

  @Nested
  @DisplayName("validateTransition 状态流转校验")
  class ValidateTransition {

    @Test
    @DisplayName("PENDING → CLAIMED 允许")
    void pendingToClaimed() {
      assertThat(machine.validateTransition(FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED)).isTrue();
    }

    @Test
    @DisplayName("PENDING → COMPLETED 允许")
    void pendingToCompleted() {
      assertThat(machine.validateTransition(FlowTaskStatus.PENDING, FlowTaskStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("CLAIMED → COMPLETED 允许")
    void claimedToCompleted() {
      assertThat(machine.validateTransition(FlowTaskStatus.CLAIMED, FlowTaskStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("DRAFT → PENDING 允许（草稿提交）")
    void draftToPending() {
      assertThat(machine.validateTransition(FlowTaskStatus.DRAFT, FlowTaskStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("DELEGATED → PENDING 允许（退回原办理人）")
    void delegatedToPending() {
      assertThat(machine.validateTransition(FlowTaskStatus.DELEGATED, FlowTaskStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("FROZEN → PENDING 允许（激活恢复）")
    void frozenToPending() {
      assertThat(machine.validateTransition(FlowTaskStatus.FROZEN, FlowTaskStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("相同状态返回 true（幂等）")
    void sameStateIsAllowed() {
      assertThat(machine.validateTransition(FlowTaskStatus.PENDING, FlowTaskStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("终态 → 任意状态 拒绝")
    void terminalToAnyRejected() {
      for (FlowTaskStatus terminal : machine.getTerminalStatuses()) {
        for (FlowTaskStatus target : FlowTaskStatus.values()) {
          if (target == terminal) {
            continue;
          }
          assertThat(machine.validateTransition(terminal, target))
              .as("%s → %s 应拒绝", terminal, target)
              .isFalse();
        }
      }
    }

    @Test
    @DisplayName("PENDING → FROZEN 允许（实例挂起连带）")
    void pendingToFrozen() {
      assertThat(machine.validateTransition(FlowTaskStatus.PENDING, FlowTaskStatus.FROZEN)).isTrue();
    }

    @Test
    @DisplayName("PENDING → SKIPPED 允许")
    void pendingToSkipped() {
      assertThat(machine.validateTransition(FlowTaskStatus.PENDING, FlowTaskStatus.SKIPPED)).isTrue();
    }

    @Test
    @DisplayName("current 为 null 抛异常")
    void nullCurrentThrows() {
      assertThatThrownBy(() -> machine.validateTransition(null, FlowTaskStatus.CLAIMED))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("target 为 null 抛异常")
    void nullTargetThrows() {
      assertThatThrownBy(() -> machine.validateTransition(FlowTaskStatus.PENDING, null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("requireTransition 强制校验")
  class RequireTransition {

    @Test
    @DisplayName("合法流转不抛异常")
    void validTransitionPasses() {
      machine.requireTransition(FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED);
    }

    @Test
    @DisplayName("非法流转抛 WorkflowException")
    void invalidTransitionThrows() {
      assertThatThrownBy(() -> machine.requireTransition(FlowTaskStatus.COMPLETED, FlowTaskStatus.PENDING))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("getAvailableTransitions 可用流转集合")
  class AvailableTransitions {

    @Test
    @DisplayName("PENDING 有 9 个可流转目标")
    void pendingTransitions() {
      Set<FlowTaskStatus> available = machine.getAvailableTransitions(FlowTaskStatus.PENDING);
      assertThat(available)
          .contains(
              FlowTaskStatus.CLAIMED,
              FlowTaskStatus.COMPLETED,
              FlowTaskStatus.REJECTED,
              FlowTaskStatus.SKIPPED,
              FlowTaskStatus.CANCELLED,
              FlowTaskStatus.DELEGATED,
              FlowTaskStatus.FROZEN,
              FlowTaskStatus.SUSPENDED,
              FlowTaskStatus.DRAFT)
          .hasSize(9);
    }

    @Test
    @DisplayName("终态返回空集合")
    void terminalReturnsEmpty() {
      for (FlowTaskStatus terminal : machine.getTerminalStatuses()) {
        assertThat(machine.getAvailableTransitions(terminal))
            .as("%s 应为空集合", terminal)
            .isEmpty();
      }
    }

    @Test
    @DisplayName("DRAFT 只能流转到 PENDING/CLAIMED")
    void draftTransitions() {
      assertThat(machine.getAvailableTransitions(FlowTaskStatus.DRAFT))
          .containsExactlyInAnyOrder(FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED);
    }

    @Test
    @DisplayName("FROZEN 只能流转到 PENDING")
    void frozenTransitions() {
      assertThat(machine.getAvailableTransitions(FlowTaskStatus.FROZEN))
          .containsExactly(FlowTaskStatus.PENDING);
    }

    @Test
    @DisplayName("null 抛异常")
    void nullThrows() {
      assertThatThrownBy(() -> machine.getAvailableTransitions(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  @Nested
  @DisplayName("状态分类")
  class StatusCategory {

    @Test
    @DisplayName("终态集合为 5 个")
    void terminalStatuses() {
      assertThat(machine.getTerminalStatuses())
          .containsExactlyInAnyOrder(
              FlowTaskStatus.COMPLETED,
              FlowTaskStatus.REJECTED,
              FlowTaskStatus.SKIPPED,
              FlowTaskStatus.CANCELLED,
              FlowTaskStatus.TIMEOUT);
    }

    @Test
    @DisplayName("活跃态集合为 PENDING/CLAIMED/DRAFT")
    void activeStatuses() {
      assertThat(machine.getActiveStatuses())
          .containsExactlyInAnyOrder(
              FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED, FlowTaskStatus.DRAFT);
    }

    @Test
    @DisplayName("暂停态集合为 FROZEN/SUSPENDED")
    void pausedStatuses() {
      assertThat(machine.getPausedStatuses())
          .containsExactlyInAnyOrder(FlowTaskStatus.FROZEN, FlowTaskStatus.SUSPENDED);
    }

    @Test
    @DisplayName("isTerminal 与枚举一致")
    void isTerminalConsistent() {
      for (FlowTaskStatus status : FlowTaskStatus.values()) {
        assertThat(machine.isTerminal(status)).isEqualTo(status.isTerminal());
      }
    }

    @Test
    @DisplayName("isActive 仅 PENDING/CLAIMED/DRAFT")
    void isActiveOnlyActiveStates() {
      assertThat(machine.isActive(FlowTaskStatus.PENDING)).isTrue();
      assertThat(machine.isActive(FlowTaskStatus.CLAIMED)).isTrue();
      assertThat(machine.isActive(FlowTaskStatus.DRAFT)).isTrue();
      assertThat(machine.isActive(FlowTaskStatus.COMPLETED)).isFalse();
      assertThat(machine.isActive(FlowTaskStatus.FROZEN)).isFalse();
    }
  }

  @Nested
  @DisplayName("requiresClaim 签收判定")
  class RequiresClaim {

    @Test
    @DisplayName("PENDING → COMPLETED 需要签收")
    void pendingToCompletedNeedsClaim() {
      assertThat(machine.requiresClaim(FlowTaskStatus.PENDING, FlowTaskStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("PENDING → REJECTED 需要签收")
    void pendingToRejectedNeedsClaim() {
      assertThat(machine.requiresClaim(FlowTaskStatus.PENDING, FlowTaskStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("CLAIMED → COMPLETED 不需要签收")
    void claimedToCompletedNoClaim() {
      assertThat(machine.requiresClaim(FlowTaskStatus.CLAIMED, FlowTaskStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("null 参数返回 false")
    void nullReturnsFalse() {
      assertThat(machine.requiresClaim(null, FlowTaskStatus.COMPLETED)).isFalse();
      assertThat(machine.requiresClaim(FlowTaskStatus.PENDING, null)).isFalse();
    }
  }

  @Nested
  @DisplayName("getAllStates 全量状态")
  class AllStates {

    @Test
    @DisplayName("返回全部枚举值")
    void returnsAll() {
      assertThat(machine.getAllStates()).containsExactly(FlowTaskStatus.values());
    }
  }
}
