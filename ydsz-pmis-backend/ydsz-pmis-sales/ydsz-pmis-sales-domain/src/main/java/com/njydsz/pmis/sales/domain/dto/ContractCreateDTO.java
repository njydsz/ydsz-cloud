package com.njydsz.pmis.sales.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.njydsz.pmis.common.safe.annotation.Xss;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * 合同创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "合同创建请求")
public class ContractCreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 合同编号 */
    @NotBlank
    @Schema(description = "合同编号", requiredMode = RequiredMode.REQUIRED)
    private String contractCode;

    /** 合同名称 */
    @NotBlank
    @Schema(description = "合同名称", requiredMode = RequiredMode.REQUIRED)
    private String contractName;

    /** 来源立项 ID */
    @Schema(description = "来源立项 ID")
    private String initiationId;

    /** 客户 ID */
    @NotNull
    @Schema(description = "客户 ID", requiredMode = RequiredMode.REQUIRED)
    private String customerId;

    /** 客户名称 */
    @Schema(description = "客户名称")
    private String customerName;

    /** 合同类型（FIXED_PRICE/T&M/OUTSOURCING/PRODUCT/MAINTENANCE） */
    @NotBlank
    @Schema(description = "合同类型 FIXED_PRICE/T&M/OUTSOURCING/PRODUCT/MAINTENANCE", requiredMode = RequiredMode.REQUIRED)
    private String contractType;

    /** 签约日期 */
    @Schema(description = "签约日期")
    private LocalDate signDate;

    /** 生效日期 */
    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    /** 到期日期 */
    @Schema(description = "到期日期")
    private LocalDate expireDate;

    /** 合同总额 */
    @NotNull
    @Schema(description = "合同总额", requiredMode = RequiredMode.REQUIRED)
    private BigDecimal totalAmount;

    /** 币种 */
    @Schema(description = "币种", example = "CNY")
    private String currency;

    /** 付款条款 */
    @Schema(description = "付款条款")
    @Xss(message = "付款条款包含非法字符")
    private String paymentTerms;

    /** 结算周期 */
    @Schema(description = "结算周期")
    private String billingCycle;

    /** 税率 0-1 */
    @Schema(description = "税率 0-1")
    private BigDecimal taxRate;

    /** 负责人 ID */
    @NotNull
    @Schema(description = "负责人 ID", requiredMode = RequiredMode.REQUIRED)
    private String ownerId;

    /** 负责人姓名 */
    @Schema(description = "负责人姓名")
    private String ownerName;

    /** 合同文件 ID */
    @Schema(description = "合同文件 ID")
    private String contractFileId;

    /** 备注 */
    @Schema(description = "备注")
    @Xss(message = "备注包含非法字符")
    private String remark;
}
