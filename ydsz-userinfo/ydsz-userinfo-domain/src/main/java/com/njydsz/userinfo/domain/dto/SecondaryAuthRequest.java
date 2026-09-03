package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import com.njydsz.common.safe.annotation.SensitiveLevel;

/**
 * 场景化二级认证请求体（P0-2 标准化）。
 *
 * <p>前端调用 {@code Post /api/v1/auth/secondary-auth} 接口时传入，包含当前用户密码和目标场景标识。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SecondaryAuthRequest {

  /**
   * 当前登录用户的明文密码。
   *
   * <p>用于验证用户身份，验证通过后写入场景化安全标记。
   */
  @NotBlank(message = "密码不能为空")
  private String password;

  /**
   * 场景标识（scene）。
   *
   * <p>用于区分不同业务场景的二级认证，每个场景独立验证、独立过期。
   * 常用值：{@code password_change}、{@code role_assign}、{@code data_export}、{@code tenant_config}
   */
  @NotBlank(message = "场景标识不能为空")
  private String scene;

  /**
   * 二级认证有效期（秒）。
   *
   * <p>默认 300 秒（5 分钟）。CRITICAL 级别会自动缩短为 40%。
   */
  private Integer ttlSeconds;

  /**
   * 敏感操作等级。
   *
   * <p>默认 HIGH。CRITICAL 级别使用更短的验证窗口（TTL 的 40%，最小 60 秒）。
   */
  private SensitiveLevel level;
}
