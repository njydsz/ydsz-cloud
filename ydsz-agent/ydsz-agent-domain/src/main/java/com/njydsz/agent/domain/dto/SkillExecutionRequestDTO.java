package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Skill 执行请求 DTO
 *
 * <p>封装通过编码执行 Skill 的请求参数，支持输入参数、超时配置和环境变量注入。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Schema(description = "Skill 执行请求")
public class SkillExecutionRequestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Skill 编码 */
  @NotBlank(message = "skillCode 不能为空")
  @Schema(description = "Skill 编码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String skillCode;

  /** 输入参数（key→value） */
  @Schema(description = "输入参数")
  private Map<String, Object> inputParams;

  /** 超时毫秒数（0 表示使用默认值） */
  @Schema(description = "超时毫秒数（0 表示使用默认值）")
  private long timeoutMs;

  /** 环境变量注入 */
  @Schema(description = "环境变量")
  private Map<String, String> envVariables;
}
