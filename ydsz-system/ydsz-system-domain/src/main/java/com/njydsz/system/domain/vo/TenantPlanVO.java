package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 租户套餐 VO（兼 DTO）
 *
 * <p>对应 {@code ydsz_tenant_plan} 表的展示视图和写入参数，是「套餐管理」列表 / 详情 / 创建 / 更新接口的通用载体。 由 {@link
 * com.njydsz.system.domain.converter.SystemConverter} 从 {@link
 * com.njydsz.system.domain.entity.TenantPlan} 实体转换而来。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code planCode} — 套餐编码，全局唯一标识
 *   <li>{@code sortOrder} — 排序号（升序，影响前端套餐选择器顺序）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.TenantPlan 套餐实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "租户套餐视图对象")
public class TenantPlanVO {

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

  @Size(max = 512, message = "描述长度不能超过512")
  @Xss(message = "描述包含非法内容")
  @Schema(description = "套餐描述")
  private String description;

  @Schema(description = "排序号")
  private Integer sortOrder;
}
