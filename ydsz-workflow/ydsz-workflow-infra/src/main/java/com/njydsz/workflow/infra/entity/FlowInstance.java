package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.WorkflowExceptionCode;
import com.njydsz.workflow.domain.event.FlowDomainEvent;
import com.njydsz.workflow.domain.event.FlowInstanceResumedEvent;
import com.njydsz.workflow.domain.event.FlowInstanceRolledBackEvent;
import com.njydsz.workflow.domain.event.FlowInstanceSuspendedEvent;
import com.njydsz.workflow.domain.statemachine.FlowInstanceStateMachine;

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
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.enums.FlowInstanceStatus 实例状态枚举
 * @see com.njydsz.workflow.infra.entity.FlowHisInstance 历史实例实体
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

  /** 发起人 ID（关联 {@code ydsz_acct_user.id}） */
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

  // ============================== 充血模型：领域事件收集器 ==============================

  /**
   * 领域事件临时收集器（transient，不持久化）。
   *
   * <p>状态变更时记录的领域事件，由应用层 Service 在事务提交后通过 {@link
   * com.njydsz.workflow.domain.event.DomainEventPublisher} 发布。
   * 设计参考：DDD 聚合根的事件收集-发布模式（避免在聚合内直接依赖事件发布器）。
   */
  private final transient List<FlowDomainEvent> domainEvents = new ArrayList<>();

  /** 状态机实例（无状态单例，延迟初始化） */
  private static volatile FlowInstanceStateMachine stateMachine;

  // ============================== 充血模型行为方法 ==============================

  /**
   * 状态流转：从当前状态转换到目标状态（使用 {@link FlowInstanceStateMachine} 校验）。
   *
   * <p>内置状态机校验，流转非法时抛出 {@link WorkflowExceptionCode#INSTANCE_STATUS_INVALID}。
   * 流转成功后自动记录领域事件（通过 {@link #domainEvents} 收集），由应用层 Service 发布。
   * 调用此方法后应持久化实体（由应用层 Service 在事务中完成）。
   *
   * @param target 目标状态，不可为 null
   * @param operatorId 操作人 ID（用于审计）
   * @throws BusinessException 状态流转非法或实例为终态时抛出
   * @see FlowInstanceStateMachine#requireTransition
   */
  public void transitTo(FlowInstanceStatus target, String operatorId) {
    FlowInstanceStatus current = FlowInstanceStatus.valueOf(flowStatus);
    getStateMachine().requireTransition(current, target);
    this.flowStatus = target.name();
    if (target.isFinished()) {
      this.endAt = LocalDateTime.now();
      if (this.startAt != null) {
        this.durationMs = Duration.between(this.startAt, this.endAt).toMillis();
      }
    }
    // 记录领域事件
    recordEventForTransition(current, target, operatorId);
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

  /**
   * 获取并清空已收集的领域事件。
   *
   * <p>由应用层 Service 在事务提交后调用，将事件发布到 {@link
   * com.njydsz.workflow.domain.event.DomainEventPublisher}。
   * 调用后本实例的事件列表将被清空，避免重复发布。
   *
   * @return 已收集的领域事件列表（可能为空）
   */
  public List<FlowDomainEvent> popDomainEvents() {
    List<FlowDomainEvent> events = new ArrayList<>(domainEvents);
    domainEvents.clear();
    return events;
  }

  // ============================== 私有方法 ==============================

  /**
   * 获取状态机实例（双重检查锁单例）。
   *
   * <p>状态机本身无状态，可安全共享。使用 volatile + DCL 保证线程安全且避免同步开销。
   *
   * @return 状态机单例
   */
  private static FlowInstanceStateMachine getStateMachine() {
    if (stateMachine == null) {
      synchronized (FlowInstance.class) {
        if (stateMachine == null) {
          stateMachine = new FlowInstanceStateMachine();
        }
      }
    }
    return stateMachine;
  }

  /**
   * 根据状态流转类型记录对应的领域事件。
   *
   * @param current 当前状态
   * @param target 目标状态
   * @param operatorId 操作人 ID
   */
  private void recordEventForTransition(
      FlowInstanceStatus current, FlowInstanceStatus target, String operatorId) {
    switch (target) {
      case SUSPENDED -> domainEvents.add(
          new FlowInstanceSuspendedEvent(
              this, instanceId(), flowCode, flowName, businessType, businessId, initiatorId, null));
      case RUNNING -> {
        if (current == FlowInstanceStatus.SUSPENDED) {
          domainEvents.add(
              new FlowInstanceResumedEvent(
                  this, instanceId(), flowCode, flowName, businessType, businessId, initiatorId));
        }
      }
      case ROLLED_BACK -> domainEvents.add(
          new FlowInstanceRolledBackEvent(
              this, instanceId(), flowCode, flowName, businessType, businessId, initiatorId,
              rejectReason));
      default -> {
        // 其他流转（COMPLETED/TERMINATED/REJECTED/ERROR）暂不记录额外事件
        // 实例完成/终止/驳回事件由 FlowInstanceServiceImpl 在事务边界发布
      }
    }
  }

  /**
   * 获取实例 ID（兼容 id 字段可能为 null 的场景）。
   *
   * @return 实例 ID 字符串
   */
  private String instanceId() {
    return this.getId();
  }
}
