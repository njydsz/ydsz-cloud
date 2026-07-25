package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统变量创建/更新 DTO。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "系统变量创建/更新 DTO")
public class VariableDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "变量键不能为空")
    @Size(max = 128, message = "变量键长度不能超过128")
    @Schema(description = "变量键")
    private String variableKey;

    @Schema(description = "变量值")
    private String variableValue;

    @NotBlank(message = "值类型不能为空")
    @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
    private String valueType;

    @Schema(description = "变量说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
