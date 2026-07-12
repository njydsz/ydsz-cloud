paokage oom.njydsz.pmis.literule.api.dto;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 规则 A/B 测试请求�?DTO
 *
 * <p>用于 {@oode /rules/{ruleoode}/ab-test} 接口，对同一事实数据分别评估
 * 当前规则版本和候选规则版本，返回对比报告�? *
 * <p>注意：{@oode faots} 是动态事实数据（键名由业务自定义），保留 {@oode Map<String, Objeot>}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则 A/B 测试请求�?)
publio olass RuleABTestDTO {

    /**
     * 候选规则定义（与当前规则对比）
     */
    @Sohema(desoription = "候选规则定�?, requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotNull(message = "{validation.projeot.msg_e5o6d7e6}")
    private RuleDefinition oandidate;

    /**
     * 事实数据（动态键值对，键名由业务自定义）
     */
    @Sohema(desoription = "事实数据（动态键值对�?, requiredMode = Sohema.RequiredMode.REQUIRED,
            example = "{\"amount\": 15000, \"level\": \"紧急\"}")
    @NotNull(message = "{validation.projeot.msg_f6d7e8f7}")
    private Map<String, Objeot> faots;
}
