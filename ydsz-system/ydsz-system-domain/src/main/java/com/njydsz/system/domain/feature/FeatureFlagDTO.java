package com.njydsz.system.domain.feature;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 特性开关保存/更新 DTO（ydsz-system-domain）。
 *
 * <p>创建 / 更新特性开关的请求体，必填字段施加 JSR-303 校验注解。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Data
public class FeatureFlagDTO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 主键 ID（创建时由后端自动生成，更新时必填） */
  private String id;

  /** 开关键（全局唯一，必填，正则开头字母+数字/下划线/点） */
  @NotBlank(message = "开关键不能为空")
  @Pattern(regexp = "^[A-Z][A-Z0-9_.]{2,63}$",
      message = "开关键需大写字母开头，仅含数字、下划线、点，长度 3-64")
  private String flagKey;

  /** 开关名称 */
  @NotBlank(message = "开关名称不能为空")
  private String flagName;

  /** 开关类型: BOOLEAN / STRING / JSON */
  @NotBlank(message = "开关类型不能为空")
  private String flagType;

  /** 默认值 */
  private String defaultValue;

  /** 当前生效值 */
  private String currentValue;

  /** 描述 */
  private String description;

  /** 启用状态: ENABLED / DISABLED */
  @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态值必须为 ENABLED 或 DISABLED")
  private String status;
}
