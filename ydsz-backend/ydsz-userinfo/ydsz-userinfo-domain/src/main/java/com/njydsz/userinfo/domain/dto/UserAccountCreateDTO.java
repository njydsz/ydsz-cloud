package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 用户创建 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在 3-64 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 个字符之间")
    private String password;

    @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Size(max = 255, message = "头像 URL 长度不能超过 255 个字符")
    private String avatar;

    private String userType;
    private String companyId;
    private String tenantId;
}
