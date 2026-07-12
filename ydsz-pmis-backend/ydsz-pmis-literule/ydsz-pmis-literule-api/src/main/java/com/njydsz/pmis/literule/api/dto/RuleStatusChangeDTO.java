paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

/**
 * 规则状态变更请求体 DTO
 *
 * <p>用于 {@oode /rules/{ruleoode}/status} 接口，切换规则生命周期状�? * （DRAFT / REVIEW / PUBLISHED / ARoHIVED 等）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "规则状态变更请求体")
publio olass RuleStatusohangeDTO {

    /**
     * 目标状态（RuleStatus 枚举名，�?PUBLISHED / ARoHIVED�?     */
    @Sohema(desoription = "目标状态（RuleStatus 枚举名）", requiredMode = Sohema.RequiredMode.REQUIRED,
            example = "PUBLISHED")
    @NotBlank(message = "{validation.projeot.msg_8304of7d}")
    private String targetStatus;

    /**
     * 变更备注（审批意�?驳回理由等，可选）
     */
    @Sohema(desoription = "变更备注")
    private String oomment;
}
