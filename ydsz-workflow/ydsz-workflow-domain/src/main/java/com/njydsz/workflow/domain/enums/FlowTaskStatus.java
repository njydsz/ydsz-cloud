package com.njydsz.workflow.domain.enums;

import com.njydsz.workflow.domain.entity.FlowRunTask;


/**
 * 工作流任务状态枚举
 *
 * <p>表示流程任务（{@code ydsz_flow_run_task}）在执行全生命周期的状态。 与 {@link FlowInstanceStatus}（实例级状态）共同构成工作流状态体系。
 * 实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验。
 *
 * <p><b>状态分类：</b>
 *
 * <ul>
 *   <li><b>活跃态：</b>{@link #PENDING}、{@link #CLAIMED}、{@link #DRAFT}（任务可被处理）
 *   <li><b>暂停态：</b>{@link #FROZEN}（实例挂起连带）、{@link #SUSPENDED}（任务级独立挂起）
 *   <li><b>终态：</b>{@link #COMPLETED}、{@link #REJECTED}、{@link #SKIPPED}、{@link #CANCELLED}、{@link
 *       #TIMEOUT}
 *   <li><b>流转中：</b>{@link #DELEGATED}（委派中，被委派人处理完后回到原办理人）
 * </ul>
 *
 * <p><b>FROZEN vs SUSPENDED 区别：</b>
 *
 * <ul>
 *   <li>{@code FROZEN}：实例级挂起时由流程引擎连带触发 PENDING/CLAIMED 任务
 *   <li>{@code SUSPENDED}：任务级独立挂起（管理员手动挂起单个任务）
 *   <li>激活后均回到 PENDING 状态
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>审批中心「我的待办」按 PENDING/CLAIMED 过滤
 *   <li>SLA 监控：超时任务自动标记 TIMEOUT
 *   <li>历史归档：定时清理终态任务
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowInstanceStatus 实例级状态
 * @see FlowRunTask 任务实体
 */
public enum FlowTaskStatus implements BaseStatusEnum<FlowTaskStatus> {

  /** 待办：任务已生成，等待审批人处理（多人任务时可能需要先签收 CLAIMED） */
  PENDING,

  /** 已签收：多人任务场景下，审批人主动签收后才可处理 */
  CLAIMED,

  /** 已通过：审批人点击「同意」，任务正常完成 */
  COMPLETED,

  /** 已驳回：审批人点击「驳回」且流程最终结束 */
  REJECTED,

  /** 已跳过：会签场景下，因条件分支/规则未轮到该用户 */
  SKIPPED,

  /** 已取消：流程被终止/撤回时连带取消的任务 */
  CANCELLED,

  /** 超时：SLA 超时自动处理（默认配置下自动通过，可配置为自动跳过/转交） */
  TIMEOUT,

  /** 已委派：被委派人处理完后回到原办理人 */
  DELEGATED,

  /** 已冻结：流程实例挂起时连带冻结 PENDING/CLAIMED 任务，激活后回到 PENDING */
  FROZEN,

  /** P2-1: 已挂起（任务级挂起，激活后回到 PENDING；与 FROZEN 区别：FROZEN 由实例级挂起连带触发，SUSPENDED 由任务级独立挂起） */
  SUSPENDED,

  /** 暂存：审批人保存审批意见草稿，不改变任务主状态，可随时提交 */
  DRAFT;

  /**
   * 是否为终态
   *
   * <p>终态任务不再变化，可被历史归档 Job 清理。 FROZEN/SUSPENDED/DRAFT/PENDING/CLAIMED/DELEGATED 不属于终态。
   *
   * @return true-终态（COMPLETED/REJECTED/SKIPPED/CANCELLED/TIMEOUT）
   */
  public boolean isFinished() {
    return this == COMPLETED
        || this == REJECTED
        || this == SKIPPED
        || this == CANCELLED
        || this == TIMEOUT;
  }

  /**
   * {@inheritDoc}
   *
   * <p>与 {@link #isFinished()} 语义对齐。
   *
   * @return true 表示终态（COMPLETED/REJECTED/SKIPPED/CANCELLED/TIMEOUT）；false-非终态
   */
  @Override
  public boolean isTerminal() {
    return isFinished();
  }

  /**
   * {@inheritDoc}
   *
   * <p>流转规则：
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
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(FlowTaskStatus target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case PENDING ->
          target == CLAIMED
              || target == COMPLETED
              || target == REJECTED
              || target == SKIPPED
              || target == CANCELLED
              || target == DELEGATED
              || target == FROZEN
              || target == SUSPENDED
              || target == DRAFT;
      case CLAIMED ->
          target == COMPLETED
              || target == REJECTED
              || target == DELEGATED
              || target == FROZEN
              || target == SUSPENDED
              || target == DRAFT;
      case DRAFT -> target == PENDING || target == CLAIMED;
      case DELEGATED -> target == PENDING || target == CLAIMED;
      case FROZEN, SUSPENDED -> target == PENDING;
      case COMPLETED, REJECTED, SKIPPED, CANCELLED, TIMEOUT -> false;
    };
  }
}
