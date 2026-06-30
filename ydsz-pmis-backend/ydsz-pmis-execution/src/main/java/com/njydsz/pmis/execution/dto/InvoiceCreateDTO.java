package com.njydsz.pmis.execution.dto;

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

    @NotBlank(message = "发票业务编号不能为空")
    private String invoiceCode;

    @NotBlank(message = "发票类型不能为空")
    private String invoiceType;          // NORMAL/RED_REVERSE

    @NotNull(message = "合同 ID 不能为空")
    private Long contractId;

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;

    private String customerName;

    @NotBlank(message = "开票依据不能为空")
    private String invoiceBasis;         // MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER

    @NotNull(message = "金额不能为空")
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
    private Long reversedById;

    /** 外包开票时：客户确认人天单附件 ID */
    private String outsourcingProofId;

    /** 里程碑/终验开票时：验收报告附件 ID */
    private String acceptanceProofId;

    private String attachmentId;
    private Long appliedBy;
}
