package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 应用密钥校验请求 DTO。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@Schema(description = "应用密钥校验请求")
public class AppValidateRequest {

  /** 应用 Key（client_id） */
  @NotBlank(message = "{system.dto.appValidate.appKey.required}")
  @Schema(description = "应用 Key（client_id）", requiredMode = Schema.RequiredMode.REQUIRED)
  private String appKey;

  /** 应用密钥（client_secret） */
  @NotBlank(message = "{system.dto.appValidate.appSecret.required}")
  @Schema(description = "应用密钥（client_secret）", requiredMode = Schema.RequiredMode.REQUIRED)
  private String appSecret;
}
