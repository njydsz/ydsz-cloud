package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * AI 流程瓶颈分析视图对象。
 *
 * <p>用于返回流程实例的瓶颈分析结果，包含拥堵节点、平均耗时和优化建议。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAiBottleneckAnalysisVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码 */
  private String flowCode;

  /** 拥堵节点列表 */
  private List<BottleneckNodeVO> bottlenecks;

  /** 平均耗时（毫秒） */
  private long avgDurationMs;

  /** 优化建议列表 */
  private List<String> suggestions;

  /** 是否由 AI 真实分析（false 表示降级结果） */
  private boolean aiAnalyzed;

  /** 降级原因（aiAnalyzed=false 时） */
  private String fallbackReason;

  /**
   * 拥堵节点视图对象。
   */
  @Data
  public static class BottleneckNodeVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 平均停留耗时（毫秒） */
    private long avgStayMs;

    /** 当前停留实例数 */
    private int stuckCount;

    /** 建议优化动作 */
    private String suggestion;
  }
}
