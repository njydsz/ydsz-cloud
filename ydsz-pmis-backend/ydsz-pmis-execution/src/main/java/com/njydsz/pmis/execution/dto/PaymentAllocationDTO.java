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

    @NotNull(message = "{validation.execution.msg_34b0ac9d}")
    private Long paymentId;

    @NotNull(message = "{validation.execution.msg_d09bbb99}")
    private Long invoiceId;

    @NotNull(message = "{validation.execution.msg_17d811ec}")
    private BigDecimal amount;

    private Long operatorId;
}
