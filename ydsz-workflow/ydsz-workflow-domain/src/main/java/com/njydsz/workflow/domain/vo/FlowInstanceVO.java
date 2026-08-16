package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 流程实例视图对象
 *
 * <p>用于 Controller 层返回流程实例数据，对应实体 {@link com.njydsz.workflow.domain.entity.FlowInstance}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowInstanceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 流程编码 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 流程定义 ID */
    private String definitionId;

    /** 流程版本号 */
    private String flowVersion;

    /** 业务类型 */
    private String businessType;

    /** 业务单据 ID */
    private String businessId;

    /** 业务单据编号 */
    private String businessNo;

    /** 流程标题 */
    private String title;

    /** 发起人 ID */
    private String initiatorId;

    /** 发起人名称（冗余） */
    private String initiatorName;

    /** 当前节点编码 */
    private String currentNodeCode;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 流程变量（JSON） */
    private String variable;

    /** 流程状态 */
    private String flowStatus;

    /** 激活状态（0=挂起 / 1=激活） */
    private Integer activityStatus;

    /** 开始时间 */
    private LocalDateTime startAt;

    /** 结束时间 */
    private LocalDateTime endAt;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 父流程实例 ID（子流程场景） */
    private String parentInstanceId;

    /** 父流程节点编码 */
    private String parentNodeCode;

    /** 外部追踪 ID */
    private String providerTraceId;

    /** 期望完成时间（SLA 超期时间） */
    private LocalDateTime dueAt;

    /** 驳回原因 */
    private String rejectReason;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
