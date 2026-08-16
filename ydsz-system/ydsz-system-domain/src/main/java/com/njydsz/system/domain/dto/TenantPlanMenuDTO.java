package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 租户套餐-菜单关联 DTO
 *
 * <p>用于为套餐批量配置可访问菜单列表。 通过套餐 ID + 菜单 ID 列表一次性设置套餐的菜单权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体
 */
@Data
@Schema(description = "套餐-菜单关联 DTO")
public class TenantPlanMenuDTO {

  @NotBlank(message = "套餐 ID 不能为空")
  @Schema(description = "套餐 ID")
  private String planId;

  @Schema(description = "菜单 ID 列表")
  private java.util.List<String> menuIds;
}
