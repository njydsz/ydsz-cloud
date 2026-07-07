package com.njydsz.pmis.project.dto;

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
    @NotBlank(message = "{validation.execution.msg_3fbd3c07}")
    private String rateCode;

    /** 职级 L1-L18 */
    @NotBlank(message = "{validation.execution.msg_11653d4c}")
    private String levelCode;

    /** 事业部/部门 ID */
    private String departmentId;
    /** 部门名称 */
    private String departmentName;

    /** 计费单位：DAY/HOUR */
    @NotBlank(message = "{validation.execution.msg_8e68458a}")
    private String billingUnit;

    /** 内部成本金额 */
    @NotNull(message = "{validation.execution.msg_eb814b7e}")
    private BigDecimal costAmount;

    /** 币种：CNY */
    private String currency;
    /** 生效日期 */
    @NotNull(message = "{validation.execution.msg_c10e0b62}")
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expiryDate;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
    /** 备注 */
    private String remark;
}
