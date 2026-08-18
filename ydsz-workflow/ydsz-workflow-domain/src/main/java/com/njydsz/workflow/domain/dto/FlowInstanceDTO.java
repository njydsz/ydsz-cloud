package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程实例 DTO
 *
 * <p>用于流程实例的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code id} — 主键 ID（更新时必填，由 Snowflake 生成）
 *   <li>{@code flowCode} — 流程编码（业务侧使用，如 {@code "project_initiation"}）
 *   <li>{@code flowName} — 流程名称（冗余，避免 JOIN 流程定义）
 *   <li>{@code definitionId} — 流程定义 ID
 *   <li>{@code flowVersion} — 流程版本号
 *   <li>{@code businessType} / {@code businessId} / {@code businessNo} — 业务单据关联
 *   <li>{@code title} — 流程标题（展示用）
 *   <li>{@code initiatorId} / {@code initiatorName} — 发起人信息
 *   <li>{@code currentNodeCode} / {@code currentNodeName} — 当前所在节点
 *   <li>{@code variable} — 流程变量 JSON（动态参数）
 *   <li>{@code flowStatus} — 实例状态（FlowInstanceStatus.name）
 *   <li>{@code activityStatus} — 激活状态（0=挂起 / 1=激活）
 *   <li>{@code startAt} / {@code endAt} / {@code durationMs} — 时间信息
 *   <li>{@code parentInstanceId} / {@code parentNodeCode} — 子流程关联
 *   <li>{@code providerTraceId} — 链路追踪 ID
 *   <li>{@code dueAt} — 期望完成时间（SLA 超期时间）
 *   <li>{@code rejectReason} — 驳回原因
 * </ul>
 *
 * <p>与 {@link com.njydsz.workflow.domain.vo.FlowInstanceVO} 的区别：
 * DTO 面向数据持久化场景，标识领域层的输入契约；
 * VO 面向数据输出场景，承载领域层的输出契约。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowInstanceDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID（更新时必填，由 Snowflake 生成） */
  private String id;

  /** 流程编码（业务侧使用，如 "project_initiation"） */
  private String flowCode;

  /** 流程名称（冗余，避免 JOIN 流程定义） */
  private String flowName;

  /** 流程定义 ID（关联 ydsz_flow_definition.id） */
  private String definitionId;

  /** 流程版本号 */
  private String flowVersion;

  /** 业务类型（如 "PROJECT" / "CONTRACT" / "LEAVE"） */
  private String businessType;

  /** 业务单据 ID（业务侧主键） */
  private String businessId;

  /** 业务单据编号（业务侧编号，可读） */
  private String businessNo;

  /** 流程标题（展示用） */
  private String title;

  /** 发起人 ID */
  private String initiatorId;

  /** 发起人姓名（冗余） */
  private String initiatorName;

  /** 当前节点编码 */
  private String currentNodeCode;

  /** 当前节点名称（冗余） */
  private String currentNodeName;

  /** 流程变量 JSON（动态参数） */
  private String variable;

  /** 实例状态（FlowInstanceStatus.name） */
  private String flowStatus;

  /** 激活状态（0=挂起 / 1=激活） */
  private Integer activityStatus;

  /** 启动时间 */
  private LocalDateTime startAt;

  /** 结束时间 */
  private LocalDateTime endAt;

  /** 流程耗时（毫秒） */
  private Long durationMs;

  /** 父流程实例 ID（子流程场景，可空） */
  private String parentInstanceId;

  /** 父流程中触发子流程的节点编码（可空） */
  private String parentNodeCode;

  /** 链路追踪 ID（关联 MDC traceId） */
  private String providerTraceId;

  /** 期望完成时间（SLA 超期时间） */
  private LocalDateTime dueAt;

  /** 驳回原因 */
  private String rejectReason;
}
