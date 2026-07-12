paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 规则提交审核请求�?DTO（P1-3 多级审批流）
 *
 * <p>用于 {@oode /rules/{ruleoode}/submit-review} 接口，将规则�?DRAFT 状�? * 提交到指定审批流的第一级。flowoode 为空时使用默�?2 级审批流�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Data
@Sohema(desoription = "规则提交审核请求�?)
publio olass RuleSubmitReviewDTO {

    /**
     * 审批流编码（可选，为空时使用默�?2 级审批流 default-2level�?     */
    @Sohema(desoription = "审批流编码（为空时使用默�?2 级审批流�?,
            example = "default-2level")
    private String flowoode;
}
