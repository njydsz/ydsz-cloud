package com.njydsz.workflow.domain.statemachine;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.message.MessageUtils;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.enums.WorkflowExceptionCode;

/**
 * 任务状态机（Domain 层）。
 *
 * <p>封装 {@link FlowTaskStatus} 的状态流转规则，提供统一的状态变更入口和校验能力。
 * 所有任务状态变更必须通过本状态机进行，禁止直接设置 {@code taskStatus} 字段。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>单一职责：只负责状态流转校验，不涉及业务逻辑
 *   <li>不可变：状态机无状态，可安全注入为 Spring 单例
 *   <li>早失败：状态流转非法时立即抛出异常，避免脏数据
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
 * Set<FlowTaskStatus> available = stateMachine.getAvailableTransitions(currentStatus);
 * }</pre>
 *
 * <p><b>状态流转规则：</b>
 *
 * <ul>
 *   <li>PENDING → CLAIMED / COMPLETED / REJECTED / SKIPPED / CANCELLED / DELEGATED / FROZEN /
 *       SUSPENDED / DRAFT
 *   <li>CLAIMED → COMPLETED / REJECTED / DELEGATED / FROZEN / SUSPENDED / DRAFT
 *   <li>DRAFT → PENDING / CLAIMED（草稿提交后回到可处理状态）
 *   <li>DELEGATED → PENDING / CLAIMED（被委派人处理完或退回原办理人）
 *   <li>FROZEN / SUSPENDED → PENDING（激活恢复）
 *   <li>COMPLETED / REJECTED / SKIPPED / CANCELLED / TIMEOUT 为终态，不可再流转
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskStatus 任务状态枚举
 */
@Slf4j
public class FlowTaskStateMachine {

  // ============================== 状态流转规则表 ==============================

  /** 活跃态可流转目标状态集合 */
  private static final Map<FlowTaskStatus, Set<FlowTaskStatus>> ACTIVE_TRANSITIONS = Map.of(
      FlowTaskStatus.PENDING,
      Set.of(
          FlowTaskStatus.CLAIMED,
          FlowTaskStatus.COMPLETED,
          FlowTaskStatus.REJECTED,
          FlowTaskStatus.SKIPPED,
          FlowTaskStatus.CANCELLED,
          FlowTaskStatus.DELEGATED,
          FlowTaskStatus.FROZEN,
          FlowTaskStatus.SUSPENDED,
          FlowTaskStatus.DRAFT),
      FlowTaskStatus.CLAIMED,
      Set.of(
          FlowTaskStatus.COMPLETED,
          FlowTaskStatus.REJECTED,
          FlowTaskStatus.DELEGATED,
          FlowTaskStatus.FROZEN,
          FlowTaskStatus.SUSPENDED,
          FlowTaskStatus.DRAFT),
      FlowTaskStatus.DRAFT,
      Set.of(FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED),
      FlowTaskStatus.DELEGATED,
      Set.of(FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED));

  /** 暂停态可流转目标状态集合 */
  private static final Map<FlowTaskStatus, Set<FlowTaskStatus>> PAUSED_TRANSITIONS = Map.of(
      FlowTaskStatus.FROZEN,
      Set.of(FlowTaskStatus.PENDING),
      FlowTaskStatus.SUSPENDED,
      Set.of(FlowTaskStatus.PENDING));

  // ============================== 核心方法 ==============================

