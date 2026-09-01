package com.njydsz.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则提交审核请求体 DTO（P1-3 多级审批流）
 *
 * <p>用于 {@code /rules/{ruleCode}/submit-review} 接口，将规则从 DRAFT 状态 提交到指定审批流的第一级。flowCode 为空时使用默认 2
 * 级审批流。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "规则提交审核请求体")
public class RuleSubmitReviewDTO {

  /** 审批流编码（可选，为空时使用默认 2 级审批流 default-2level） */
  @Schema(description = "审批流编码（为空时使用默认 2 级审批流）", example = "default-2level")
  private String flowCode;
}
