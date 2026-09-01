package com.njydsz.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 规则审批驳回请求体 DTO
 *
 * <p>用于 {@code /rules/{ruleCode}/reject} 接口，将规则从 DRAFT/REVIEW/PUBLISHED 状态变更为 ARCHIVED，并记录驳回理由。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "规则审批驳回请求体")
public class RuleRejectDTO {

  /** 驳回理由（必填） */
  @Schema(
      description = "驳回理由",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "条件表达式覆盖不全，需补充金额上限判断")
  @NotBlank(message = "{validation.project.msg_d4b5c6d4}")
  private String reason;
}
