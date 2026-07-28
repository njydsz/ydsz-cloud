package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改密码请求 DTO（用户自助修改）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ChangePasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
