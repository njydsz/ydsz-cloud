package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 流程实例 DO
 *
 * <p>对标 Warm-Flow flow_instance，每次启动流程生成一条记录。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_instance")
public class FlowInstanceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程编码 */
    private String flowCode;

    /** 流程名称（冗余） */
    private String flowName;

    /** 流程定义 ID */
    private Long definitionId;

    /** 流程版本 */
    @TableField("flow_version")
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
    private Long initiatorId;

    /** 发起人姓名 */
    private String initiatorName;

    /** 当前节点编码 */
    private String currentNodeCode;

    /** 当前节点名称 */
    private String currentNodeName;

    /** 流程变量 JSON */
    private String variable;

    /** 实例状态（FlowInstanceStatus.name） */
    private String flowStatus;

    /** 激活状态：0 挂起 / 1 激活 */
    private Integer activityStatus;

    /** 启动时间 */
    @TableField("start_at")
    private LocalDateTime startAt;

    /** 结束时间 */
    @TableField("end_at")
    private LocalDateTime endAt;

    /** 耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** GAP-P1: 父流程实例 ID（子流程场景，可空） */
    private Long parentInstanceId;

    /** GAP-P1: 父流程中触发子流程的节点编码（可空） */
    private String parentNodeCode;

    /** 租户 ID */
    private Long tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
