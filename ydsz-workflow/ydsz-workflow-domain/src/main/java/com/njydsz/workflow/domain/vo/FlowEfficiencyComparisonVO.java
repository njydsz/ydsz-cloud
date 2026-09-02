package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 流程效率对比视图对象
 *
 * <p>用于不同流程定义之间的效率横向对比，包含各流程的完成数、平均耗时、驳回率及逾期率。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowEfficiencyComparisonVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码 */
  private String flowCode;

  /** 流程名称 */
  private String flowName;

  /** 任务总数 */
  private long totalCount;

  /** 已完成数量 */
  private long completedCount;

  /** 平均耗时（毫秒） */
  private long avgDurationMs;

  /** 驳回率（0~1） */
  private double rejectionRate;

  /** 逾期率（0~1） */
  private double overdueRate;
}
