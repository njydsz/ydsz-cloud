package com.njydsz.pmis.execution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目风险登记
 */
@Data
@TableName("pmis_execution_risk")
public class RiskDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String riskCode;
    private Long initiationId;
    private String riskTitle;
    private String riskType;        // SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER
    private String description;
    private String probability;     // LOW/MEDIUM/HIGH
    private String impact;          // LOW/MEDIUM/HIGH
    private String riskLevel;       // 计算后的等级
    private String mitigation;
    private String contingency;
    private Long ownerId;
    private String ownerName;
    private String status;
    private LocalDateTime occurredAt;
    private LocalDateTime closedAt;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
