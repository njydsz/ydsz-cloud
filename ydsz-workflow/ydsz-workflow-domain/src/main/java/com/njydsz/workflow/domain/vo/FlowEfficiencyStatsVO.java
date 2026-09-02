package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 流程效率统计视图对象
 *
 * <p>用于展示审批流程的整体效率指标，包括任务总数、平均耗时、代理率及逾期率。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowEfficiencyStatsVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务总数 */
  private long totalCount;

  /** 平均耗时（毫秒） */
  private long avgDurationMs;

  /** 代理率（0~1） */
  private double proxyRate;

  /** 逾期率（0~1） */
  private double overdueRate;
}
