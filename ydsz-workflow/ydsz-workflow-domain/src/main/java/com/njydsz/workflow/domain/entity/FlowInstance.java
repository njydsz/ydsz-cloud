package com.njydsz.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.exception.core.BusinessException;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.WorkflowExceptionCode;
import java.io.Serial;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程实例实体
 *
 * <p>对应数据库表 {@code ydsz_flow_instance}，每次启动流程生成一条记录。 流程实例是工作流引擎的核心实体，记录一次完整流程审批的全部上下文。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code flowCode} / {@code definitionId} / {@code flowVersion}：流程标识三元组，唯一确定一份流程定义
 *   <li>{@code businessType} / {@code businessId} / {@code businessNo}：业务单据关联，承载「业务侧 - 流程侧」双向跳转
 *   <li>{@code flowStatus}：实例状态（{@link com.njydsz.workflow.domain.enums.FlowInstanceStatus}）
 *   <li>{@code activityStatus}：激活状态（0=挂起 / 1=激活），与 flowStatus 解耦
 *   <li>{@code currentNodeCode/Name}：当前所在节点（流程图高亮）
 *   <li>{@code variable}：流程变量 JSON，存储动态参数
 * </ul>
 *
 * <p><b>字段冗余说明：</b>
 *
 * <ul>
 *   <li>{@code flowName}、{@code initiatorName}、{@code currentNodeName} 均为冗余字段， 避免 JOIN
 *       查询，提高审批中心列表渲染性能
 *   <li>流程定义变更（重命名）后，冗余字段不会自动更新，但不影响流程执行
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_business_type_id}（{@code business_type}, {@code
 *       business_id}）：保证业务单据唯一关联一个流程实例
 *   <li>普通索引 {@code idx_initiator}（{@code initiator_id}）：加速「我发起的」查询
 *   <li>普通索引 {@code idx_flow_status}（{@code flow_status}）：加速状态筛选
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.domain.enums.FlowInstanceStatus 实例状态枚举
 * @see com.njydsz.workflow.domain.entity.FlowHisInstance 历史实例实体
 * @see com.njydsz.workflow.server.facade.YdszWorkflowFacade 流程引擎门面
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_instance")
public class FlowInstance extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码（业务侧使用，如 {@code "project_initiation"}） */
  private String flowCode;

  /** 流程名称（冗余，避免 JOIN 流程定义） */
  private String flowName;

  /** 流程定义 ID（关联 {@code ydsz_flow_definition.id}） */
  private String definitionId;

  /** 流程版本（关联 {@code ydsz_flow_definition.version}） */
  @TableField("flow_version")
  private String flowVersion;

  /** 业务类型（如 {@code "PROJECT"} / {@code "CONTRACT"} / {@code "LEAVE"}） */
  private String businessType;

  /** 业务单据 ID（业务侧主键） */
  private String businessId;

  /** 业务单据编号（业务侧编号，可读） */
  private String businessNo;

  /** 流程标题（展示用，默认为「{业务类型}-{业务编号}」） */
  private String title;

  /** 发起人 ID（关联 {@code ydsz_user_account.id}） */
  private String initiatorId;

  /** 发起人姓名（冗余） */
  private String initiatorName;

  /** 当前节点编码（流程图高亮 + 进度提示） */
  private String currentNodeCode;

  /** 当前节点名称（冗余） */
  private String currentNodeName;

  /** 流程变量 JSON（动态参数） */
  private String variable;

  /** 实例状态（{@link com.njydsz.workflow.domain.enums.FlowInstanceStatus}.name） */
  private String flowStatus;

  /** 激活状态：0 挂起 / 1 激活 */
  private Integer activityStatus;

  /** 启动时间 */
  @TableField("start_at")
  private LocalDateTime startAt;

  /** 结束时间（终态实例有值，活跃实例为 null） */
  @TableField("end_at")
  private LocalDateTime endAt;

  /** 流程耗时（毫秒），endAt - startAt，启动时为 null，结束时由引擎填充 */
  @TableField("duration_ms")
  private Long durationMs;

  /** GAP-P1: 父流程实例 ID（子流程场景，可空） */
  private String parentInstanceId;

  /** GAP-P1: 父流程中触发子流程的节点编码（可空） */
  private String parentNodeCode;

  /** 链路追踪 ID（关联 MDC traceId，用于跨服务追踪） */
  private String providerTraceId;

  /** 子流程超时时间（超时自动终止子流程，可空） */
  @TableField("due_at")
  private LocalDateTime dueAt;

  /** 乐观锁版本号由 MpBaseEntity 继承，无需在此声明 */

  /** 退回原因（最近一次 REJECT 操作的备注，重审时清空） */
  private String rejectReason;

  // ============================== 充血模型行为方法 ==============================

  /**
   * 状态流转：从当前状态转换到目标状态
   *
   * <p>内置状态机校验，流转非法时抛出 {@link WorkflowExceptionCode#INSTANCE_STATUS_INVALID}。 调用此方法后应持久化实体（由应用层
   * Service 在事务中完成）。
   *
   * @param target 目标状态，不可为 null
   * @param operatorId 操作人 ID（用于审计）
   * @throws BusinessException 状态流转非法或实例为终态时抛出
   * @see FlowInstanceStatus#canTransitTo
   */
  public void transitTo(FlowInstanceStatus target, String operatorId) {
    FlowInstanceStatus current = FlowInstanceStatus.valueOf(flowStatus);
    if (!current.canTransitTo(target)) {
      throw new BusinessException(
          WorkflowExceptionCode.INSTANCE_STATUS_INVALID,
          "流程实例状态流转非法: " + flowStatus + " -> " + target.name());
    }
    this.flowStatus = target.name();
    if (target.isFinished()) {
      this.endAt = LocalDateTime.now();
      if (this.startAt != null) {
        this.durationMs = Duration.between(this.startAt, this.endAt).toMillis();
      }
    }
  }

  /**
   * 是否为终态实例
   *
   * <p>终态实例不允许再次变更状态（ROLLBACK 回滚场景由专门的 {@link #rollback} 方法处理）。
   *
   * @return true-终态（已完成/已终止/已驳回/已回滚）；false-运行中
   */
  public boolean isFinished() {
    return FlowInstanceStatus.valueOf(flowStatus).isFinished();
  }

  /**
   * 是否可挂起（仅 RUNNING 状态可挂起）
   *
   * @return true-可挂起
   */
  public boolean canSuspend() {
    return FlowInstanceStatus.RUNNING.name().equals(flowStatus);
  }

  /**
   * 是否可恢复（仅 SUSPENDED 状态可恢复）
   *
   * @return true-可恢复
   */
  public boolean canActivate() {
    return FlowInstanceStatus.SUSPENDED.name().equals(flowStatus);
  }

  /**
   * 是否已超期（存在 dueAt 且当前时间在 dueAt 之后）
   *
   * @return true-已超期
   */
  public boolean isOverdue() {
    return dueAt != null && LocalDateTime.now().isAfter(dueAt);
  }

  /**
   * 是否为子流程实例
   *
   * @return true-父流程实例 ID 不为空
   */
  public boolean isSubProcess() {
    return parentInstanceId != null && !parentInstanceId.isBlank();
  }

  /**
   * 标记实例为驳回状态
   *
   * <p>同时记录驳回原因。内部调用 {@link #transitTo} 做状态机校验。
   *
   * @param reason 驳回原因
   * @param operatorId 操作人 ID
   */
  public void reject(String reason, String operatorId) {
    this.rejectReason = reason;
    transitTo(FlowInstanceStatus.REJECTED, operatorId);
  }

  /**
   * 回滚已完成的实例（仅 COMPLETED 状态可回滚）
   *
   * <p>由发起人/管理员触发，流转到 ROLLED_BACK 终态。
   *
   * @param operatorId 操作人 ID
   */
  public void rollback(String operatorId) {
    transitTo(FlowInstanceStatus.ROLLED_BACK, operatorId);
  }
}
