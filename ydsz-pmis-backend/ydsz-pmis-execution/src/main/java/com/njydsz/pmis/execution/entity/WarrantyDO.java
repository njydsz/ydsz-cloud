package com.njydsz.pmis.execution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质保期实体
 *
 * <p>项目结项后自动创建，到期前 N 天提醒，到期后自动 EXPIRED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_warranty")
public class WarrantyDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码（WY-YYYYMMDD-XXXX） */
    private String warrantyCode;
    private Long initiationId;
    private Long contractId;
    private String projectType;
    private String projectLevel;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationMonths;
    private Integer noticeDays;
    private Boolean noticeSent;
    private LocalDateTime noticeSentAt;
    /** WarrantyStatus.code */
    private String status;
    private LocalDateTime terminatedAt;
    private String terminatedReason;
    private String contactName;
    private String contactPhone;
    private String remark;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
