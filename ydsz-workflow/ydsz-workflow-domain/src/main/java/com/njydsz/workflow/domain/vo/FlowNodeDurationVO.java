package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 节点耗时统计视图对象
 *
 * <p>用于展示流程中各节点的耗时分布情况，包括平均耗时、最大耗时、P50/P90 分位数及处理数量。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowNodeDurationVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 平均耗时（毫秒） */
  private long avgDurationMs;

  /** 最大耗时（毫秒） */
  private long maxDurationMs;

  /** P50 分位耗时（毫秒） */
  private long p50DurationMs;

  /** P90 分位耗时（毫秒） */
  private long p90DurationMs;

  /** 处理数量 */
  private long count;
}
