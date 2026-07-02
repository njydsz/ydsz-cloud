package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVM 测量创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class EvmMeasureCreateDTO {

    @NotNull(message = "{validation.execution.msg_576c2b5e}")
    private Long initiationId;

    private Long wbsTaskId;

    @NotBlank(message = "{validation.execution.msg_f0414199}")
    private String period;

    @NotNull(message = "{validation.execution.msg_35a08bf9}")
    private BigDecimal pv;

    @NotNull(message = "{validation.execution.msg_2484f14d}")
    private BigDecimal ev;

    @NotNull(message = "{validation.execution.msg_1fe74216}")
    private BigDecimal ac;

    @NotNull(message = "{validation.execution.msg_6fda0b24}")
    private BigDecimal bac;

    private LocalDate measureDate;
    private String remark;
}
