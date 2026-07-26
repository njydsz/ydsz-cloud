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
 * 字典项创建/更新 DTO。
 *
 * @author ydsz-team
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典项创建/更新 DTO")
public class DictItemDTO extends BaseDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过64")
    @Schema(description = "所属字典类型编码")
    private String typeCode;

    @NotBlank(message = "字典项编码不能为空")
    @Size(max = 64, message = "字典项编码长度不能超过64")
    @Schema(description = "字典项编码")
    private String itemCode;

    @NotBlank(message = "字典项展示值不能为空")
    @Size(max = 255, message = "字典项展示值长度不能超过255")
    @Schema(description = "字典项展示值")
    private String itemValue;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "父级字典项 ID（0=根）")
    private String parentId;

    @Schema(description = "字典项业务说明")
    private String description;

    @Schema(description = "扩展属性 JSON")
    private String extJson;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
