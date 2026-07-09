package com.njydsz.pmis.project.dto.execution;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算明细 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "立项预算明细")
public class BudgetItemDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 立项 ID */
    @NotNull
    @Schema(description = "立项 ID", requiredMode = RequiredMode.REQUIRED)
    private String initiationId;

    /** 分类（LABOR/PURCHASE/EXPENSE/OUTSOURCE/OTHER） */
    @NotBlank
    @Schema(description = "分类: LABOR/PURCHASE/EXPENSE/OUTSOURCE/OTHER", requiredMode = RequiredMode.REQUIRED)
    private String category;

    /** 子分类 */
    @Schema(description = "子分类")
    private String subCategory;

    /** 说明 */
    @Schema(description = "说明")
    private String description;

    /** 数量 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 */
    @Schema(description = "单位")
    private String unit;

    /** 单价 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 金额 */
    @Schema(description = "金额")
    private BigDecimal amount;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 排序序号 */
    @Schema(description = "排序")
    private Integer sortOrder;
}
