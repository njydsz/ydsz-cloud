package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 历史任务实体
 *
 * <p>对应数据库表 {@code ydsz_flow_his_task}，对标 Warm-Flow {@code flow_his_task}，
 * 存储已完成的待办任务（含 APPROVED / REJECTED / CANCELED / DELEGATED），是审批历史的查询表。
 *
 * <p><b>归档机制：</b>任务完成（终态）后由 {@code FlowArchiveScheduler} 从 {@link FlowRunTask} 迁移至本表，
 * 避免运行态主表膨胀。本表按月分区（{@code ydsz_flow_his_task_2026m07}），超过保留期后由清理调度器删除。
 *
 * <p><b>设计要点：</b>{@code createdAt} 复用 {@link MpBaseEntity} 字段，但关闭 MyBatis-Plus 自动填充，
 * 归档时由业务代码显式从源 {@code task.createdAt} 复制（保留业务创建时间，非归档时间）。
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：流程审批历史时间线查询</li>
 *   <li>普通索引 {@code idx_assignee}（{@code assignee_id}）：「我审批过的」查询</li>
 *   <li>普通索引 {@code idx_business}（{@code business_type}, {@code business_id}）：业务侧审批历史</li>
 *   <li>普通索引 {@code idx_finish_at}（{@code finish_at}）：性能分析、报表</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowRunTask 运行态任务表
 * @see com.njydsz.workflow.server.scheduler.FlowArchiveScheduler 归档调度器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_his_task")
public class FlowHisTask extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程实例 ID（归档时从 {@code ydsz_flow_run_task.instance_id} 复制） */
    private String instanceId;

    /** 原始任务 ID（指向源 {@code ydsz_flow_run_task.id}，归档后源表清理前可关联） */
    private String taskId;

    /** 流程编码 */
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

    /** 任务标题 */
    private String title;

    /** 办理人类型（{@link com.njydsz.workflow.domain.enums.FlowAssigneeType}.name） */
    private String assigneeType;

    /** 办理人 ID */
    private String assigneeId;

    /** 办理人姓名（冗余） */
    private String assigneeName;

    /** 会签类型（{@link com.njydsz.workflow.domain.enums.FlowPerformType}.name） */
    private String performType;

    /** 会签所需通过人数 */
    private Integer approveCount;

    /** 会签当前已通过人数 */
    private Integer approveFinished;

    /** VOTE 模式通过率阈值（{@code 0~1}，从源 task 复制） */
    private BigDecimal votePassRate;

    /** 任务状态（终态：{@code APPROVED} / {@code REJECTED} / {@code CANCELED} / {@code DELEGATED}） */
    private String taskStatus;

    /** 审批意见（终态时填写） */
    private String comment;

    /** 签收时间 */
    private LocalDateTime claimAt;

    /** 完成时间（终态时刻） */
    private LocalDateTime finishAt;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 链路追踪 ID（保留原始 trace 便于历史回溯） */
    private String providerTraceId;

    /**
     * FOREACH 迭代元素值（从源 task 复制）。
     *
     * <p>循环节点每条独立 task 对应的集合元素，归档后保留以支持审批历史追溯
     * （如「这个审批是谁做的？属于哪一轮迭代？」）。
     * 非 FOREACH 节点的 task 该字段为 {@code null}。
     */
    private String iterVar;
}