  /**
   * 校验状态流转是否合法。
   *
   * <p>与 {@link FlowTaskStatus#canTransitTo} 语义对齐，额外提供日志记录和详细错误信息。
   *
   * @param current 当前状态（不可为 null）
   * @param target 目标状态（不可为 null）
   * @return true=允许流转；false=非法流转
   * @throws IllegalArgumentException 当 current 或 target 为 null 时
   */
  public boolean validateTransition(FlowTaskStatus current, FlowTaskStatus target) {
    if (current == null || target == null) {
      throw new IllegalArgumentException(
          "状态流转校验参数不能为空: current=" + current + ", target=" + target);
    }
    if (current == target) {
      log.debug("[FlowTaskStateMachine] 状态未变化: {}", current);
      return true;
    }
    boolean allowed = current.canTransitTo(target);
    if (!allowed) {
      log.warn("[FlowTaskStateMachine] 非法状态流转: {} -> {}", current, target);
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
  public void requireTransition(FlowTaskStatus current, FlowTaskStatus target) {
    if (current == null || target == null) {
      throw new IllegalArgumentException(
          "状态流转校验参数不能为空: current=" + current + ", target=" + target);
    }
    if (!validateTransition(current, target)) {
      throw BusinessException.of(
          WorkflowExceptionCode.ILLEGAL_STATE_TRANSITION, current.name(), target.name());
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
  public Set<FlowTaskStatus> getAvailableTransitions(FlowTaskStatus current) {
    if (current == null) {
      throw new IllegalArgumentException("当前状态不能为空");
    }
    if (current.isTerminal()) {
      return Set.of();
    }
    Set<FlowTaskStatus> transitions = ACTIVE_TRANSITIONS.get(current);
    if (transitions != null) {
      return transitions;
    }
    transitions = PAUSED_TRANSITIONS.get(current);
    return transitions != null ? transitions : Set.of();
  }

  /**
   * 判断当前状态是否为终态。
   *
   * @param status 状态（不可为 null）
   * @return true=终态；false=非终态
   * @throws IllegalArgumentException 当 status 为 null 时
   */
  public boolean isTerminal(FlowTaskStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("状态不能为空");
    }
    return status.isTerminal();
  }

  /**
   * 判断当前状态是否为活跃态（可被处理）。
   *
   * @param status 状态（不可为 null）
   * @return true=活跃态；false=非活跃态
   * @throws IllegalArgumentException 当 status 为 null 时
   */
  public boolean isActive(FlowTaskStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("状态不能为空");
    }
    return status == FlowTaskStatus.PENDING
        || status == FlowTaskStatus.CLAIMED
        || status == FlowTaskStatus.DRAFT;
  }

  /**
   * 获取所有活跃态状态集合。
   *
   * @return 活跃态状态集合（不可变集合）
   */
  public Set<FlowTaskStatus> getActiveStatuses() {
    return Set.of(FlowTaskStatus.PENDING, FlowTaskStatus.CLAIMED, FlowTaskStatus.DRAFT);
  }

  /**
   * 获取所有终态状态集合。
   *
   * @return 终态状态集合（不可变集合）
   */
  public Set<FlowTaskStatus> getTerminalStatuses() {
    return Set.of(
        FlowTaskStatus.COMPLETED,
        FlowTaskStatus.REJECTED,
        FlowTaskStatus.SKIPPED,
        FlowTaskStatus.CANCELLED,
        FlowTaskStatus.TIMEOUT);
  }

  /**
   * 获取所有暂停态状态集合。
   *
   * @return 暂停态状态集合（不可变集合）
   */
  public Set<FlowTaskStatus> getPausedStatuses() {
    return Set.of(FlowTaskStatus.FROZEN, FlowTaskStatus.SUSPENDED);
  }

  /**
   * 判断状态流转是否需要签收中间态。
   *
   * <p>某些状态流转需要先经过 CLAIMED 状态（如多人任务场景），本方法用于判断是否需要签收。
   *
   * @param current 当前状态
   * @param target 目标状态
   * @return true=需要签收；false=不需要签收
   */
  public boolean requiresClaim(FlowTaskStatus current, FlowTaskStatus target) {
    if (current == null || target == null) {
      return false;
    }
    // PENDING → COMPLETED/REJECTED 在多人任务场景下需要先签收
    return current == FlowTaskStatus.PENDING
        && (target == FlowTaskStatus.COMPLETED || target == FlowTaskStatus.REJECTED);
  }

  /**
   * 获取状态流转的描述信息。
   *
   * <p>用于日志记录、审计追踪或前端展示。
   *
   * @param current 当前状态
   * @param target 目标状态
   * @return 状态流转描述（如 "PENDING → COMPLETED: 任务通过"）
   */
  public String getTransitionDescription(FlowTaskStatus current, FlowTaskStatus target) {
    if (current == null || target == null) {
      return MessageUtils.getMessage("workflow.transition.unknown", "未知状态流转");
    }
    if (current == target) {
      return MessageUtils.getMessage("workflow.transition.noChange", new Object[] {current.name()},
          current.name() + ": 状态未变化");
    }
    return switch (target) {
      case PENDING -> MessageUtils.getMessage("workflow.transition.PENDING", new Object[] {current},
          current + " → PENDING: 任务激活/恢复");
      case CLAIMED -> MessageUtils.getMessage("workflow.transition.CLAIMED", new Object[] {current},
          current + " → CLAIMED: 任务签收");
      case COMPLETED -> MessageUtils.getMessage("workflow.transition.COMPLETED", new Object[] {current},
          current + " → COMPLETED: 任务通过");
      case REJECTED -> MessageUtils.getMessage("workflow.transition.REJECTED", new Object[] {current},
          current + " → REJECTED: 任务驳回");
      case SKIPPED -> MessageUtils.getMessage("workflow.transition.SKIPPED", new Object[] {current},
          current + " → SKIPPED: 任务跳过");
      case CANCELLED -> MessageUtils.getMessage("workflow.transition.CANCELLED", new Object[] {current},
          current + " → CANCELLED: 任务取消");
      case TIMEOUT -> MessageUtils.getMessage("workflow.transition.TIMEOUT", new Object[] {current},
          current + " → TIMEOUT: 任务超时");
      case DELEGATED -> MessageUtils.getMessage("workflow.transition.DELEGATED", new Object[] {current},
          current + " → DELEGATED: 任务委派");
      case FROZEN -> MessageUtils.getMessage("workflow.transition.FROZEN", new Object[] {current},
          current + " → FROZEN: 任务冻结");
      case SUSPENDED -> MessageUtils.getMessage("workflow.transition.SUSPENDED", new Object[] {current},
          current + " → SUSPENDED: 任务挂起");
      case DRAFT -> MessageUtils.getMessage("workflow.transition.DRAFT", new Object[] {current},
          current + " → DRAFT: 任务暂存");
    };
  }

  /**
   * 获取所有状态枚举值。
   *
   * @return 所有状态枚举值列表
   */
  public List<FlowTaskStatus> getAllStates() {
    return List.of(FlowTaskStatus.values());
  }
}
