package com.njydsz.system.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 租户套餐-菜单关联 DTO
 *
 * <p>用于为套餐批量配置可访问菜单列表。 通过套餐 ID + 菜单 ID 列表一次性设置套餐的菜单权限。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TenantPlanMenuDTO {

  @NotBlank(message = "套餐 ID 不能为空")
  private String planId;

  private List<String> menuIds;
}
