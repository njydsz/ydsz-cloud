package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 用户创建 DTO，用于 POST 请求。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountCreateDTO {

    /** 登录用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在 3-64 个字符之间")
    private String username;

    /** 登录密码（明文，后端 BCrypt 加密后存储） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 个字符之间")
    private String password;

    /** 真实姓名 */
    @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
    private String realName;

    /** 手机号码 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 邮箱地址 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    /** 头像 URL */
    @Size(max = 255, message = "头像 URL 长度不能超过 255 个字符")
    private String avatar;

    /** 用户类型，如 SYS（系统）、BIZ（业务） */
    private String userType;
    /** 所属公司 ID */
    private String companyId;
    /** 租户 ID */
    private String tenantId;
}
