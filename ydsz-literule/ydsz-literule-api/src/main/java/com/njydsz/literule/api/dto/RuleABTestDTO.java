package com.njydsz.literule.api.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import com.njydsz.literule.api.RuleDefinition;

/**
 * A/B 测试请求体 DTO
 *
 * <p>用于 {@code /rules/{ruleCode}/ab-test} 接口：基于当前规则与候选规则定义， 对同一份事实数据分别评估，
 * 输出对比报告（触发差异 / 严重度差异 / 建议结论）。
 *
 * <p>候选规则通常由前端基于当前规则克隆并修改条件/严重度表达式后提交， 服务端仅做评估对比，不落库、不发布事件、不记录统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "A/B 测试请求体")
public class RuleABTestDTO {

  /** 候选规则定义（需包含与当前规则相同的 code，服务端强制覆盖） */
  @Schema(description = "候选规则定义", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "{validation.project.msg_8304cf7d}")
  @Valid
  private RuleDefinition candidate;

  /** 测试用事实数据（同一份 facts 分别评估当前规则与候选规则） */
  @Schema(description = "测试用事实数据", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "{validation.project.msg_8304cf7d}")
  private Map<String, Object> facts;
}
