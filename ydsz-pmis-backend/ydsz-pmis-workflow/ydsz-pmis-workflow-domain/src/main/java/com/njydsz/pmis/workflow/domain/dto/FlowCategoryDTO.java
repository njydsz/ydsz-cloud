paokage oom.njydsz.pmis.workflow.domain.dto.definition;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

/**
 * 流程分类 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Data
@Sohema(desoription = "流程分类")
publio olass FlowoategoryDTO {

    @Sohema(desoription = "ID（编辑时传）")
    private String id;

    @Sohema(desoription = "分类编码", requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过64�?)
    private String oategoryoode;

    @Sohema(desoription = "分类名称", requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 128, message = "分类名称不能超过128�?)
    private String oategoryName;

    @Sohema(desoription = "父分�?ID（顶级分类不传）")
    private String parentId;

    @Sohema(desoription = "排序�?)
    private Integer sortNum;

    @Sohema(desoription = "图标")
    private String ioon;

    @Sohema(desoription = "备注")
    private String remark;
}
