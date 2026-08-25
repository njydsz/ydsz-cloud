package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置密码请求 DTO（管理员操作）。
 *
 * <p>用于 {@code Post /api/v1/user/reset-password} 接口，管理员重置指定用户的密码。 无需提供旧密码，重置后可选择通过指定通道通知用户。
 *
 * <p><b>安全说明：</b>重置密码成功后，目标用户的所有活跃会话将被撤销， 须使用新密码重新登录。该接口需要管理员权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ResetPasswordDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID（指定重置密码的目标用户） */
  @NotBlank(message = "用户ID不能为空")
  private String userId;

  /** 新密码（明文传入，服务端 BCrypt 加密存储，须符合密码策略） */
  @NotBlank(message = "新密码不能为空")
  private String newPassword;

  /** 通知渠道（重置后通知用户，如 {@code SMS} / {@code EMAIL}，不传则不通知） */
  private String notifyChannel;
}
