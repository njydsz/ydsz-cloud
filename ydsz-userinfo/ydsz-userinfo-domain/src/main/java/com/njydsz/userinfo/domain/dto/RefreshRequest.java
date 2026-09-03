package com.njydsz.userinfo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 刷新 Token 请求体
 *
 * <p>封装 refreshToken 字段，避免与 {@link com.njydsz.userinfo.domain.dto.LoginDTO} 耦合。
 *
 * <p>由 {@link com.njydsz.userinfo.web.controller.AuthController#refresh(RefreshRequest)} 使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RefreshRequest {

  /** 刷新令牌（来自上一次登录或上一次 refresh 响应） */
  @Schema(description = "刷新令牌", example = "abc123def456...")
  private String refreshToken;
}
