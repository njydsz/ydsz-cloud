package com.njydsz.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流程自动触发规则创建请求体 DTO
 *
 * <p>用于 {@code /workflow/trigger} 接口，创建流程实例完成时的自动触发规则。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "流程自动触发规则创建请求体")
public class FlowAutoTriggerCreateDTO {

  /** 源流程编码（流程实例完成时触发） */
  @Schema(
      description = "源流程编码",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "LEAVE_APPLY")
  @NotBlank(message = "{validation.workflow.trigger.sourceFlowCode.required}")
  private String sourceFlowCode;

  /** 目标流程编码（自动启动的流程） */
  @Schema(
      description = "目标流程编码",
      requiredMode = Schema.RequiredMode.REQUIRED,
      example = "LEAVE_NOTIFY")
  @NotBlank(message = "{validation.workflow.trigger.targetFlowCode.required}")
  private String targetFlowCode;

  /** 触发条件表达式（可选，为空表示无条件触发） */
  @Schema(description = "触发条件表达式（可选）", example = "days >= 3")
  private String conditionExpression;

  /** 触发规则描述（可选） */
  @Schema(description = "触发规则描述")
  private String description;
}
