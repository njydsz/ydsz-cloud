package com.njydsz.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * 商机创建 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机创建请求")
public class OpportunityCreateDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 商机编号 */
    @NotBlank
    @Size(max = 64)
    @Schema(description = "商机编号", requiredMode = RequiredMode.REQUIRED)
    private String opportunityCode;

    /** 商机名称 */
    @NotBlank
    @Size(max = 256)
    @Schema(description = "商机名称", requiredMode = RequiredMode.REQUIRED)
    private String opportunityName;

    /** 客户 ID */
    @NotNull
    @Schema(description = "客户 ID", requiredMode = RequiredMode.REQUIRED)
    private String customerId;

    /** 客户名称（冗余） */
    @Schema(description = "客户名称（冗余）")
    private String customerName;

    /** 业务部门 ID */
    @Schema(description = "业务部门 ID")
    private String businessDeptId;

    /** 负责人 ID */
    @NotNull
    @Schema(description = "负责人 ID", requiredMode = RequiredMode.REQUIRED)
    private String ownerId;

    /** 负责人姓名（冗余） */
    @Schema(description = "负责人姓名（冗余）")
    private String ownerName;

    /** 分级 A/B/C */
    @Schema(description = "分级 A/B/C", example = "C")
    private String level;

    /** 商机来源 */
    @Schema(description = "来源")
    private String source;

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
