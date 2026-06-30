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
 * 回款记录
 *
 * <p>支持按客户回款、按合同核销；可与一张或多张发票自动匹配核销。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_finance_payment")
public class PaymentDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;             // 流水号
    private String paymentCode;           // 业务编号
    private Long contractId;
    private Long initiationId;
    private Long customerId;
    private String customerName;
    private BigDecimal amount;            // 回款金额
    private String currency;
    private String paymentMethod;         // BANK_TRANSFER/CHECK/CASH/OTHER
    private LocalDate paymentDate;        // 到账日期
    private String bankAccount;           // 客户付款账户
    private String ourBankAccount;        // 我方收款账户
    private String bankReference;         // 银行流水号
    private String invoiceAllocation;     // 已分配发票ID列表（JSON/逗号分隔）
    private BigDecimal allocatedAmount;   // 已核销金额
    private BigDecimal unallocatedAmount; // 未核销金额
    private String status;                // PaymentStatus.code
    private String remark;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long recordedBy;              // 录入人
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
