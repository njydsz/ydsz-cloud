package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程异常记录视图对象
 *
 * <p>用于展示审批流程中检测到的异常情况，包括卡住（STUCK）、高驳回率（HIGH_REJECTION）、
 * 长时间运行（LONG_RUNNING）等类型，支持按告警等级分类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAnomalyVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 异常类型：STUCK / HIGH_REJECTION / LONG_RUNNING */
  private String type;

  /** 告警等级：RED / YELLOW / ORANGE */
  private String warnLevel;

  /** 异常分类描述 */
  private String anomalyType;

  /** 流程实例 ID */
  private String instanceId;

  /** 任务 ID */
  private String taskId;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 异常描述信息 */
  private String description;

  /** 卡住时长（小时），仅 STUCK 类型有效 */
  private Long stuckHours;

  /** 异常发生时间 */
  private LocalDateTime createdAt;

  /** 驳回率统计：总数量 */
  private Long totalCount;

  /** 驳回率统计：驳回数量 */
  private Long rejectedCount;

  /** 驳回率（0~1），仅 HIGH_REJECTION 类型有效 */
  private Double rejectionRate;
}
