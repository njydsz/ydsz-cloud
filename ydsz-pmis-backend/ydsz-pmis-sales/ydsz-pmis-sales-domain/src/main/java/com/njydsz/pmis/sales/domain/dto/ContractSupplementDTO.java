package com.njydsz.pmis.sales.domain.dto;

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
 * 合同补充协议 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "合同补充协议")
public class ContractSupplementDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 合同 ID */
    @NotNull
    @Schema(description = "合同 ID", requiredMode = RequiredMode.REQUIRED)
    private String contractId;

    /** 补充协议编号 */
    @NotBlank
    @Schema(description = "补充协议编号", requiredMode = RequiredMode.REQUIRED)
    private String supplementCode;

    /** 补充协议名称 */
    @NotBlank
    @Schema(description = "补充协议名称", requiredMode = RequiredMode.REQUIRED)
    private String supplementName;

    /** 补充类型（AMOUNT/SCOPE/TERM/OTHER） */
    @NotBlank
    @Schema(description = "类型 AMOUNT/SCOPE/TERM/OTHER", requiredMode = RequiredMode.REQUIRED)
    private String supplementType;

    /** 变更金额（可正可负） */
    @Schema(description = "变更金额（可正可负）")
    private BigDecimal changeAmount;

    /** 变更后合同总额 */
    @Schema(description = "变更后合同总额")
    private BigDecimal newTotalAmount;

    /** 生效日期 */
    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    /** 到期日期 */
    @Schema(description = "到期日期")
    private LocalDate expireDate;

    /** 补充协议内容 */
    @Schema(description = "补充协议内容")
    private String content;

    /** 附件 ID */
    @Schema(description = "附件 ID")
    private String fileId;
}
