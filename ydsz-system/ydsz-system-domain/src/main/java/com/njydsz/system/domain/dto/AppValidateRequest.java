package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 应用密钥校验请求 DTO。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "应用密钥校验请求")
public class AppValidateRequest {

  /** 应用 Key（client_id） */
  @NotBlank(message = "应用 Key 不能为空")
  @Schema(description = "应用 Key（client_id）", required = true)
  private String appKey;

  /** 应用密钥（client_secret） */
  @NotBlank(message = "应用密钥不能为空")
  @Schema(description = "应用密钥（client_secret）", required = true)
  private String appSecret;
}
