package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "商机 ID", required = true)
    private Long opportunityId;

    @NotBlank
    @Schema(description = "跟进类型", required = true,
            allowableValues = {"VISIT", "CALL", "QUOTE", "NEGOTIATE", "OTHER"})
    private String followType;

    @Schema(description = "跟进内容")
    private String content;

    @Schema(description = "下一步计划")
    private String nextStep;

    @Schema(description = "下次跟进日期")
    private LocalDate nextFollowDate;
}
