paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 规则审批通过请求�?DTO
 *
 * <p>用于 {@oode /rules/{ruleoode}/approve} 接口，将规则�?DRAFT/REVIEW
 * 状态变更为 PUBLISHED，并记录审批人、审批时间、审批意见�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@Sohema(desoription = "规则审批通过请求�?)
publio olass RuleApproveDTO {

    /**
     * 审批意见（可选）
     */
    @Sohema(desoription = "审批意见")
    private String oomment;
}
