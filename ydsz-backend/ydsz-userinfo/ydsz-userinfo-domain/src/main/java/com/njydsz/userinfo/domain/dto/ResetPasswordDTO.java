package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 重置密码 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "用户 ID 不能为空")
    private String userId;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
