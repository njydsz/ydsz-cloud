package com.njydsz.system.domain.dto;

import com.njydsz.common.domain.dto.BaseDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统配置创建/更新 DTO。
 *
 * @author ydsz-team
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统配置创建/更新 DTO")
public class ConfigDTO extends BaseDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "配置分组不能为空")
    @Size(max = 64, message = "配置分组长度不能超过64")
    @Schema(description = "配置分组")
    private String configGroup;

    @NotBlank(message = "配置键不能为空")
    @Size(max = 128, message = "配置键长度不能超过128")
    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @NotBlank(message = "值类型不能为空")
    @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
    private String valueType;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "配置项说明")
    private String description;

    @Schema(description = "是否对前端公开: 1 公开 / 0 仅后端")
    private Integer isPublic;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
