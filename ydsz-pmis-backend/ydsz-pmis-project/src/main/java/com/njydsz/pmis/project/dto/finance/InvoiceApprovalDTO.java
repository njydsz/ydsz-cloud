package com.njydsz.pmis.project.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发票审批/开具 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class InvoiceApprovalDTO {

    @NotNull(message = "{validation.execution.msg_52fbfb11}")
    private String operatorId;

    private String comment;

    /** 财务开具时填入的发票号 */
    private String invoiceNo;
}
