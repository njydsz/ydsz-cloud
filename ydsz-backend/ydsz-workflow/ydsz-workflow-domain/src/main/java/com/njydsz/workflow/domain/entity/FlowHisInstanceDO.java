package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * P2-3 流程实例归档 DO
 *
 * <p>对应 ydsz_flow_his_instance 表，存储已完成且超过 retention 天数的实例冷数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_his_instance")
public class FlowHisInstanceDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private LocalDateTime archivedAt;
    private String tenantId;
    private String providerTraceId;
}
