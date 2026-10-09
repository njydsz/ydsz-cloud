package com.njydsz.workflow.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link FlowInstanceStatus} 状态流转矩阵测试。
 *
 * <p>验证每一项合法/非法的状态转换是否符合工作流生命周期定义，防止状态机逻辑被误改导致流程死锁或数据不一致。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FlowInstanceStatusStateTransitionTest {

  @Nested
  @DisplayName("DRAFT 状态流转")
  class DraftTransitions {

    @Test
    @DisplayName("DRAFT 可以流转到 RUNNING（正式提交）")
    void draft_canTransitTo_running() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("DRAFT 可以流转到 TERMINATED（取消草稿）")
    void draft_canTransitTo_terminated() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("DRAFT 不能直接流转到 COMPLETED")
    void draft_cannotTransitTo_completed() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("DRAFT 不能流转到 SUSPENDED")
    void draft_cannotTransitTo_suspended() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.SUSPENDED)).isFalse();
    }
  }

  @Nested
  @DisplayName("RUNNING 状态流转")
  class RunningTransitions {

    @Test
    @DisplayName("RUNNING 可以流转到 SUSPENDED（挂起）")
    void running_canTransitTo_suspended() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.SUSPENDED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可以流转到 COMPLETED（正常完成）")
    void running_canTransitTo_completed() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可以流转到 TERMINATED（强制终止）")
    void running_canTransitTo_terminated() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可以流转到 REJECTED（被驳回）")
    void running_canTransitTo_rejected() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可以流转到 ERROR（执行异常）")
    void running_canTransitTo_error() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.ERROR)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可以流转到 ROLLED_BACK（撤销）")
    void running_canTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isTrue();
    }
  }

  @Nested
  @DisplayName("SUSPENDED 状态流转")
  class SuspendedTransitions {

    @Test
    @DisplayName("SUSPENDED 可以流转到 RUNNING（恢复）")
    void suspended_canTransitTo_running() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED 可以流转到 TERMINATED")
    void suspended_canTransitTo_terminated() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED 不能流转到 COMPLETED（需先恢复为 RUNNING）")
    void suspended_cannotTransitTo_completed() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }
  }

  @Nested
  @DisplayName("COMPLETED 状态流转")
  class CompletedTransitions {

    @Test
    @DisplayName("COMPLETED 可以流转到 ROLLED_BACK（撤销已完成实例）")
    void completed_canTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isTrue();
    }

    @Test
    @DisplayName("COMPLETED 不能流转到 RUNNING")
    void completed_cannotTransitTo_running() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.RUNNING)).isFalse();
    }

    @Test
    @DisplayName("COMPLETED 不能流转到 TERMINATED")
    void completed_cannotTransitTo_terminated() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.TERMINATED)).isFalse();
    }
  }

  @Nested
  @DisplayName("终态不可流转")
  class TerminalStates {

    @Test
    @DisplayName("TERMINATED 为终态，不可再流转")
    void terminated_isTerminal_and_cannotTransit() {
      assertThat(FlowInstanceStatus.TERMINATED.isTerminal()).isTrue();
      assertThat(FlowInstanceStatus.TERMINATED.isFinished()).isTrue();
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.RUNNING)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 为终态，不可再流转")
    void rejected_isTerminal_and_cannotTransit() {
      assertThat(FlowInstanceStatus.REJECTED.isTerminal()).isTrue();
      assertThat(FlowInstanceStatus.REJECTED.isFinished()).isTrue();
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.RUNNING)).isFalse();
    }

    @Test
    @DisplayName("ROLLED_BACK 为终态，不可再流转")
    void rolledBack_isTerminal_and_cannotTransit() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.isTerminal()).isTrue();
      assertThat(FlowInstanceStatus.ROLLED_BACK.isFinished()).isTrue();
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.RUNNING)).isFalse();
    }
  }

  @Nested
  @DisplayName("自反性：状态可以流转到自身")
  class ReflexiveTransitions {

    @ParameterizedTest(name = "{0} → {0} = true")
    @CsvSource({"DRAFT", "RUNNING", "SUSPENDED", "COMPLETED", "TERMINATED", "REJECTED", "ERROR", "ROLLED_BACK"})
    @DisplayName("任意状态都可以流转到自身")
    void selfTransition_alwaysAllowed(FlowInstanceStatus status) {
      assertThat(status.canTransitTo(status)).isTrue();
    }
  }
}
