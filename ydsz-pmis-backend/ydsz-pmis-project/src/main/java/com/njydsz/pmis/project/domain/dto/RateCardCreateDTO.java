package com.njydsz.pmis.project.domain.dto;

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

    /** 费率业务编号 */
    @NotBlank(message = "{validation.execution.msg_3fbd3c07}")
    private String rateCode;

    /** 职级 L1-L18 */
    @NotBlank(message = "{validation.execution.msg_11653d4c}")
    private String levelCode;

    /** 项目类型：ProjectType.code（可空） */
    private String projectType;       // 可空
    /** 客户等级：A/B/C/D（可空） */
    private String customerLevel;     // 可空

    /** 计费单位：DAY/HOUR */
    @NotBlank(message = "{validation.execution.msg_8e68458a}")
    private String billingUnit;       // DAY/HOUR

    /** 报价金额 */
    @NotNull(message = "{validation.execution.msg_8e9f9028}")
    private BigDecimal rateAmount;

    /** 币种：CNY/USD/EUR */
    private String currency;
    /** 生效日期 */
    @NotNull(message = "{validation.execution.msg_c10e0b62}")
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expiryDate;
    /** 状态：ACTIVE/INACTIVE */
    private String status;            // ACTIVE/INACTIVE
    /** 备注 */
    private String remark;
}
