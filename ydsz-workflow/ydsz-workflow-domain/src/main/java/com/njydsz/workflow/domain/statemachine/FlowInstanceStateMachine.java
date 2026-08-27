package com.njydsz.workflow.domain.statemachine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.WorkflowExceptionCode;

/**
 * 流程实例状态机（Domain 层）。
 *
 * <p>封装 {@link FlowInstanceStatus} 的状态流转规则，提供统一的状态变更入口和校验能力。
 * 所有流程实例状态变更必须通过本状态机进行，禁止直接设置 {@code flowStatus} 字段。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>单一职责：只负责状态流转校验，不涉及业务逻辑
 *   <li>不可变：状态机无状态，可安全注入为 Spring 单例
 *   <li>早失败：状态流转非法时立即抛出异常，避免脏数据
 *   <li>对称设计：与 {@link FlowTaskStateMachine}（任务级状态机）形成对称结构
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 校验状态流转是否合法
 * stateMachine.validateTransition(currentStatus, targetStatus);
 *
 * // 执行状态变更（带校验）
 * stateMachine.requireTransition(currentStatus, targetStatus);
 *
 * // 获取当前状态可流转的目标状态集合
 * Set<FlowInstanceStatus> available = stateMachine.getAvailableTransitions(currentStatus);
 * }</pre>
 *
 * <p><b>状态流转规则：</b>
 *
 * <ul>
 *   <li>RUNNING → SUSPENDED / COMPLETED / TERMINATED / REJECTED / ERROR / ROLLED_BACK
 *   <li>SUSPENDED → RUNNING（恢复）/ TERMINATED / REJECTED / ERROR
 *   <li>ERROR → RUNNING（重试）/ TERMINATED / REJECTED / ROLLED_BACK
 *   <li>COMPLETED → ROLLED_BACK（撤销已完成的实例）
 *   <li>TERMINATED / REJECTED / ROLLED_BACK 为终态，不可再流转
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowInstanceStatus 流程实例状态枚举
 * @see FlowTaskStateMachine 任务级状态机（对称设计）
 */
@Slf4j
public class FlowInstanceStateMachine {

  // ============================== 状态流转规则表 ==============================

  /** 运行态可流转目标状态集合 */
  private static final Map<FlowInstanceStatus, Set<FlowInstanceStatus>> RUNNING_TRANSITIONS =
      Map.of(
          FlowInstanceStatus.RUNNING,
          Set.of(
              FlowInstanceStatus.SUSPENDED,
              FlowInstanceStatus.COMPLETED,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR,
              FlowInstanceStatus.ROLLED_BACK),
          FlowInstanceStatus.SUSPENDED,
          Set.of(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ERROR),
          FlowInstanceStatus.ERROR,
          Set.of(
              FlowInstanceStatus.RUNNING,
              FlowInstanceStatus.TERMINATED,
              FlowInstanceStatus.REJECTED,
              FlowInstanceStatus.ROLLED_BACK));

  /** 已完成态可流转目标状态集合（仅支持回滚） */
  private static final Map<FlowInstanceStatus, Set<FlowInstanceStatus>> COMPLETED_TRANSITIONS =
      Map.of(FlowInstanceStatus.COMPLETED, Set.of(FlowInstanceStatus.ROLLED_BACK));

  // ============================== 核心方法 ==============================

  /**
   * 校验状态流转是否合法。
   *
   * <p>与 {@link FlowInstanceStatus#canTransitTo} 语义对齐，额外提供日志记录和详细错误信息。
   *
   * @param current 当前状态（不可为 null）
   * @param target 目标状态（不可为 null）
   * @return true=允许流转；false=非法流转
   * @throws IllegalArgumentException 当 current 或 target 为 null 时
   */
  public boolean validateTransition(FlowInstanceStatus current, FlowInstanceStatus target) {
    if (current == null || target == null) {
      throw new IllegalArgumentException(
          "状态流转校验参数不能为空: current=" + current + ", target=" + target);
    }
    if (current == target) {
      log.debug("[FlowInstanceStateMachine] 状态未变化: {}", current);
      return true;
    }
    boolean allowed = current.canTransitTo(target);
    if (!allowed) {
      log.warn("[FlowInstanceStateMachine] 非法状态流转: {} -> {}", current, target);
    }
    return allowed;
  }

  /**
   * 校验状态流转，非法时抛出异常。
   *
   * <p>适用于业务层需要强制校验状态流转的场景，异常信息包含当前状态和目标状态。
   *
   * @param current 当前状态（不可为 null）
   * @param target 目标状态（不可为 null）
   * @throws BusinessException 当状态流转非法时，异常码为 {@link
   *     WorkflowExceptionCode#ILLEGAL_STATE_TRANSITION}
   * @throws IllegalArgumentException 当 current 或 target 为 null 时
   */
  public void requireTransition(FlowInstanceStatus current, FlowInstanceStatus target) {
    if (current == null || target == null) {
      throw new IllegalArgumentException(
          "状态流转校验参数不能为空: current=" + current + ", target=" + target);
    }
    if (!validateTransition(current, target)) {
      throw BusinessException.builder()
          .resultCode(WorkflowExceptionCode.ILLEGAL_STATE_TRANSITION)
          .params(current.name(), target.name())
          .build();
    }
  }

