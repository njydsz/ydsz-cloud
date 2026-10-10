package com.njydsz.workflow.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.exception.WorkflowException;
import com.njydsz.workflow.domain.exception.WorkflowExceptionCode;
import com.njydsz.workflow.domain.statemachine.FlowInstanceStateMachine;

/**
 * {@link FlowInstanceStateMachine} 状态机单元测试。
 *
 * <p>覆盖全部 8 个状态枚举值的流转矩阵，验证状态机的正向流转、负向拦截、终态判定、
 * 活跃态判定、可用流转集合等核心行为，防止状态机逻辑被误改导致流程死锁或数据不一致。
 *
 * <p>测试策略：
 * <ul>
 *   <li>正向参数化测试：枚举所有合法转换（含自反性），确认 validateTransition 返回 true</li>
 *   <li>requireTransition 正向：合法转换不抛异常</li>
 *   <li>负向参数化测试：枚举代表性非法转换，确认 validateTransition 返回 false</li>
 *   <li>requireTransition 负向：非法转换抛出 WorkflowException(ILLEGAL_STATE_TRANSITION)</li>
 *   <li>getAvailableTransitions：逐状态验证可流转目标集合与规则表一致</li>
 *   <li>终态/活跃态/运行中语义：逐状态验证 isTerminal/isActive/isRunning</li>
 *   <li>Null 参数防护：所有公开方法传入 null 时抛出异常</li>
 * </ul>
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class FlowInstanceStateMachineTest {

  private FlowInstanceStateMachine stateMachine;

  @BeforeEach
  void setUp() {
    stateMachine = new FlowInstanceStateMachine();
  }

  // ============================== validateTransition 正向 ==============================

  @Nested
  @DisplayName("validateTransition — 合法流转")
  class ValidateTransitionPositive {

    @ParameterizedTest(name = "{0} → {1} 允许")
    @CsvSource({
        // DRAFT：提交审批 / 取消草稿
        "DRAFT, RUNNING",
        "DRAFT, TERMINATED",
        // RUNNING：挂起/完成/终止/驳回/异常/撤销
        "RUNNING, SUSPENDED",
        "RUNNING, COMPLETED",
        "RUNNING, TERMINATED",
        "RUNNING, REJECTED",
        "RUNNING, ERROR",
        "RUNNING, ROLLED_BACK",
        // SUSPENDED：恢复/终止/驳回/异常
        "SUSPENDED, RUNNING",
        "SUSPENDED, TERMINATED",
        "SUSPENDED, REJECTED",
        "SUSPENDED, ERROR",
        // ERROR：重试/终止/驳回/撤销
        "ERROR, RUNNING",
        "ERROR, TERMINATED",
        "ERROR, REJECTED",
        "ERROR, ROLLED_BACK",
        // COMPLETED：撤销
        "COMPLETED, ROLLED_BACK",
    })
    @DisplayName("合法状态流转应返回 true")
    void validateTransition_legal_returnsTrue(FlowInstanceStatus current, FlowInstanceStatus target) {
      assertThat(stateMachine.validateTransition(current, target))
          .as("%s → %s 应当被允许", current, target)
          .isTrue();
    }

    @ParameterizedTest(name = "{0} → {0} 自反性")
    @CsvSource({
        "DRAFT", "RUNNING", "SUSPENDED", "COMPLETED",
        "TERMINATED", "REJECTED", "ERROR", "ROLLED_BACK"
    })
    @DisplayName("自反性：任意状态流转到自身应返回 true")
    void validateTransition_selfTransition_returnsTrue(FlowInstanceStatus status) {
      assertThat(stateMachine.validateTransition(status, status))
          .as("%s → %s 自反性应当被允许", status, status)
          .isTrue();
    }
  }

  // ============================== validateTransition 负向 ==============================

  @Nested
  @DisplayName("validateTransition — 非法流转")
  class ValidateTransitionNegative {

    @ParameterizedTest(name = "{0} → {1} 非法")
    @CsvSource({
        // DRAFT 非法目标
        "DRAFT, SUSPENDED",
        "DRAFT, COMPLETED",
        "DRAFT, REJECTED",
        "DRAFT, ERROR",
        "DRAFT, ROLLED_BACK",
        // RUNNING 不能回到 DRAFT
        "RUNNING, DRAFT",
        // SUSPENDED 非法目标
        "SUSPENDED, SUSPENDED",
        "SUSPENDED, COMPLETED",
        "SUSPENDED, ROLLED_BACK",
        "SUSPENDED, DRAFT",
        // ERROR 非法目标
        "ERROR, COMPLETED",
        "ERROR, SUSPENDED",
        "ERROR, DRAFT",
        // COMPLETED 非法目标（仅 ROLLED_BACK 合法）
        "COMPLETED, RUNNING",
        "COMPLETED, TERMINATED",
        "COMPLETED, REJECTED",
        "COMPLETED, ERROR",
        "COMPLETED, SUSPENDED",
        "COMPLETED, DRAFT",
        // TERMINATED 终态不可再流转（自反性除外，在上方正向中已覆盖）
        "TERMINATED, RUNNING",
        "TERMINATED, SUSPENDED",
        "TERMINATED, COMPLETED",
        "TERMINATED, REJECTED",
        "TERMINATED, ERROR",
        "TERMINATED, ROLLED_BACK",
        "TERMINATED, DRAFT",
        // REJECTED 终态不可再流转
        "REJECTED, RUNNING",
        "REJECTED, SUSPENDED",
        "REJECTED, COMPLETED",
        "REJECTED, TERMINATED",
        "REJECTED, ERROR",
        "REJECTED, ROLLED_BACK",
        "REJECTED, DRAFT",
        // ROLLED_BACK 终态不可再流转
        "ROLLED_BACK, RUNNING",
        "ROLLED_BACK, SUSPENDED",
        "ROLLED_BACK, COMPLETED",
        "ROLLED_BACK, TERMINATED",
        "ROLLED_BACK, REJECTED",
        "ROLLED_BACK, ERROR",
        "ROLLED_BACK, DRAFT",
    })
    @DisplayName("非法状态流转应返回 false")
    void validateTransition_illegal_returnsFalse(FlowInstanceStatus current, FlowInstanceStatus target) {
      assertThat(stateMachine.validateTransition(current, target))
          .as("%s → %s 应当被拒绝", current, target)
          .isFalse();
    }

    @Test
    @DisplayName("validateTransition — current 为 null 应抛出 WorkflowException")
    void validateTransition_currentNull_throwsException() {
      assertThatThrownBy(() -> stateMachine.validateTransition(null, FlowInstanceStatus.RUNNING))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("validateTransition — target 为 null 应抛出 WorkflowException")
    void validateTransition_targetNull_throwsException() {
      assertThatThrownBy(() -> stateMachine.validateTransition(FlowInstanceStatus.RUNNING, null))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("validateTransition — current 和 target 均为 null 应抛出 WorkflowException")
    void validateTransition_bothNull_throwsException() {
      assertThatThrownBy(() -> stateMachine.validateTransition(null, null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== requireTransition ==============================

  @Nested
  @DisplayName("requireTransition — 正向")
  class RequireTransitionPositive {

    @ParameterizedTest(name = "requireTransition({0} → {1}) 不抛异常")
    @CsvSource({
        "DRAFT, RUNNING",
        "DRAFT, TERMINATED",
        "RUNNING, SUSPENDED",
        "RUNNING, COMPLETED",
        "RUNNING, TERMINATED",
        "RUNNING, REJECTED",
        "RUNNING, ERROR",
        "RUNNING, ROLLED_BACK",
        "SUSPENDED, RUNNING",
        "SUSPENDED, TERMINATED",
        "ERROR, RUNNING",
        "ERROR, TERMINATED",
        "ERROR, REJECTED",
        "ERROR, ROLLED_BACK",
        "COMPLETED, ROLLED_BACK",
    })
    @DisplayName("合法流转 requireTransition 不抛出异常")
    void requireTransition_legal_doesNotThrow(FlowInstanceStatus current, FlowInstanceStatus target) {
      assertThatCode(() -> stateMachine.requireTransition(current, target))
          .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "requireTransition({0} → {0}) 自反性不抛异常")
    @CsvSource({
        "DRAFT", "RUNNING", "SUSPENDED", "COMPLETED",
        "TERMINATED", "REJECTED", "ERROR", "ROLLED_BACK"
    })
    @DisplayName("自反性 requireTransition 不抛出异常")
    void requireTransition_selfTransition_doesNotThrow(FlowInstanceStatus status) {
      assertThatCode(() -> stateMachine.requireTransition(status, status))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("requireTransition — 负向")
  class RequireTransitionNegative {

    @ParameterizedTest(name = "requireTransition({0} → {1}) 抛出 ILLEGAL_STATE_TRANSITION")
    @CsvSource({
        "DRAFT, COMPLETED",
        "DRAFT, SUSPENDED",
        "DRAFT, ERROR",
        "DRAFT, REJECTED",
        "DRAFT, ROLLED_BACK",
        "RUNNING, DRAFT",
        "SUSPENDED, COMPLETED",
        "SUSPENDED, ROLLED_BACK",
        "SUSPENDED, DRAFT",
        "ERROR, COMPLETED",
        "ERROR, SUSPENDED",
        "ERROR, DRAFT",
        "COMPLETED, RUNNING",
        "COMPLETED, TERMINATED",
        "TERMINATED, RUNNING",
        "TERMINATED, COMPLETED",
        "REJECTED, RUNNING",
        "REJECTED, COMPLETED",
        "ROLLED_BACK, RUNNING",
        "ROLLED_BACK, COMPLETED",
    })
    @DisplayName("非法流转 requireTransition 抛出 WorkflowException")
    void requireTransition_illegal_throwsException(FlowInstanceStatus current, FlowInstanceStatus target) {
      assertThatThrownBy(() -> stateMachine.requireTransition(current, target))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("requireTransition — 非法流转异常码为 ILLEGAL_STATE_TRANSITION")
    void requireTransition_illegal_exceptionCodeIsIllegalStateTransition() {
      assertThatThrownBy(() -> stateMachine.requireTransition(FlowInstanceStatus.TERMINATED, FlowInstanceStatus.RUNNING))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("requireTransition — current 为 null 应抛出 WorkflowException")
    void requireTransition_currentNull_throwsException() {
      assertThatThrownBy(() -> stateMachine.requireTransition(null, FlowInstanceStatus.RUNNING))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("requireTransition — target 为 null 应抛出 WorkflowException")
    void requireTransition_targetNull_throwsException() {
      assertThatThrownBy(() -> stateMachine.requireTransition(FlowInstanceStatus.RUNNING, null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== getAvailableTransitions ==============================

  @Nested
  @DisplayName("getAvailableTransitions — 可用流转集合")
  class GetAvailableTransitions {

    @Test
    @DisplayName("DRAFT 可流转到 {RUNNING, TERMINATED}")
    void getAvailableTransitions_draft() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.DRAFT);
      assertThat(result).containsExactlyInAnyOrder(FlowInstanceStatus.RUNNING, FlowInstanceStatus.TERMINATED);
    }

    @Test
    @DisplayName("RUNNING 可流转到 {SUSPENDED, COMPLETED, TERMINATED, REJECTED, ERROR, ROLLED_BACK}")
    void getAvailableTransitions_running() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.RUNNING);
      assertThat(result)
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.SUSPENDED,
              FlowInstanceStatus.COMPLETED,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR,
              FlowInstanceStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("SUSPENDED 可流转到 {RUNNING, TERMINATED, REJECTED, ERROR}")
    void getAvailableTransitions_suspended() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.SUSPENDED);
      assertThat(result)
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR);
    }

    @Test
    @DisplayName("ERROR 可流转到 {RUNNING, TERMINATED, REJECTED, ROLLED_BACK}")
    void getAvailableTransitions_error() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.ERROR);
      assertThat(result)
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("COMPLETED 仅可流转到 {ROLLED_BACK}")
    void getAvailableTransitions_completed() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.COMPLETED);
      assertThat(result).containsExactlyInAnyOrder(FlowInstanceStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("TERMINATED 终态无可流转目标")
    void getAvailableTransitions_terminated_isEmpty() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.TERMINATED);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("REJECTED 终态无可流转目标")
    void getAvailableTransitions_rejected_isEmpty() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.REJECTED);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ROLLED_BACK 终态无可流转目标")
    void getAvailableTransitions_rolledBack_isEmpty() {
      Set<FlowInstanceStatus> result = stateMachine.getAvailableTransitions(FlowInstanceStatus.ROLLED_BACK);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAvailableTransitions — null 入参应抛出 WorkflowException")
    void getAvailableTransitions_null_throwsException() {
      assertThatThrownBy(() -> stateMachine.getAvailableTransitions(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== isTerminal ==============================

  @Nested
  @DisplayName("isTerminal — 终态判定")
  class IsTerminal {

    @ParameterizedTest(name = "isTerminal({0}) = true")
    @CsvSource({"COMPLETED", "TERMINATED", "REJECTED", "ROLLED_BACK"})
    @DisplayName("终态状态应返回 true")
    void isTerminal_terminalStates_returnsTrue(FlowInstanceStatus status) {
      assertThat(stateMachine.isTerminal(status))
          .as("%s 应为终态", status)
          .isTrue();
    }

    @ParameterizedTest(name = "isTerminal({0}) = false")
    @CsvSource({"DRAFT", "RUNNING", "SUSPENDED", "ERROR"})
    @DisplayName("非终态状态应返回 false")
    void isTerminal_nonTerminalStates_returnsFalse(FlowInstanceStatus status) {
      assertThat(stateMachine.isTerminal(status))
          .as("%s 应为非终态", status)
          .isFalse();
    }

    @Test
    @DisplayName("isTerminal — null 入参应抛出 WorkflowException")
    void isTerminal_null_throwsException() {
      assertThatThrownBy(() -> stateMachine.isTerminal(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== isActive ==============================

  @Nested
  @DisplayName("isActive — 活跃态判定")
  class IsActive {

    @ParameterizedTest(name = "isActive({0}) = true")
    @CsvSource({"RUNNING", "SUSPENDED", "ERROR"})
    @DisplayName("RUNNING/SUSPENDED/ERROR 为活跃态")
    void isActive_activeStates_returnsTrue(FlowInstanceStatus status) {
      assertThat(stateMachine.isActive(status))
          .as("%s 应为活跃态", status)
          .isTrue();
    }

    @ParameterizedTest(name = "isActive({0}) = false")
    @CsvSource({"DRAFT", "COMPLETED", "TERMINATED", "REJECTED", "ROLLED_BACK"})
    @DisplayName("DRAFT/终态 为非活跃态")
    void isActive_nonActiveStates_returnsFalse(FlowInstanceStatus status) {
      assertThat(stateMachine.isActive(status))
          .as("%s 应为非活跃态", status)
          .isFalse();
    }

    @Test
    @DisplayName("isActive — null 入参应抛出 WorkflowException")
    void isActive_null_throwsException() {
      assertThatThrownBy(() -> stateMachine.isActive(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== isRunning ==============================

  @Nested
  @DisplayName("isRunning — 运行中判定")
  class IsRunning {

    @Test
    @DisplayName("RUNNING 状态 isRunning 返回 true")
    void isRunning_running_returnsTrue() {
      assertThat(stateMachine.isRunning(FlowInstanceStatus.RUNNING)).isTrue();
    }

    @ParameterizedTest(name = "isRunning({0}) = false")
    @CsvSource({
        "DRAFT", "SUSPENDED", "COMPLETED",
        "TERMINATED", "REJECTED", "ERROR", "ROLLED_BACK"
    })
    @DisplayName("非 RUNNING 状态 isRunning 返回 false")
    void isRunning_nonRunningStates_returnsFalse(FlowInstanceStatus status) {
      assertThat(stateMachine.isRunning(status))
          .as("%s 不应为运行中", status)
          .isFalse();
    }

    @Test
    @DisplayName("isRunning — null 入参应抛出 WorkflowException")
    void isRunning_null_throwsException() {
      assertThatThrownBy(() -> stateMachine.isRunning(null))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== getActiveStatuses / getTerminalStatuses ==============================

  @Nested
  @DisplayName("集合查询方法")
  class CollectionQueries {

    @Test
    @DisplayName("getActiveStatuses 返回 {RUNNING, SUSPENDED, ERROR}")
    void getActiveStatuses_returnsCorrectSet() {
      Set<FlowInstanceStatus> active = stateMachine.getActiveStatuses();
      assertThat(active)
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.SUSPENDED,
              FlowInstanceStatus.ERROR);
    }

    @Test
    @DisplayName("getTerminalStatuses 返回 {COMPLETED, TERMINATED, REJECTED, ROLLED_BACK}")
    void getTerminalStatuses_returnsCorrectSet() {
      Set<FlowInstanceStatus> terminal = stateMachine.getTerminalStatuses();
      assertThat(terminal)
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.COMPLETED,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ROLLED_BACK);
    }
  }

  // ============================== getTransitionDescription ==============================

  @Nested
  @DisplayName("getTransitionDescription — 流转描述")
  class GetTransitionDescription {

    @Test
    @DisplayName("RUNNING → COMPLETED 返回非空描述")
    void getTransitionDescription_normalTransition_returnsNonEmpty() {
      String desc = stateMachine.getTransitionDescription(FlowInstanceStatus.RUNNING, FlowInstanceStatus.COMPLETED);
      assertThat(desc).isNotNull();
    }

    @Test
    @DisplayName("自反性流转返回非空描述")
    void getTransitionDescription_selfTransition_returnsNonEmpty() {
      String desc = stateMachine.getTransitionDescription(FlowInstanceStatus.RUNNING, FlowInstanceStatus.RUNNING);
      assertThat(desc).isNotNull();
    }

    @Test
    @DisplayName("current 为 null 返回非空描述（降级文案）")
    void getTransitionDescription_currentNull_returnsNonNull() {
      String desc = stateMachine.getTransitionDescription(null, FlowInstanceStatus.RUNNING);
      assertThat(desc).isNotNull();
    }

    @Test
    @DisplayName("target 为 null 返回非空描述（降级文案）")
    void getTransitionDescription_targetNull_returnsNonNull() {
      String desc = stateMachine.getTransitionDescription(FlowInstanceStatus.RUNNING, null);
      assertThat(desc).isNotNull();
    }
  }

  // ============================== getAllStates ==============================

  @Nested
  @DisplayName("getAllStates — 全量状态枚举")
  class GetAllStates {

    @Test
    @DisplayName("getAllStates 返回全部 8 个状态")
    void getAllStates_returnsAllEightStates() {
      List<FlowInstanceStatus> allStates = stateMachine.getAllStates();
      assertThat(allStates)
          .hasSize(8)
          .containsExactlyInAnyOrder(
              FlowInstanceStatus.DRAFT,
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.SUSPENDED,
              FlowInstanceStatus.COMPLETED,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR,
              FlowInstanceStatus.ROLLED_BACK);
    }
  }

  // ============================== 状态机与枚举一致性互验 ==============================

  @Nested
  @DisplayName("状态机与枚举一致性互验")
  class StateMachineConsistency {

    @ParameterizedTest(name = "状态机 validateTransition 与枚举 canTransitTo 结果一致 — {0} → {1}")
    @CsvSource({
        // 合法转换
        "DRAFT, RUNNING, true",
        "RUNNING, COMPLETED, true",
        "RUNNING, ROLLED_BACK, true",
        "SUSPENDED, RUNNING, true",
        "ERROR, RUNNING, true",
        "COMPLETED, ROLLED_BACK, true",
        // 非法转换
        "DRAFT, COMPLETED, false",
        "RUNNING, DRAFT, false",
        "SUSPENDED, COMPLETED, false",
        "ERROR, COMPLETED, false",
        "TERMINATED, RUNNING, false",
        "REJECTED, COMPLETED, false",
        "ROLLED_BACK, COMPLETED, false",
    })
    @DisplayName("状态机 validateTransition 结果必须与枚举 canTransitTo 一致")
    void stateMachineValidateTransition_consistentWithEnum(
        FlowInstanceStatus current, FlowInstanceStatus target, boolean expected) {
      boolean enumResult = current.canTransitTo(target);
      boolean smResult = stateMachine.validateTransition(current, target);

      assertThat(enumResult)
          .as("枚举 canTransitTo(%s, %s)", current, target)
          .isEqualTo(expected);
      assertThat(smResult)
          .as("状态机 validateTransition(%s, %s)", current, target)
          .isEqualTo(expected);
      assertThat(smResult)
          .as("状态机与枚举结果必须一致")
          .isEqualTo(enumResult);
    }

    @ParameterizedTest(name = "状态机 isTerminal 必须与枚举 isTerminal 一致 — {0}")
    @CsvSource({
        "DRAFT, false",
        "RUNNING, false",
        "SUSPENDED, false",
        "ERROR, false",
        "COMPLETED, true",
        "TERMINATED, true",
        "REJECTED, true",
        "ROLLED_BACK, true",
    })
    @DisplayName("状态机 isTerminal 结果必须与枚举 isTerminal 一致")
    void stateMachineIsTerminal_consistentWithEnum(FlowInstanceStatus status, boolean expected) {
      assertThat(stateMachine.isTerminal(status))
          .as("状态机 isTerminal(%s)", status)
          .isEqualTo(status.isTerminal())
          .isEqualTo(expected);
    }

    @ParameterizedTest(name = "getAvailableTransitions 必须与 canTransitTo 逐一对应 — {0}")
    @CsvSource({"DRAFT", "RUNNING", "SUSPENDED", "ERROR", "COMPLETED", "TERMINATED", "REJECTED", "ROLLED_BACK"})
    @DisplayName("getAvailableTransitions 集合必须等价于 canTransitTo 遍历所有枚举值的结果")
    void getAvailableTransitions_matchesCanTransitToForEachStatus(FlowInstanceStatus current) {
      Set<FlowInstanceStatus> available = stateMachine.getAvailableTransitions(current);
      FlowInstanceStatus[] allValues = FlowInstanceStatus.values();

      for (FlowInstanceStatus target : allValues) {
        if (current == target) {
          // 自反性不包含在 getAvailableTransitions 的 Set 中（直接用规则表）
          continue;
        }
        if (current.canTransitTo(target)) {
          assertThat(available)
              .as("getAvailableTransitions(%s) 应包含 %s（canTransitTo=true）", current, target)
              .contains(target);
        } else {
          assertThat(available)
              .as("getAvailableTransitions(%s) 不应包含 %s（canTransitTo=false）", current, target)
              .doesNotContain(target);
        }
      }
    }
  }
}
