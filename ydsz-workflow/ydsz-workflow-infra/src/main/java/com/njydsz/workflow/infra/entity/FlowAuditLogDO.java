package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 流程审计日志实体
 *
 * <p>对应数据库表 {@code ydsz_flow_audit_log}，记录流程全生命周期的操作轨迹： 「谁在何时对哪个实例/任务做了什么操作」。是流程合规审计、问题回溯的核心数据源。
 *
 * <p><b>不可变性约束：</b>本表为<b>只追加</b>（append-only），<b>禁止修改、禁止删除</b>。
 * 任何业务操作（包括撤销、修改、删除业务单据）都不会改写已写入的审计日志。
 *
 * <p><b>操作类型（{@code action}）：</b>
 *
 * <ul>
 *   <li>{@code START}：发起流程
 *   <li>{@code PASS}：同意
 *   <li>{@code REJECT}：拒绝
 *   <li>{@code TRANSFER}：转办
 *   <li>{@code DELEGATE}：委派（被委托人办完后回到委托人）
 *   <li>{@code COUNTERSIGN}：加签/减签
 *   <li>{@code RECALL}：撤销流程（仅发起人未办结前）
 *   <li>{@code URGE}：催办
 *   <li>{@code TERMINATE}：终止流程（管理员）
 *   <li>{@code SUSPEND}：挂起实例
 *   <li>{@code ACTIVATE}：激活实例
 *   <li>{@code CLAIM}：签收（多人会签时认领）
 * </ul>
 *
 * <p><b>审批意见分类（P2-42，{@code commentType}）：</b>
 *
 * <ul>
 *   <li>{@code AGREE}：同意
 *   <li>{@code DISAGREE}：不同意
 *   <li>{@code SUGGEST}：建议（不影响流程推进）
 *   <li>{@code INQUIRE}：询问（@某人需回复）
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：实例审批轨迹时间线
 *   <li>普通索引 {@code idx_business}（{@code business_type}, {@code business_id}）：业务侧审计
 *   <li>普通索引 {@code idx_operator}（{@code operator_id}）：「我操作的」审计
 *   <li>普通索引 {@code idx_operated_at}（{@code operated_at}）：按时间范围查询
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowCommentDO 流程评论（用户视角，可修改可删除）
 * @see com.njydsz.common.audit.OperationLog 通用操作日志（横切关注点）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_audit_log")
public class FlowAuditLogDO extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程实例 ID */
  private String instanceId;

  /** 任务 ID（实例级操作可为空） */
  private String taskId;

  /** 流程编码 */
  private String flowCode;

  /** 业务类型 */
  private String businessType;

  /** 业务单据 ID */
  private String businessId;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称（冗余） */
  private String nodeName;

  /**
   * 操作类型：{@code
   * START/PASS/REJECT/TRANSFER/DELEGATE/COUNTERSIGN/RECALL/URGE/TERMINATE/SUSPEND/ACTIVATE/CLAIM}
   */
  private String action;

  /** 操作人 ID */
  private String operatorId;

  /** 操作人姓名（冗余） */
  private String operatorName;

  /** 目标人 ID（转办/委派/加签/抄送时使用） */
  private String targetId;

  /** 目标人姓名（冗余） */
  private String targetName;

  /** 审批意见 */
  private String comment;

  /** 审批意见分类：{@code AGREE/DISAGREE/SUGGEST/INQUIRE} */
  private String commentType;

  /** 操作时间（精确到毫秒） */
  private LocalDateTime operatedAt;

  /** 链路追踪 ID */
  private String providerTraceId;
}
