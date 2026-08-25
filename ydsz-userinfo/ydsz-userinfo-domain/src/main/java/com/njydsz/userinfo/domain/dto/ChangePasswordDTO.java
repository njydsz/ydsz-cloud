package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改密码请求 DTO（用户自助修改）。
 *
 * <p>用于 {@code Post /api/v1/user/change-password} 接口，用户自行修改登录密码。 服务端会校验旧密码是否正确，新密码须符合密码策略（长度+复杂度）。
 *
 * <p><b>安全说明：</b>修改密码成功后，当前会话 Token 不会被撤销， 如需强制下线请调用管理员重置密码接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ChangePasswordDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID（指定修改密码的目标用户） */
  @NotBlank(message = "用户ID不能为空")
  private String userId;

  /** 旧密码（明文传入，服务端 BCrypt 比对验证） */
  @NotBlank(message = "旧密码不能为空")
  private String oldPassword;

  /** 新密码（明文传入，服务端 BCrypt 加密存储，须符合密码策略） */
  @NotBlank(message = "新密码不能为空")
  private String newPassword;
}
