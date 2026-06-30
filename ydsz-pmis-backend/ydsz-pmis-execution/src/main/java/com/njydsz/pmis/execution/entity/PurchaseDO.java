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
 * 采购成本
 */
@Data
@TableName("pmis_cost_purchase")
public class PurchaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String purchaseCode;
    private Long initiationId;
    private String vendor;
    private String itemName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private LocalDate purchaseDate;
    private String status;
    private Long applicantId;
    private String applicantName;
    private Long approverId;
    private String approverName;
    private LocalDateTime approvedAt;
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
