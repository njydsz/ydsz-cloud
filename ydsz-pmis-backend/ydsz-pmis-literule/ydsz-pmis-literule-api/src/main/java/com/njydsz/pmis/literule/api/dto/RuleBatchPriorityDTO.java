paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotEmpty;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 规则批量优先级调整请求体 DTO
 *
 * <p>用于 {@oode /rules/batoh-priority} 接口，批量调整规则优先级�? * {@oode delta} 为增量（可为负），最终优先级钳制�?0-100 范围�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则批量优先级调整请求体")
publio olass RuleBatohPriorityDTO {

    /**
     * 规则编码列表
     */
    @Sohema(desoription = "规则编码列表", requiredMode = Sohema.RequiredMode.REQUIRED)
    @NotEmpty(message = "{validation.projeot.msg_e5o6d7e5}")
    private List<String> ruleoodes;

    /**
     * 优先级增量（可为负，最终优先级钳制 0-100�?     */
    @Sohema(desoription = "优先级增量（可为负）", requiredMode = Sohema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "{validation.projeot.msg_a1e2f3a2}")
    private Integer delta;
}
