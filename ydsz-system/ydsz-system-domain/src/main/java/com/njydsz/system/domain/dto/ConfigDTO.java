package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 系统配置创建/更新 DTO
 *
 * <p>对应 {@code ydsz_config} 表的写入参数，是「系统配置中心」创建 / 更新接口的入参载体。
 * 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code configGroup} — 配置分组，按业务域分类管理
 *   <li>{@code configKey} — 配置键，同组内唯一标识
 *   <li>{@code configValue} — 配置值
 *   <li>{@code valueType} — 值类型: STRING/NUMBER/BOOLEAN/JSON
 *   <li>{@code defaultValue} — 默认值（配置未设置时使用）
 *   <li>{@code isPublic} — 是否对前端公开: 1 公开 / 0 仅后端
 *   <li>{@code sortOrder} — 排序序号
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "系统配置创建/更新 DTO")
public class ConfigDTO {

  @Schema(description = "主键 ID（更新时必填）")
  private String id;

  @NotBlank(message = "配置分组不能为空")
  @Size(max = 64, message = "配置分组长度不能超过64")
  @Xss(message = "配置分组包含非法内容")
  @Schema(description = "配置分组")
  private String configGroup;

  @NotBlank(message = "配置键不能为空")
  @Size(max = 128, message = "配置键长度不能超过128")
  @Xss(message = "配置键包含非法内容")
  @Schema(description = "配置键")
  private String configKey;

  @Xss(message = "配置值包含非法内容")
  @Schema(description = "配置值")
  private String configValue;

  @NotBlank(message = "值类型不能为空")
  @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
  private String valueType;

  @Xss(message = "默认值包含非法内容")
  @Schema(description = "默认值")
  private String defaultValue;

  @Xss(message = "配置项说明包含非法内容")
  @Schema(description = "配置项说明")
  private String description;

  @Schema(description = "是否对前端公开: 1 公开 / 0 仅后端")
  private Integer isPublic;

  @Schema(description = "排序号")
  private Integer sortOrder;

  @Schema(description = "启用状态: ENABLED/DISABLED")
  private String status;
}
