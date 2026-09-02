package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 流程回放步骤视图对象。
 *
 * <p>用于流程回放功能，记录每一步的执行状态、处理人、耗时等信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowReplayStepVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 步骤序号 */
  private int stepIndex;

  /** 步骤类型：HIS_TASK / AUDIT_LOG / CURRENT_TASK / START / END */
  private String type;

  /** 步骤发生时间 */
  private LocalDateTime timestamp;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 执行人标识 */
  private String actor;

  /** 执行人姓名 */
  private String actorName;

  /** 操作动作 */
  private String action;

  /** 审批意见 */
  private String comment;

  /** 节点状态：ENTERED / PASSED / REJECTED / ACTIVE / SKIPPED / FINISHED */
  private String nodeState;

  /** 该步骤耗时（毫秒） */
  private Long durationMs;

  /** 节点坐标信息（含 x、y 等设计器定位数据） */
  private Map<String, Object> coordinate;
}
