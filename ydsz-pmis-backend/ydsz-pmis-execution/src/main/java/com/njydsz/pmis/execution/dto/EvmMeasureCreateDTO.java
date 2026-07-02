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

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    private Long wbsTaskId;

    @NotBlank(message = "周期 YYYY-MM 不能为空")
    private String period;

    @NotNull(message = "PV 不能为空")
    private BigDecimal pv;

    @NotNull(message = "EV 不能为空")
    private BigDecimal ev;

    @NotNull(message = "AC 不能为空")
    private BigDecimal ac;

    @NotNull(message = "BAC 不能为空")
    private BigDecimal bac;

    private LocalDate measureDate;
    private String remark;
}
