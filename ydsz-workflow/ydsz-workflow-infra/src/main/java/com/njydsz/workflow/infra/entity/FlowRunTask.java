package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 待办任务运行态实体
 *
 * <p>对应数据库表 {@code ydsz_flow_run_task}（原 {@code ydsz_flow_task}，2026-07-06 重命名），
 * 存储实例推进过程中产生的待办切片，是「我的待办」核心查询表。
 *
 * <p><b>命名说明：</b>表名采用 {@code run_task} 而非 {@code task}，与 {@link FlowHisTask}（已完成归档）区分 —
 * 本表只承载「正在运行中」的待办实例。任务完成后由归档调度器迁移至 {@code ydsz_flow_his_task}。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code instanceId}：所属流程实例 ID
 *   <li>{@code nodeCode} / {@code nodeType}：节点信息（流程图定位）
 *   <li>{@code assigneeType}：办理人类型（{@code USER} / {@code ROLE} / {@code DEPT} / {@code POST}）
 *   <li>{@code performType}：会签类型（{@code OR} / {@code PARALLEL}）
 *   <li>{@code taskStatus}：任务状态（{@link com.njydsz.workflow.domain.enums.FlowTaskStatus}）
 * </ul>
 *
 * <p><b>会签机制：</b>
 *
 * <ul>
 *   <li>{@code OR}：或签（任一人同意即推进）
 *   <li>{@code PARALLEL}：并行会签（所有人同意才推进）
 *   <li>{@code WEIGHTED}：票签（加权投票，{@code approveWeight / totalWeight ≥ votePassRate} 时推进）
 * </ul>
 *
 * <p><b>SLA 催办（P1-6）：</b>
 *
 * <ul>
 *   <li>{@code urgeCount}：已发送的催办次数（超过配置上限后停止）
 *   <li>{@code slaAction}：最终触发的动作（{@code REMIND} / {@code ESCALATE} / {@code AUTO_PASS} / {@code
 *       AUTO_REJECT}）
 *   <li>{@code slaEscalated}：是否已升级（{@code 0} 否 / {@code 1} 是，避免重复升级）
 * </ul>
 *
 * <p><b>循环节点（GAP-P2-10）：</b>FOREACH 节点为集合中每个元素创建独立 task， {@code iterVar} 存储当前 task 对应的元素值（如
 * userId/deptId），用于区分迭代实例。performType 使用 PARALLEL（全部完成才推进）。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_instance_node_assignee}（{@code instance_id}, {@code node_code}, {@code
 *       assignee_id}, {@code iter_var}）
 *   <li>普通索引 {@code idx_assignee}（{@code assignee_id}）：「我的待办」核心索引
 *   <li>普通索引 {@code idx_business}（{@code business_type}, {@code business_id}）
 *   <li>普通索引 {@code idx_due_at}（{@code due_at}）：SLA 扫描索引
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowHisTask 任务归档表
 * @see com.njydsz.workflow.domain.enums.FlowTaskStatus 任务状态枚举
 * @see com.njydsz.workflow.domain.enums.FlowPerformType 会签类型枚举
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_run_task")
public class FlowRunTask extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程实例 ID（关联 {@code ydsz_flow_instance.id}） */
  private String instanceId;

  /** 流程编码（冗余字段） */
  private String flowCode;

  /** 流程定义 ID */
  private String definitionId;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称（冗余） */
  private String nodeName;

  /** 节点类型（{@link com.njydsz.workflow.domain.enums.FlowNodeType}.code） */
  private Integer nodeType;

  /** 业务类型 */
  private String businessType;

  /** 业务单据 ID */
  private String businessId;

  /** 业务单据编号 */
  private String businessNo;

  /** 流程名称（冗余） */
  private String flowName;

  /** 任务标题（默认为「{流程名}-{节点名}-{业务编号}」） */
  private String title;

  /** 委托人 ID（被委托人完成任务后回填，由委托操作产生） */
  private String assignorId;

  /** 委托人姓名（冗余） */
  private String assignorName;

  /** 办理人类型（{@link com.njydsz.workflow.domain.enums.FlowAssigneeType}.name） */
  private String assigneeType;

  /** 办理人 ID（按 type 解析，{@code USER} 传 userId，{@code ROLE} 传 roleCode） */
  private String assigneeId;

  /** 办理人姓名（冗余） */
  private String assigneeName;

  /** 办理人权限标识（原始 SpEL 表达式，存档便于回溯） */
  private String permissionFlag;

  /** 会签类型（{@link com.njydsz.workflow.domain.enums.FlowPerformType}.name） */
  private String performType;

  /** 会签所需通过人数（{@code PARALLEL} 模式：会签总人数） */
  private Integer approveCount;

  /** 会签当前已通过人数 */
  private Integer approveFinished;

  /** 通过率阈值（{@code 0~1}，默认 {@code 0.5} 表示过半数） */
  private BigDecimal votePassRate;

  /**
   * 当前办理人的权重值。
   *
   * <p>仅当 {@code performType=WEIGHTED} 时有效，从节点 {@code ext.userWeights} 中按 userId 查找，
   * 未配置时默认为 {@code 1}。用于票签场景的加权计票。
   */
  private Integer userWeight;

  /** 累计已通过权重（票签模式：每次通过时累加 {@code userWeight}） */
  private Integer approveWeight;

  /** 节点总权重（票签模式：所有办理人权重之和，用于计算通过率） */
  private Integer totalWeight;

  /** 任务状态（{@link com.njydsz.workflow.domain.enums.FlowTaskStatus}.name） */
  private String taskStatus;

  /** 审批意见 */
  private String comment;

  /** 签收时间（多人会签时记录每个办理人的签收时间） */
  private LocalDateTime claimAt;

  /** 完成时间 */
  private LocalDateTime finishAt;

  /**
   * 生效时间（P2-1 穿越时空/补录审批）。
   *
   * <p>默认为 {@code null}，表示即时生效。当非空时，表示该审批"补录"到指定的过去时间，
   * 流程引擎会将此任务的历史顺序按照 {@code effectiveTime} 重新计算。
   *
   * <p>应用场景：线下已审批完成后在系统中补录、或审批日期有特殊追溯需求。
   */
  private LocalDateTime effectiveTime;

  /** 耗时（毫秒，{@code finishAt - claimAt}） */
  private Long durationMs;

  /** 截止时间（SLA 阈值，由 {@code slaConfig.timeoutMinutes} 计算） */
  private LocalDateTime dueAt;

  /** 任务优先级（{@code 1-100}，默认 {@code 50}），待办默认按 {@code priority DESC, created_at ASC} 排序 */
  private Integer priority;

  /** 已发送的 SLA 催办次数 */
  private Integer urgeCount;

  /** 最近一次催办时间 */
  private LocalDateTime lastUrgedAt;

  /** 最终触发的 SLA 动作（{@code REMIND} / {@code ESCALATE} / {@code AUTO_PASS} / {@code AUTO_REJECT}） */
  private String slaAction;

  /** 是否已升级（{@code 0} 否 / {@code 1} 是，避免重复升级） */
  private Integer slaEscalated;

  /** 乐观锁版本号由 MpBaseEntity 继承，无需在此声明 */

  /**
   * FOREACH 节点当前迭代元素值。
   *
   * <p>循环节点为集合中每个元素创建独立 task，该字段存储当前 task 对应的元素值 （如 userId、deptId 等），用于区分不同迭代实例。 非 FOREACH 节点的 task
   * 该字段为 {@code null}。
   */
  private String iterVar;

  /** 链路追踪 ID */
  private String providerTraceId;
}
