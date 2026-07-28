package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String captchaKey;
    private String captcha;
    private String tenantId;
}
