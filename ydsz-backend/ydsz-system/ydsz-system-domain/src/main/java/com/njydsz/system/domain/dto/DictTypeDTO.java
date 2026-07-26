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
 * 字典类型创建/更新 DTO。
 *
 * @author ydsz-team
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型创建/更新 DTO")
public class DictTypeDTO extends BaseDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过64")
    @Schema(description = "字典类型编码")
    private String typeCode;

    @NotBlank(message = "字典类型名称不能为空")
    @Size(max = 128, message = "字典类型名称长度不能超过128")
    @Schema(description = "字典类型名称")
    private String typeName;

    @Schema(description = "字典类型业务说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
