package com.njydsz.pmis.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票创建 DTO
 *
 * <p>支持正常开票与红冲（invoiceType=RED_REVERSE 时须传 reversedById）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class InvoiceCreateDTO {

    private String invoiceNo;

    @NotBlank(message = "{validation.execution.msg_ffebd629}")
    private String invoiceCode;

    @NotBlank(message = "{validation.execution.msg_f063c858}")
    private String invoiceType;          // NORMAL/RED_REVERSE

    @NotNull(message = "{validation.execution.msg_af96cf73}")
    private String contractId;

    @NotNull(message = "{validation.execution.msg_576c2b5e}")
    private String initiationId;

    @NotNull(message = "{validation.execution.msg_6de1fd36}")
    private String customerId;

    private String customerName;

    @NotBlank(message = "{validation.execution.msg_b0f8bcc9}")
    private String invoiceBasis;         // MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER

    @NotNull(message = "{validation.execution.msg_406c0ea8}")
    private BigDecimal amount;

    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private String currency = "CNY";

    private LocalDate invoiceDate;
    private String taxPeriod;
    private String title;
    private String taxNo;
    private String bankInfo;
    private String address;
    private String phone;
    private String remark;

    /** 红冲时：被红冲的发票 ID */
    private String reversedById;

    /** 外包开票时：客户确认人天单附件 ID */
    private String outsourcingProofId;

    /** 里程碑/终验开票时：验收报告附件 ID */
    private String acceptanceProofId;

    private String attachmentId;
    private Long appliedBy;
}
