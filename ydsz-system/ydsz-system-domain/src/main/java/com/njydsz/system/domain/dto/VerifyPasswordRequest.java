package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码校验请求 DTO。
 *
 * <p>供系统管理模块内部二次认证使用，传递用户 ID 与明文密码到用户中心服务进行校验。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
@Schema(description = "密码校验请求（供二次认证内部调用）")
public class VerifyPasswordRequest {

  /** 用户 ID */
  @NotBlank(message = "用户 ID 不能为空")
  @Schema(description = "用户 ID", required = true)
  private String userId;

  /** 明文密码（HTTPS 传输） */
  @NotBlank(message = "密码不能为空")
  @Schema(description = "明文密码（HTTPS 传输）", required = true)
  private String password;
}
