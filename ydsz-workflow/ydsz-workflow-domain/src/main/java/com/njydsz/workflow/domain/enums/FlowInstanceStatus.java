package com.njydsz.workflow.domain.enums;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 流程实例状态枚举
 *
 * <p>用于表示自研工作流 v2 流程实例在执行全生命周期的状态。 与 {@link FlowTaskStatus}（任务级状态）共同构成工作流状态体系。 实现 {@link
 * BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验。
 *
 * <p><b>状态流转：</b>
 *
 * <pre>
 *   DRAFT ──▶ RUNNING ──▶ SUSPENDED ──▶ RUNNING（恢复）
 *      │         │           │           │
 *      │         ├───────────┴───────────┴─▶ COMPLETED（正常完成）
 *      │         ├─▶ TERMINATED（管理员强制）
 *      │         ├─▶ REJECTED（被驳回最终结束）
 *      │         ├─▶ ERROR（异常，需人工介入）
 *      │         └─▶ ROLLED_BACK（已完成的实例被撤销，最终态）
 *      └─▶ TERMINATED（草稿取消）
 * </pre>
 *
 * <p><b>终态判定：</b>{@link #isFinished()} 标识流程已结束， 包括正常完成、强制终止、驳回、已回滚。终态实例不允许再次变更状态（除非回滚撤销）。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>流程监控页面按状态筛选/统计
 *   <li>历史归档 Job 定时清理非 ERROR/RUNNING 状态实例
 *   <li>业务侧监听（如 {@code ProjectInitiationFlowListener}）根据 ROLLED_BACK 状态做补偿
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskStatus 任务级状态枚举
 * @see com.njydsz.workflow.infra.entity.FlowInstance 流程实例实体
 */
public enum FlowInstanceStatus implements BaseStatusEnum<FlowInstanceStatus> {

  /** 运行中：流程已启动，至少有一个任务未完成 */
  RUNNING,

  /** 挂起：流程被管理员或系统暂停，可通过「恢复」重新进入 RUNNING */
  SUSPENDED,

  /** 已完成：所有任务均已通过，流程正常结束 */
  COMPLETED,

  /** 已终止：管理员强制停止流程，任务不再继续 */
  TERMINATED,

  /** 已驳回：被驳回且流程最终结束（区别于「驳回到上一节点」） */
  REJECTED,

  /**
   * P1-4: 异常
   *
   * <p>服务节点执行失败、超时等异常状态，需人工介入处理。 流程引擎会持续重试或等待人工干预。
   */
  ERROR,

  /**
   * P2-3: 已回滚
   *
   * <p>原本已 COMPLETED 的实例被发起人/管理员撤销，最终态。 流程不再运行，但保留全部历史轨迹，供业务侧 （如 {@code
   * ProjectInitiationFlowListener}）感知并执行回滚补偿逻辑。
   */
  ROLLED_BACK,

  /**
   * P0-5: 草稿
   *
   * <p>用户暂存待审状态，已填写表单但未正式提交。 借鉴 Flowlong 的「暂存待审」概念，允许用户保存草稿后修改再提交。
   *
   * <p><b>流转规则：</b>
   *
   * <ul>
   *   <li>DRAFT → RUNNING：用户正式提交审批
   *   <li>DRAFT → TERMINATED：用户取消草稿
   * </ul>
   */
  DRAFT;

  /**
   * 是否为终态
   *
   * <p>终态指流程已结束（无论正常完成、强制终止、驳回还是回滚）。 终态实例不允许再次变更状态（ROLLBACK 除外）。
   *
   * @return true-终态（COMPLETED / TERMINATED / REJECTED / ROLLED_BACK）；false-非终态（RUNNING / SUSPENDED
   *     / ERROR / DRAFT）
   */
  public boolean isFinished() {
    return this == COMPLETED || this == TERMINATED || this == REJECTED || this == ROLLED_BACK;
  }

  /**
   * {@inheritDoc}
   * 
   * <p>与 {@link #isFinished()} 语义对齐：终态包含已回滚（ROLLED_BACK）。
   *
   * @return 返回值说明
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
   *   <li>RUNNING → SUSPENDED / COMPLETED / TERMINATED / REJECTED / ERROR / ROLLED_BACK
   *   <li>SUSPENDED → RUNNING（恢复）/ TERMINATED / REJECTED / ERROR
   *   <li>ERROR → RUNNING（重试）/ TERMINATED / REJECTED / ROLLED_BACK
   *   <li>COMPLETED → ROLLED_BACK（撤销已完成的实例）
   *   <li>TERMINATED / REJECTED / ROLLED_BACK 为终态，不可再流转
   * </ul>
   *
   * @param target 目标状态
   * @return true 表示允许流转
   */
  @Override
  public boolean canTransitTo(FlowInstanceStatus target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case RUNNING ->
          target == SUSPENDED
              || target == COMPLETED
              || target == TERMINATED
              || target == REJECTED
              || target == ERROR
              || target == ROLLED_BACK;
      case SUSPENDED ->
          target == RUNNING || target == TERMINATED || target == REJECTED || target == ERROR;
      case ERROR ->
          target == RUNNING || target == TERMINATED || target == REJECTED || target == ROLLED_BACK;
      case COMPLETED -> target == ROLLED_BACK;
      case TERMINATED, REJECTED, ROLLED_BACK -> false;
      case DRAFT -> target == RUNNING || target == TERMINATED;
    };
  }
}
