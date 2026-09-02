package com.njydsz.cronjob.domain.dag;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * DAG 节点状态枚举
 *
 * <p>实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验 与 {@link #isTerminal()} 终态判定，供
 * DAG 执行引擎与业务层复用。
 *
 * <h3>状态分类</h3>
 *
 * <ul>
 *   <li><b>进行中</b>：PENDING、RUNNING、RETRYING、WAITING_FOR_APPROVAL
 *   <li><b>终态</b>：SUCCESS、FAILED、SKIPPED、TIMEOUT、APPROVAL_REJECTED
 * </ul>
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>{@link #WAITING_FOR_APPROVAL} / {@link #APPROVAL_REJECTED}：审批工作流（{@code DagInstanceControlService}）
 *   <li>{@link #RETRYING}：节点级重试（{@code DagInstanceExecutor} 重试前过渡态）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum DagNodeStatus implements BaseStatusEnum<DagNodeStatus> {

  /** 待执行 */
  PENDING,
  /** 执行中 */
  RUNNING,
  /**
   * 等待审批。
   *
   * <p>用于审批工作流，节点执行前需人工审批通过。
   */
  WAITING_FOR_APPROVAL,
  /**
   * 审批驳回（终态）。
   *
   * <p>审批人驳回后进入此状态，等同于节点执行失败。
   */
  APPROVAL_REJECTED,
  /**
   * 重试中。
   *
   * <p>节点失败后等待 DAG 执行器重新触发的过渡态。
   */
  RETRYING,
  /** 成功 */
  SUCCESS,
  /** 失败 */
  FAILED,
  /** 跳过 */
  SKIPPED,
  /** 超时 */
  TIMEOUT;

  /**
   * 解析字符串为枚举值（大小写不敏感，容忍 null）
   *
   * @param value 状态字符串
   * @return 枚举值，null 时返回 null
   */
  public static DagNodeStatus parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return DagNodeStatus.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * 判断是否为终态（不会再发生变化的状态）
   *
   * @return 终态返回 true
   */
  @Override
  public boolean isTerminal() {
    return this == SUCCESS
        || this == FAILED
        || this == SKIPPED
        || this == TIMEOUT
        || this == APPROVAL_REJECTED;
  }

  /**
   * 校验状态流转是否合法。
   *
   * <p>流转规则：
   *
   * <ul>
   *   <li>PENDING → RUNNING / WAITING_FOR_APPROVAL / SKIPPED / CANCELLED（取消由上层实例兜底）
   *   <li>WAITING_FOR_APPROVAL → APPROVAL_REJECTED / RUNNING（审批通过继续执行）
   *   <li>RUNNING → WAITING_FOR_APPROVAL / RETRYING / SUCCESS / FAILED / TIMEOUT
   *   <li>RETRYING → RUNNING / FAILED / TIMEOUT
   *   <li>SUCCESS / FAILED / SKIPPED / TIMEOUT / APPROVAL_REJECTED 为终态，不可再流转
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(DagNodeStatus target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case PENDING -> target == RUNNING || target == WAITING_FOR_APPROVAL || target == SKIPPED;
      case WAITING_FOR_APPROVAL -> target == APPROVAL_REJECTED || target == RUNNING;
      case RUNNING ->
          target == WAITING_FOR_APPROVAL
              || target == RETRYING
              || target == SUCCESS
              || target == FAILED
              || target == TIMEOUT;
      case RETRYING -> target == RUNNING || target == FAILED || target == TIMEOUT;
      case SUCCESS, FAILED, SKIPPED, TIMEOUT, APPROVAL_REJECTED -> false;
    };
  }
}
