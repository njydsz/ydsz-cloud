package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 租户套餐 VO
 *
 * <p>对应 {@code ydsz_tenant_plan} 表的展示视图，是「套餐管理」列表 / 详情接口的返回值类型。 由 {@link
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
@Schema(description = "租户套餐视图对象")
public class TenantPlanVO {

  @Schema(description = "主键 ID")
  private String id;

  @Schema(description = "套餐编码")
  private String planCode;

  @Schema(description = "套餐名称")
  private String planName;

  @Schema(description = "套餐描述")
  private String description;

  @Schema(description = "排序号")
  private Integer sortOrder;
}
