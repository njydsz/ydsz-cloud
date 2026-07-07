package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机更新请求")
public class OpportunityUpdateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 商机 ID */
    @NotNull
    @Schema(description = "商机 ID", requiredMode = RequiredMode.REQUIRED)
    private String id;

    /** 商机名称 */
    @Schema(description = "商机名称")
    private String opportunityName;

    /** 分级 A/B/C */
    @Schema(description = "分级 A/B/C")
    private String level;

    /** 行业 */
    @Schema(description = "行业")
    private String industry;

    /** 预计金额 */
    @Schema(description = "预计金额")
    private BigDecimal estimatedAmount;

    /** 赢率 0-1 */
    @Schema(description = "赢率 0-1")
    private BigDecimal winRate;

    /** 预计签约日期 */
    @Schema(description = "预计签约日期")
    private LocalDate expectedSignDate;

    /** 预计开始日期 */
    @Schema(description = "预计开始日期")
    private LocalDate expectedStartDate;

    /** 预计结束日期 */
    @Schema(description = "预计结束日期")
    private LocalDate expectedEndDate;

    /** 竞争对手 */
    @Schema(description = "竞争对手")
    private String competitor;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 标签，逗号分隔 */
    @Schema(description = "标签，逗号分隔")
    private String tags;
}
