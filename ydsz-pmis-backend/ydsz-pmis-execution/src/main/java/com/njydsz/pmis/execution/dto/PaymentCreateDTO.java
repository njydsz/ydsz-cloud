package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回款录入 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PaymentCreateDTO {

    @NotBlank(message = "回款编号不能为空")
    private String paymentCode;

    private String paymentNo;

    @NotNull(message = "合同 ID 不能为空")
    private Long contractId;

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;

    private String customerName;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    private String currency = "CNY";

    private String paymentMethod = "BANK_TRANSFER";  // BANK_TRANSFER/CHECK/CASH/OTHER

    @NotNull(message = "到账日期不能为空")
    private LocalDate paymentDate;

    private String bankAccount;
    private String ourBankAccount;
    private String bankReference;
    private String remark;

    /** 预分配的发票 ID（可选） */
    private String invoiceAllocation;
    private BigDecimal allocatedAmount;

    private Long recordedBy;
}
