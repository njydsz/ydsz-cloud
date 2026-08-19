package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 租户套餐创建/更新 DTO
 *
 * <p>对应 {@code ydsz_tenant_plan} 表的写入参数，是「套餐管理」创建 / 更新接口的入参载体。
 * 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
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
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class TenantPlanDTO {

  private String id;

  @NotBlank(message = "套餐编码不能为空")
  @Size(max = 64, message = "套餐编码长度不能超过64")
  @Xss(message = "套餐编码包含非法内容")
  private String planCode;

  @NotBlank(message = "套餐名称不能为空")
  @Size(max = 128, message = "套餐名称长度不能超过128")
  @Xss(message = "套餐名称包含非法内容")
  private String planName;

  @Size(max = 512, message = "描述长度不能超过512")
  @Xss(message = "描述包含非法内容")
  private String description;

  private Integer sortOrder;

  @Size(max = 2048, message = "配额配置长度不能超过2048")
  @Xss(message = "配额配置包含非法内容")
  private String quotaJson;

  @Size(max = 2048, message = "功能开关长度不能超过2048")
  @Xss(message = "功能开关包含非法内容")
  private String featureJson;

  private String status;
}
