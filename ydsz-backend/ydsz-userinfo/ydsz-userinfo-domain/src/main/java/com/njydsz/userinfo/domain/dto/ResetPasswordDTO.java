package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 重置密码 DTO，管理员重置用户密码场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ResetPasswordDTO {

    /** 用户 ID */
    @NotBlank(message = "用户 ID 不能为空")
    private String userId;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
