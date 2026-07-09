package com.njydsz.pmis.project.dto.ruleengine;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则审批通过请求体 DTO
 *
 * <p>用于 {@code /rules/{ruleCode}/approve} 接口，将规则从 DRAFT/REVIEW
 * 状态变更为 PUBLISHED，并记录审批人、审批时间、审批意见。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Schema(description = "规则审批通过请求体")
public class RuleApproveDTO {

    /**
     * 审批意见（可选）
     */
    @Schema(description = "审批意见")
    private String comment;
}
