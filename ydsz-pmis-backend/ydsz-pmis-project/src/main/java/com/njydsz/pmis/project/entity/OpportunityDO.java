package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机主表 DO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_opportunity")
public class OpportunityDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String opportunityCode;
    private String opportunityName;
    private Long customerId;
    private String customerName;
    private Long businessDeptId;
    private Long ownerId;
    private String ownerName;
    private String level;       // A/B/C
    private String source;
    private String industry;
    private BigDecimal estimatedAmount;
    private BigDecimal winRate;
    private LocalDate expectedSignDate;
    private LocalDate expectedStartDate;
    private LocalDate expectedEndDate;
    private String status;      // OpportunityStatus.code
    private String lostReason;
    private String competitor;
    private String remark;
    private String tags;
    private Long tenantId;

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
