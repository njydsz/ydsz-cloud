package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

@Data
@SuperBuilder
@NoArgsConstructor
@Schema(name = "ConfigDTO", description = "系统配置创建/更新请求体")
public class ConfigDTO {

  @Schema(description = "主键 ID（更新时必填）")
  private String id;

  @NotBlank(message = "配置分组不能为空")
  @Size(max = 64, message = "配置分组长度不能超过64")
  @Xss(message = "配置分组包含非法内容")
  @Schema(description = "配置分组，按业务域分类管理", example = "SYSTEM", requiredMode = Schema.RequiredMode.REQUIRED)
  private String configGroup;

  @NotBlank(message = "配置键不能为空")
  @Size(max = 128, message = "配置键长度不能超过128")
  @Xss(message = "配置键包含非法内容")
  @Schema(description = "配置键，同组内唯一标识", example = "max-upload-size", requiredMode = Schema.RequiredMode.REQUIRED)
  private String configKey;

  @Xss(message = "配置值包含非法内容")
  @Schema(description = "配置值", example = "10485760")
  private String configValue;

  @NotBlank(message = "值类型不能为空")
  @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON", example = "NUMBER", requiredMode = Schema.RequiredMode.REQUIRED)
  private String valueType;

  @Xss(message = "默认值包含非法内容")
  @Schema(description = "默认值（配置未设置时使用）", example = "10485760")
  private String defaultValue;

  @Xss(message = "配置项说明包含非法内容")
  @Schema(description = "配置项业务说明", example = "最大上传文件大小（字节）")
  private String description;

  @Schema(description = "是否对前端公开", example = "true")
  private Boolean isPublic;

  @Schema(description = "排序序号", example = "0")
  private Integer sort;

  @Schema(description = "启用状态", example = "ENABLED", allowableValues = {"ENABLED", "DISABLED"})
  private String status;
}
