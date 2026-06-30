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
import java.time.LocalDateTime;

/**
 * 客户信用记录
 *
 * <p>按客户维度跟踪：累计合同金额、累计回款、回款及时率、当前等级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_finance_customer_credit")
public class CustomerCreditDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;
    private String customerName;
    private String creditLevel;           // CreditLevel.code (A/B/C/D)
    private Integer creditScore;          // 0-100
    private BigDecimal totalContractAmount;   // 累计合同金额
    private BigDecimal totalInvoicedAmount;   // 累计开票金额
    private BigDecimal totalReceivedAmount;   // 累计回款金额
    private BigDecimal onTimeRate;            // 及时回款率（0-1）
    private Integer contractCount;            // 合作合同数
    private Integer overdueCount;             // 逾期次数
    private LocalDateTime lastEvaluationAt;   // 上次评估时间
    private String evaluator;                 // 评估人
    private String remark;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
