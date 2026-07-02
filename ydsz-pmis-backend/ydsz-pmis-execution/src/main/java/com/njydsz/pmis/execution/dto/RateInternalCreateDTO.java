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

    /** 费率业务编号 */
    @NotBlank(message = "费率编号不能为空")
    private String rateCode;

    /** 职级 L1-L18 */
    @NotBlank(message = "职级不能为空")
    private String levelCode;

    /** 事业部/部门 ID */
    private Long departmentId;
    /** 部门名称 */
    private String departmentName;

    /** 计费单位：DAY/HOUR */
    @NotBlank(message = "计费单位不能为空")
    private String billingUnit;

    /** 内部成本金额 */
    @NotNull(message = "成本金额不能为空")
    private BigDecimal costAmount;

    /** 币种：CNY */
    private String currency;
    /** 生效日期 */
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expiryDate;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
    /** 备注 */
    private String remark;
}
