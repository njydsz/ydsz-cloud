package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 瓶颈节点视图对象
 *
 * <p>用于标识审批流程中耗时较长、处理数量较多的瓶颈节点，帮助定位流程优化点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowBottleneckVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 平均耗时（毫秒） */
  private long avgDurationMs;

  /** 处理数量 */
  private long count;
}
