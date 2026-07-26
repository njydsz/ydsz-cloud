package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在 3-64 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码（captchaEnabled=true 时必填） */
    private String captcha;

    /** 验证码 key（captchaEnabled=true 时必填） */
    private String captchaKey;
}
