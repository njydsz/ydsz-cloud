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
  @DisplayName("DRAFT 状态流转 — 正向")
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

    @Test
    @DisplayName("DRAFT 不能流转到 ERROR")
    void draft_cannotTransitTo_error() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.ERROR)).isFalse();
    }

    @Test
    @DisplayName("DRAFT 不能流转到 REJECTED")
    void draft_cannotTransitTo_rejected() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("DRAFT 不能流转到 ROLLED_BACK")
    void draft_cannotTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isFalse();
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

    @Test
    @DisplayName("SUSPENDED 不能流转到 REJECTED")
    void suspended_cannotTransitTo_rejected() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED 不能流转到 ERROR")
    void suspended_cannotTransitTo_error() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.ERROR)).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED 不能流转到 ROLLED_BACK")
    void suspended_cannotTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED 不能流转到 DRAFT")
    void suspended_cannotTransitTo_draft() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.DRAFT)).isFalse();
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

    @Test
    @DisplayName("COMPLETED 不能流转到 REJECTED")
    void completed_cannotTransitTo_rejected() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("COMPLETED 不能流转到 ERROR")
    void completed_cannotTransitTo_error() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.ERROR)).isFalse();
    }

    @Test
    @DisplayName("COMPLETED 不能流转到 SUSPENDED")
    void completed_cannotTransitTo_suspended() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("COMPLETED 不能流转到 DRAFT")
    void completed_cannotTransitTo_draft() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.DRAFT)).isFalse();
    }
  }

  @Nested
  @DisplayName("ERROR 状态流转 — 正向")
  class ErrorTransitions {

    @Test
    @DisplayName("ERROR 可以流转到 RUNNING（重试）")
    void error_canTransitTo_running() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("ERROR 可以流转到 TERMINATED（放弃异常实例）")
    void error_canTransitTo_terminated() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("ERROR 可以流转到 REJECTED（驳回异常实例）")
    void error_canTransitTo_rejected() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("ERROR 可以流转到 ROLLED_BACK（撤销）")
    void error_canTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isTrue();
    }
  }

  @Nested
  @DisplayName("ERROR 状态流转 — 负向")
  class ErrorNegatives {

    @Test
    @DisplayName("ERROR 不能流转到 COMPLETED")
    void error_cannotTransitTo_completed() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("ERROR 不能流转到 SUSPENDED")
    void error_cannotTransitTo_suspended() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("ERROR 不能流转到 DRAFT")
    void error_cannotTransitTo_draft() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.DRAFT)).isFalse();
    }
  }

  @Nested
  @DisplayName("中间态：ERROR 非终态，可被恢复")
  class IntermediateErrorSemantics {

    @Test
    @DisplayName("ERROR 不是终态")
    void error_isNotTerminal() {
      assertThat(FlowInstanceStatus.ERROR.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("ERROR 不是完成态")
    void error_isNotFinished() {
      assertThat(FlowInstanceStatus.ERROR.isFinished()).isFalse();
    }
  }

  @Nested
  @DisplayName("中间态：DRAFT/RUNNING/SUSPENDED 非终态")
  class IntermediateNonTerminalSemantics {

    @Test
    @DisplayName("DRAFT 不是终态")
    void draft_isNotTerminal() {
      assertThat(FlowInstanceStatus.DRAFT.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RUNNING 不是终态")
    void running_isNotTerminal() {
      assertThat(FlowInstanceStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED 不是终态")
    void suspended_isNotTerminal() {
      assertThat(FlowInstanceStatus.SUSPENDED.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("TERMINATED 终态负向")
  class TerminatedNegatives {

    @Test
    @DisplayName("TERMINATED 不能流转到 COMPLETED")
    void terminated_cannotTransitTo_completed() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("TERMINATED 不能流转到 REJECTED")
    void terminated_cannotTransitTo_rejected() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("TERMINATED 不能流转到 ROLLED_BACK")
    void terminated_cannotTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isFalse();
    }

    @Test
    @DisplayName("TERMINATED 不能流转到 ERROR")
    void terminated_cannotTransitTo_error() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.ERROR)).isFalse();
    }

    @Test
    @DisplayName("TERMINATED 不能流转到 SUSPENDED")
    void terminated_cannotTransitTo_suspended() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("TERMINATED 不能流转到 DRAFT")
    void terminated_cannotTransitTo_draft() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.DRAFT)).isFalse();
    }
  }

  @Nested
  @DisplayName("REJECTED 终态负向")
  class RejectedNegatives {

    @Test
    @DisplayName("REJECTED 不能流转到 COMPLETED")
    void rejected_cannotTransitTo_completed() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 不能流转到 TERMINATED")
    void rejected_cannotTransitTo_terminated() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.TERMINATED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 不能流转到 ROLLED_BACK")
    void rejected_cannotTransitTo_rolledBack() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 不能流转到 ERROR")
    void rejected_cannotTransitTo_error() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.ERROR)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 不能流转到 SUSPENDED")
    void rejected_cannotTransitTo_suspended() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 不能流转到 DRAFT")
    void rejected_cannotTransitTo_draft() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.DRAFT)).isFalse();
    }
  }

  @Nested
  @DisplayName("ROLLED_BACK 终态负向")
  class RolledBackNegatives {

    @Test
    @DisplayName("ROLLED_BACK 不能流转到 COMPLETED")
    void rolledBack_cannotTransitTo_completed() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("ROLLED_BACK 不能流转到 TERMINATED")
    void rolledBack_cannotTransitTo_terminated() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.TERMINATED)).isFalse();
    }

    @Test
    @DisplayName("ROLLED_BACK 不能流转到 REJECTED")
    void rolledBack_cannotTransitTo_rejected() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("ROLLED_BACK 不能流转到 ERROR")
    void rolledBack_cannotTransitTo_error() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.ERROR)).isFalse();
    }

    @Test
    @DisplayName("ROLLED_BACK 不能流转到 SUSPENDED")
    void rolledBack_cannotTransitTo_suspended() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("ROLLED_BACK 不能流转到 DRAFT")
    void rolledBack_cannotTransitTo_draft() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.canTransitTo(FlowInstanceStatus.DRAFT)).isFalse();
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
