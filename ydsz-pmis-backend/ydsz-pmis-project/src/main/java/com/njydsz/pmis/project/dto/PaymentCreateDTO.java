package com.njydsz.pmis.project.dto;

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

    @NotBlank(message = "{validation.execution.msg_d55e99b3}")
    private String paymentCode;

    private String paymentNo;

    @NotNull(message = "{validation.execution.msg_af96cf73}")
    private String contractId;

    @NotNull(message = "{validation.execution.msg_576c2b5e}")
    private String initiationId;

    @NotNull(message = "{validation.execution.msg_6de1fd36}")
    private String customerId;

    private String customerName;

    @NotNull(message = "{validation.execution.msg_406c0ea8}")
    private BigDecimal amount;

    private String currency = "CNY";

    private String paymentMethod = "BANK_TRANSFER";  // BANK_TRANSFER/CHECK/CASH/OTHER

    @NotNull(message = "{validation.execution.msg_4fa8fbb5}")
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
