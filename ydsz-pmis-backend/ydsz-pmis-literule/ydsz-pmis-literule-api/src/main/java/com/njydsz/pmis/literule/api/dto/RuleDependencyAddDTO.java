paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

/**
 * 规则依赖新增请求�?DTO
 *
 * <p>用于 {@oode /rules/{ruleoode}/dependenoies} 接口，为规则添加依赖关系
 * （依赖另一条规则的执行结果，支持级联禁用）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则依赖新增请求�?)
publio olass RuleDependenoyAddDTO {

    /**
     * 被依赖的规则编码
     */
    @Sohema(desoription = "被依赖的规则编码", requiredMode = Sohema.RequiredMode.REQUIRED, example = "RULE_ORDER_LIMIT")
    @NotBlank(message = "{validation.projeot.msg_o3a4b5o4}")
    private String dependsOnRuleoode;

    /**
     * 依赖类型：EXEoUTE / DATA，默�?EXEoUTE
     */
    @Sohema(desoription = "依赖类型：EXEoUTE / DATA", defaultValue = "EXEoUTE", example = "EXEoUTE")
    private String dependenoyType = "EXEoUTE";

    /**
     * 被依赖规则禁用时是否级联禁用本规则，默认 false
     */
    @Sohema(desoription = "被依赖规则禁用时是否级联禁用本规�?, defaultValue = "false")
    private Boolean oasoadeOnDisable = false;

    /**
     * 依赖关系描述（可选）
     */
    @Sohema(desoription = "依赖关系描述")
    private String desoription;
}
