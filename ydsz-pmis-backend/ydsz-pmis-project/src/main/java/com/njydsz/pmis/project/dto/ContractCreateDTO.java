package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "合同创建请求")
public class ContractCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "合同编号", requiredMode = RequiredMode.REQUIRED)
    private String contractCode;

    @NotBlank
    @Schema(description = "合同名称", requiredMode = RequiredMode.REQUIRED)
    private String contractName;

    @Schema(description = "来源立项 ID")
    private Long initiationId;

    @NotNull
    @Schema(description = "客户 ID", requiredMode = RequiredMode.REQUIRED)
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @NotBlank
    @Schema(description = "合同类型 FIXED_PRICE/T&M/OUTSOURCING/PRODUCT/MAINTENANCE", required = true)
    private String contractType;

    @Schema(description = "签约日期")
    private LocalDate signDate;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "到期日期")
    private LocalDate expireDate;

    @NotNull
    @Schema(description = "合同总额", requiredMode = RequiredMode.REQUIRED)
    private BigDecimal totalAmount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "付款条款")
    private String paymentTerms;

    @Schema(description = "结算周期")
    private String billingCycle;

    @Schema(description = "税率 0-1")
    private BigDecimal taxRate;

    @NotNull
    @Schema(description = "负责人 ID", requiredMode = RequiredMode.REQUIRED)
    private Long ownerId;

    @Schema(description = "负责人姓名")
    private String ownerName;

    @Schema(description = "合同文件 ID")
    private Long contractFileId;

    @Schema(description = "备注")
    private String remark;
}
