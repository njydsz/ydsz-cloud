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
 * 费用报销
 */
@Data
@TableName("pmis_cost_expense")
public class ExpenseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String expenseCode;
    private Long initiationId;
    private Long employeeId;
    private String employeeName;
    private String expenseType;     // TRAVEL/CATERING/...
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String description;
    private String receiptUrl;
    private String status;
    private Long approverId;
    private String approverName;
    private LocalDateTime approvedAt;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
