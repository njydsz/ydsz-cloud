package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机创建请求")
public class OpportunityCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "商机编号", required = true)
    private String opportunityCode;

    @NotBlank
    @Size(max = 256)
    @Schema(description = "商机名称", required = true)
    private String opportunityName;

    @NotNull
    @Schema(description = "客户 ID", required = true)
    private Long customerId;

    @Schema(description = "客户名称（冗余）")
    private String customerName;

    @Schema(description = "业务部门 ID")
    private Long businessDeptId;

    @NotNull
    @Schema(description = "负责人 ID", required = true)
    private Long ownerId;

    @Schema(description = "负责人姓名（冗余）")
    private String ownerName;

    @Schema(description = "分级 A/B/C", example = "C")
    private String level;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "行业")
    private String industry;

    @Schema(description = "预计金额")
    private BigDecimal estimatedAmount;

    @Schema(description = "赢率 0-1")
    private BigDecimal winRate;

    @Schema(description = "预计签约日期")
    private LocalDate expectedSignDate;

    @Schema(description = "预计开始日期")
    private LocalDate expectedStartDate;

    @Schema(description = "预计结束日期")
    private LocalDate expectedEndDate;

    @Schema(description = "竞争对手")
    private String competitor;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "标签，逗号分隔")
    private String tags;
}
