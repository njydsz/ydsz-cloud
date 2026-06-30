package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 回款核销请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PaymentAllocationDTO {

    @NotNull(message = "回款 ID 不能为空")
    private Long paymentId;

    @NotNull(message = "发票 ID 不能为空")
    private Long invoiceId;

    @NotNull(message = "核销金额不能为空")
    private BigDecimal amount;

    private Long operatorId;
}
