package com.njydsz.system.domain.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 租户套餐创建/更新 DTO
 *
 * <p>对应 {@code ydsz_tenant_plan} 表的写入参数。
 * 创建时 {@code id} 为空，更新时 {@code id} 必填。
 *
 * <p><b>字段约束：</b>
 * <ul>
 *   <li>{@code planCode} — 套餐编码，全局唯一</li>
 *   <li>{@code price} — 套餐价格（元/月），使用 BigDecimal 精确计算</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.TenantPlan 套餐实体
 */
@Data
@Schema(description = "租户套餐创建/更新 DTO")
public class TenantPlanDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "套餐编码不能为空")
    @Size(max = 64, message = "套餐编码长度不能超过64")
    @Xss(message = "套餐编码包含非法内容")
    @Schema(description = "套餐编码")
    private String planCode;

    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 128, message = "套餐名称长度不能超过128")
    @Xss(message = "套餐名称包含非法内容")
    @Schema(description = "套餐名称")
    private String planName;

    @NotBlank(message = "套餐类型不能为空")
    @Schema(description = "套餐类型: BASIC/PROFESSIONAL/ENTERPRISE")
    private String planType;

    @NotNull(message = "套餐价格不能为空")
    @Schema(description = "套餐价格（元/月）")
    private BigDecimal price;

    @NotNull(message = "套餐时长不能为空")
    @Schema(description = "套餐时长（月）")
    private Integer durationMonths;

    @Schema(description = "最大用户数")
    private Integer maxUsers;

    @Schema(description = "最大存储容量（GB）")
    private Long maxStorage;

    @Size(max = 512, message = "描述长度不能超过512")
    @Xss(message = "描述包含非法内容")
    @Schema(description = "套餐描述")
    private String description;

    @Schema(description = "状态: ENABLED/DISABLED")
    private String status;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
