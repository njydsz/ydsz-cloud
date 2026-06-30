package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "合同 ID", required = true)
    private Long contractId;

    @NotBlank
    @Schema(description = "补充协议编号", required = true)
    private String supplementCode;

    @NotBlank
    @Schema(description = "补充协议名称", required = true)
    private String supplementName;

    @NotBlank
    @Schema(description = "类型 AMOUNT/SCOPE/TERM/OTHER", required = true)
    private String supplementType;

    @Schema(description = "变更金额（可正可负）")
    private BigDecimal changeAmount;

    @Schema(description = "变更后合同总额")
    private BigDecimal newTotalAmount;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "到期日期")
    private LocalDate expireDate;

    @Schema(description = "补充协议内容")
    private String content;

    @Schema(description = "附件 ID")
    private Long fileId;
}
