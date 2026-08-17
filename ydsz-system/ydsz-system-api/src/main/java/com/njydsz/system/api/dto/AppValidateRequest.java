package com.njydsz.system.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用密钥校验请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "应用密钥校验请求")
public class AppValidateRequest {

  /** 应用 Key（client_id） */
  @Schema(description = "应用 Key（client_id）", required = true)
  private String appKey;

  /** 应用密钥（client_secret） */
  @Schema(description = "应用密钥（client_secret）", required = true)
  private String appSecret;
}
