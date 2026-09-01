package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 流程趋势数据视图对象
 *
 * <p>用于按时间维度展示审批流程的数量与耗时变化趋势，适用于折线图等可视化场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowTrendVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 时间标签（如 "2024-01"、"2024-W03"） */
  private String timeLabel;

  /** 该时间段内的任务数量 */
  private long count;

  /** 该时间段内的平均耗时（毫秒） */
  private long avgDurationMs;
}
