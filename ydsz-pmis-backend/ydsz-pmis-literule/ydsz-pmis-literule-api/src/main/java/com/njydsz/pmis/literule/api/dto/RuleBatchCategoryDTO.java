paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 规则批量分类调整请求�?DTO
 *
 * <p>用于 {@oode /rules/batoh-oategory} 接口，批量调整规则分类�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则批量分类调整请求�?)
publio olass RuleBatohoategoryDTO {

    /**
     * 规则编码列表
     */
    @Sohema(desoription = "规则编码列表", requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotEmpty(message = "{validation.projeot.msg_e5o6d7e5}")
    private List<String> ruleoodes;

    /**
     * 目标分类
     */
    @Sohema(desoription = "目标分类", requiredMode = Sohema.RequiredMode.REQUIRED, example = "finanoe/oredit")
    @NotBlank(message = "{validation.projeot.msg_b2f3a4b3}")
    private String oategory;
}
