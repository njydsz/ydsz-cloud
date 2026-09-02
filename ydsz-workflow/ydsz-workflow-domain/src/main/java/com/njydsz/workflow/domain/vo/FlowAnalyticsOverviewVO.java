package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 审批总览统计视图对象
 *
 * <p>用于展示审批流程的整体统计数据，包括任务总数、完成数、驳回数、待办数、
 * 平均耗时、驳回率及逾期数量等核心指标。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowAnalyticsOverviewVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务总数 */
  private long totalTasks;

  /** 已完成任务数 */
  private long completedTasks;

  /** 已驳回任务数 */
  private long rejectedTasks;

  /** 待处理任务数 */
  private long pendingTasks;

  /** 平均耗时（毫秒） */
  private long avgDurationMs;

  /** 驳回率（0~1） */
  private double rejectionRate;

  /** 逾期数量 */
  private long overdueCount;
}
