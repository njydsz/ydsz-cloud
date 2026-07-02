package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对内成本费率 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RateInternalCreateDTO {

    @NotBlank(message = "费率编号不能为空")
    private String rateCode;

    @NotBlank(message = "职级不能为空")
    private String levelCode;

    private Long departmentId;
    private String departmentName;

    @NotBlank(message = "计费单位不能为空")
    private String billingUnit;

    @NotNull(message = "成本金额不能为空")
    private BigDecimal costAmount;

    private String currency;
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String status;
    private String remark;
}
