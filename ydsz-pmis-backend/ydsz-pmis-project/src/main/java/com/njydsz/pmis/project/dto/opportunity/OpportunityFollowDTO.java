package com.njydsz.pmis.project.dto.opportunity;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 商机跟进记录 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机跟进记录")
public class OpportunityFollowDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 商机 ID */
    @NotNull
    @Schema(description = "商机 ID", requiredMode = RequiredMode.REQUIRED)
    private String opportunityId;

    /** 跟进类型（VISIT/CALL/QUOTE/NEGOTIATE/OTHER） */
    @NotBlank
    @Schema(description = "跟进类型", requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"VISIT", "CALL", "QUOTE", "NEGOTIATE", "OTHER"})
    private String followType;

    /** 跟进内容 */
    @Schema(description = "跟进内容")
    private String content;

    /** 下一步计划 */
    @Schema(description = "下一步计划")
    private String nextStep;

    /** 下次跟进日期 */
    @Schema(description = "下次跟进日期")
    private LocalDate nextFollowDate;
}
