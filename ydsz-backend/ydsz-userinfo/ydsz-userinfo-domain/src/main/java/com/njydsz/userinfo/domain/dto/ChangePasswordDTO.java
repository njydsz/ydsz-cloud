package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 修改密码 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "用户 ID 不能为空")
    private String userId;

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 个字符之间")
    private String newPassword;
}