  /**
   * 获取当前状态可流转的目标状态集合。
   *
   * <p>适用于前端展示可用操作按钮、或业务层需要动态判断可用操作的场景。
   *
   * @param current 当前状态（不可为 null）
   * @return 可流转的目标状态集合（不可变集合，终态返回空集合）
   * @throws IllegalArgumentException 当 current 为 null 时
   */
  public Set<FlowInstanceStatus> getAvailableTransitions(FlowInstanceStatus current) {
    if (current == null) {
      throw new IllegalArgumentException("当前状态不能为空");
    }
    if (current.isTerminal()) {
      return Set.of();
    }
    Set<FlowInstanceStatus> transitions = RUNNING_TRANSITIONS.get(current);
    if (transitions != null) {
      return transitions;
    }
    transitions = COMPLETED_TRANSITIONS.get(current);
    return transitions != null ? transitions : Set.of();
  }

  /**
   * 判断当前状态是否为终态。
   *
   * @param status 状态（不可为 null）
   * @return true=终态；false=非终态
   * @throws IllegalArgumentException 当 status 为 null 时
   */
  public boolean isTerminal(FlowInstanceStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("状态不能为空");
    }
    return status.isTerminal();
  }

  /**
   * 判断当前状态是否为活跃态（流程正在运行中）。
   *
   * @param status 状态（不可为 null）
   * @return true=活跃态；false=非活跃态
   * @throws IllegalArgumentException 当 status 为 null 时
   */
  public boolean isActive(FlowInstanceStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("状态不能为空");
    }
    return status == FlowInstanceStatus.RUNNING
        || status == FlowInstanceStatus.SUSPENDED
        || status == FlowInstanceStatus.ERROR;
  }

  /**
   * 获取所有活跃态状态集合。
   *
   * @return 活跃态状态集合（不可变集合）
   */
  public Set<FlowInstanceStatus> getActiveStatuses() {
    return Set.of(
        FlowInstanceStatus.RUNNING, FlowInstanceStatus.SUSPENDED, FlowInstanceStatus.ERROR);
  }

  /**
   * 获取所有终态状态集合。
   *
   * @return 终态状态集合（不可变集合）
   */
  public Set<FlowInstanceStatus> getTerminalStatuses() {
    return Set.of(
        FlowInstanceStatus.COMPLETED,
        FlowInstanceStatus.TERMINATED,
        FlowInstanceStatus.REJECTED,
        FlowInstanceStatus.ROLLED_BACK);
  }

  /**
   * 判断当前状态是否为运行中。
   *
   * @param status 状态（不可为 null）
   * @return true=运行中；false=非运行中
   * @throws IllegalArgumentException 当 status 为 null 时
   */
  public boolean isRunning(FlowInstanceStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("状态不能为空");
    }
    return status == FlowInstanceStatus.RUNNING;
  }

  /**
   * 获取状态流转的描述信息。
   *
   * <p>用于日志记录、审计追踪或前端展示。
   *
   * @param current 当前状态
   * @param target 目标状态
   * @return 状态流转描述（如 "RUNNING → COMPLETED: 流程完成"）
   */
  public String getTransitionDescription(FlowInstanceStatus current, FlowInstanceStatus target) {
    if (current == null || target == null) {
      return MessageUtils.getMessage("workflow.instance.transition.unknown", "未知状态流转");
    }
    if (current == target) {
      return MessageUtils.getMessage(
          "workflow.instance.transition.noChange",
          new Object[] {current.name()},
          current.name() + ": 状态未变化");
    }
    return switch (target) {
      case RUNNING -> MessageUtils.getMessage(
          "workflow.instance.transition.RUNNING",
          new Object[] {current},
          current + " → RUNNING: 流程激活/恢复");
      case SUSPENDED -> MessageUtils.getMessage(
          "workflow.instance.transition.SUSPENDED",
          new Object[] {current},
          current + " → SUSPENDED: 流程挂起");
      case COMPLETED -> MessageUtils.getMessage(
          "workflow.instance.transition.COMPLETED",
          new Object[] {current},
          current + " → COMPLETED: 流程完成");
      case TERMINATED -> MessageUtils.getMessage(
          "workflow.instance.transition.TERMINATED",
          new Object[] {current},
          current + " → TERMINATED: 流程终止");
      case REJECTED -> MessageUtils.getMessage(
          "workflow.instance.transition.REJECTED",
          new Object[] {current},
          current + " → REJECTED: 流程驳回");
      case ERROR -> MessageUtils.getMessage(
          "workflow.instance.transition.ERROR",
          new Object[] {current},
          current + " → ERROR: 流程异常");
      case ROLLED_BACK -> MessageUtils.getMessage(
          "workflow.instance.transition.ROLLED_BACK",
          new Object[] {current},
          current + " → ROLLED_BACK: 流程回滚");
    };
  }

  /**
   * 获取所有状态枚举值。
   *
   * @return 所有状态枚举值列表
   */
  public List<FlowInstanceStatus> getAllStates() {
    return List.of(FlowInstanceStatus.values());
  }
}
