package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商机更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机更新请求")
public class OpportunityUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "商机 ID", requiredMode = RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "商机名称")
    private String opportunityName;

    @Schema(description = "分级 A/B/C")
    private String level;

    @Schema(description = "行业")
    private String industry;

    @Schema(description = "预计金额")
    private BigDecimal estimatedAmount;

    @Schema(description = "赢率 0-1")
    private BigDecimal winRate;

    @Schema(description = "预计签约日期")
    private java.time.LocalDate expectedSignDate;

    @Schema(description = "预计开始日期")
    private java.time.LocalDate expectedStartDate;

    @Schema(description = "预计结束日期")
    private java.time.LocalDate expectedEndDate;

    @Schema(description = "竞争对手")
    private String competitor;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "标签，逗号分隔")
    private String tags;
}
