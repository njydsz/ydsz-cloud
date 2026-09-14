package com.njydsz.workflow.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link FlowInstanceStatus} 单元测试
 *
 * <p>验证流程实例状态机的核心行为：终态判定（{@link FlowInstanceStatus#isFinished()}）
 * 和状态流转规则（{@link FlowInstanceStatus#canTransitTo(FlowInstanceStatus)}）。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Tag("unit")
@DisplayName("FlowInstanceStatus 状态机测试")
class FlowInstanceStatusTest {

  @Nested
  @DisplayName("终态判定")
  class IsFinishedTest {

    @Test
    @DisplayName("COMPLETED 应为终态")
    void completedShouldBeFinished() {
      assertThat(FlowInstanceStatus.COMPLETED.isFinished()).isTrue();
    }

    @Test
    @DisplayName("TERMINATED 应为终态")
    void terminatedShouldBeFinished() {
      assertThat(FlowInstanceStatus.TERMINATED.isFinished()).isTrue();
    }

    @Test
    @DisplayName("REJECTED 应为终态")
    void rejectedShouldBeFinished() {
      assertThat(FlowInstanceStatus.REJECTED.isFinished()).isTrue();
    }

    @Test
    @DisplayName("ROLLED_BACK 应为终态")
    void rolledBackShouldBeFinished() {
      assertThat(FlowInstanceStatus.ROLLED_BACK.isFinished()).isTrue();
    }

    @Test
    @DisplayName("RUNNING 不应为终态")
    void runningShouldNotBeFinished() {
      assertThat(FlowInstanceStatus.RUNNING.isFinished()).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED 不应为终态")
    void suspendedShouldNotBeFinished() {
      assertThat(FlowInstanceStatus.SUSPENDED.isFinished()).isFalse();
    }

    @Test
    @DisplayName("ERROR 不应为终态")
    void errorShouldNotBeFinished() {
      assertThat(FlowInstanceStatus.ERROR.isFinished()).isFalse();
    }

    @Test
    @DisplayName("DRAFT 不应为终态")
    void draftShouldNotBeFinished() {
      assertThat(FlowInstanceStatus.DRAFT.isFinished()).isFalse();
    }
  }

  @Nested
  @DisplayName("流转规则")
  class CanTransitToTest {

    @Test
    @DisplayName("RUNNING 可流转到 SUSPENDED")
    void runningCanTransitToSuspended() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.SUSPENDED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可流转到 COMPLETED")
    void runningCanTransitToCompleted() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可流转到 TERMINATED")
    void runningCanTransitToTerminated() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可流转到 REJECTED")
    void runningCanTransitToRejected() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可流转到 ERROR")
    void runningCanTransitToError() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.ERROR)).isTrue();
    }

    @Test
    @DisplayName("RUNNING 可流转到 ROLLED_BACK")
    void runningCanTransitToRolledBack() {
      assertThat(FlowInstanceStatus.RUNNING.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED 可流转到 RUNNING（恢复）")
    void suspendedCanTransitToRunning() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED 可流转到 TERMINATED")
    void suspendedCanTransitToTerminated() {
      assertThat(FlowInstanceStatus.SUSPENDED.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("ERROR 可流转到 RUNNING（重试）")
    void errorCanTransitToRunning() {
      assertThat(FlowInstanceStatus.ERROR.canTransitTo(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("COMPLETED 可流转到 ROLLED_BACK")
    void completedCanTransitToRolledBack() {
      assertThat(FlowInstanceStatus.COMPLETED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isTrue();
    }

    @Test
    @DisplayName("DRAFT 可流转到 RUNNING")
    void draftCanTransitToRunning() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("DRAFT 可流转到 TERMINATED（取消）")
    void draftCanTransitToTerminated() {
      assertThat(FlowInstanceStatus.DRAFT.canTransitTo(FlowInstanceStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("TERMINATED 不可再流转到其他状态")
    void terminatedCannotTransit() {
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.RUNNING)).isFalse();
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
      assertThat(FlowInstanceStatus.TERMINATED.canTransitTo(FlowInstanceStatus.ROLLED_BACK)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 不可再流转到其他状态")
    void rejectedCannotTransit() {
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.RUNNING)).isFalse();
      assertThat(FlowInstanceStatus.REJECTED.canTransitTo(FlowInstanceStatus.COMPLETED)).isFalse();
    }
  }
}
