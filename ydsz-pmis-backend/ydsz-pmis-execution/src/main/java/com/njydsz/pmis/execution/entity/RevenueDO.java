package com.njydsz.pmis.execution.entity;

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
 * 收入确认
 */
@Data
@TableName("pmis_profit_revenue")
public class RevenueDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;
    private Long initiationId;
    private String revenueCode;
    private String recognitionMethod;
    private String period;
    private BigDecimal amount;
    private LocalDate recognitionDate;
    private String milestone;
    private BigDecimal percentComplete;
    private Long invoiceId;
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String description;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
