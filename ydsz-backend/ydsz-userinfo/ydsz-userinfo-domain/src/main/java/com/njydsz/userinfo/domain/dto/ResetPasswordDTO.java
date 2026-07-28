package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置密码请求 DTO（管理员操作）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ResetPasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    private String notifyChannel;
}
