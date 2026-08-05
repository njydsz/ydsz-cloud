package com.remisoft.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

/**
 * 流程实例实体
 *
 * <p>对应数据库表 {@code remi_flow_instance}，每次启动流程生成一条记录。
 * 流程实例是工作流引擎的核心实体，记录一次完整流程审批的全部上下文。
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code flowCode} / {@code definitionId} / {@code flowVersion}：流程标识三元组，唯一确定一份流程定义</li>
 *   <li>{@code businessType} / {@code businessId} / {@code businessNo}：业务单据关联，承载「业务侧 - 流程侧」双向跳转</li>
 *   <li>{@code flowStatus}：实例状态（{@link com.remisoft.workflow.domain.enums.FlowInstanceStatus}）</li>
 *   <li>{@code activityStatus}：激活状态（0=挂起 / 1=激活），与 flowStatus 解耦</li>
 *   <li>{@code currentNodeCode/Name}：当前所在节点（流程图高亮）</li>
 *   <li>{@code variable}：流程变量 JSON，存储动态参数</li>
 * </ul>
 *
 * <p><b>字段冗余说明：</b>
 * <ul>
 *   <li>{@code flowName}、{@code initiatorName}、{@code currentNodeName} 均为冗余字段，
 *       避免 JOIN 查询，提高审批中心列表渲染性能</li>
 *   <li>流程定义变更（重命名）后，冗余字段不会自动更新，但不影响流程执行</li>
 * </ul>
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>唯一索引 {@code uk_business_type_id}（{@code business_type}, {@code business_id}）：保证业务单据唯一关联一个流程实例</li>
 *   <li>普通索引 {@code idx_initiator}（{@code initiator_id}）：加速「我发起的」查询</li>
 *   <li>普通索引 {@code idx_flow_status}（{@code flow_status}）：加速状态筛选</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.workflow.domain.enums.FlowInstanceStatus 实例状态枚举
 * @see com.remisoft.workflow.domain.entity.FlowHisInstance 历史实例实体
 * @see com.remisoft.workflow.server.facade.RemiWorkflowFacade 流程引擎门面
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_flow_instance")
public class FlowInstance extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程编码（业务侧使用，如 {@code "project_initiation"}） */
    private String flowCode;

    /** 流程名称（冗余，避免 JOIN 流程定义） */
    private String flowName;

    /** 流程定义 ID（关联 {@code remi_flow_definition.id}） */
    private String definitionId;

    /** 流程版本（关联 {@code remi_flow_definition.version}） */
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

    /** 发起人 ID（关联 {@code remi_user_account.id}） */
    private String initiatorId;

    /** 发起人姓名（冗余） */
    private String initiatorName;

    /** 当前节点编码（流程图高亮 + 进度提示） */
    private String currentNodeCode;

    /** 当前节点名称（冗余） */
    private String currentNodeName;

    /** 流程变量 JSON（动态参数） */
    private String variable;

    /** 实例状态（{@link com.remisoft.workflow.domain.enums.FlowInstanceStatus}.name） */
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
}
