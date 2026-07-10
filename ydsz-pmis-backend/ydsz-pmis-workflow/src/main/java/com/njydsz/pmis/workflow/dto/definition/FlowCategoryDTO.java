package com.njydsz.pmis.workflow.dto.definition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程分类 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@Schema(description = "流程分类")
public class FlowCategoryDTO {

    @Schema(description = "ID（编辑时传）")
    private String id;

    @Schema(description = "分类编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过64字")
    private String categoryCode;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 128, message = "分类名称不能超过128字")
    private String categoryName;

    @Schema(description = "父分类 ID（顶级分类不传）")
    private String parentId;

    @Schema(description = "排序号")
    private Integer sortNum;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "备注")
    private String remark;
}
