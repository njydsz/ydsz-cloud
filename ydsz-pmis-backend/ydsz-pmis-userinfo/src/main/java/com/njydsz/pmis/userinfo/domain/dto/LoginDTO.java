package com.njydsz.pmis.userinfo.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 *
 * <p>携带用户名/密码以及图形验证码（启用时校验）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "登录参数")
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "{validation.auth.msg_0b62b5ce}")
    @Schema(description = "用户名", example = "admin")
    private String username;

    /** 密码（明文，服务端加盐后哈希校验） */
    @NotBlank(message = "{validation.auth.msg_89b5d3d5}")
    @Size(min = 6, message = "{validation.auth.msg_4592106f}")
    @Schema(description = "密码", example = "admin123")
    private String password;

    /** 记住我（用于延长 Token 有效期） */
    @Schema(description = "记住我")
    private Boolean rememberMe;

    /** 图形验证码 Key（由 captcha 接口返回） */
    @Schema(description = "图形验证码 Key")
    private String captchaKey;

    /** 图形验证码（用户输入） */
    @Schema(description = "图形验证码")
    private String captchaCode;
}
