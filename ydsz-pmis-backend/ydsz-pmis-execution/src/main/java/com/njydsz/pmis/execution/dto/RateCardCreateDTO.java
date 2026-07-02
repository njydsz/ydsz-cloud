package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对外报价费率 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RateCardCreateDTO {

    @NotBlank(message = "费率编号不能为空")
    private String rateCode;

    @NotBlank(message = "职级不能为空")
    private String levelCode;

    private String projectType;       // 可空
    private String customerLevel;     // 可空

    @NotBlank(message = "计费单位不能为空")
    private String billingUnit;       // DAY/HOUR

    @NotNull(message = "报价金额不能为空")
    private BigDecimal rateAmount;

    private String currency;
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String status;            // ACTIVE/INACTIVE
    private String remark;
}
