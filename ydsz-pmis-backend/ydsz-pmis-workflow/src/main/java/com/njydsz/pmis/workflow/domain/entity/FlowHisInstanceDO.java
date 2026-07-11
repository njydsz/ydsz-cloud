package com.njydsz.pmis.workflow.domain.entity.instance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * P2-3 流程实例归档 DO
 *
 * <p>对应 pmis_flow_his_instance 表，存储已完成且超过 retention 天数的实例冷数据。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_flow_his_instance")
public class FlowHisInstanceDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String flowCode;
    private String flowName;
    private String definitionId;
    private String flowVersion;
    private String businessType;
    private String businessId;
    private String businessNo;
    private String title;
    private String initiatorId;
    private String initiatorName;
    private String currentNodeCode;
    private String currentNodeName;
    private String variable;
    private String flowStatus;
    private Integer activityStatus;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long durationMs;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
    private String tenantId;
    private String providerTraceId;
}
