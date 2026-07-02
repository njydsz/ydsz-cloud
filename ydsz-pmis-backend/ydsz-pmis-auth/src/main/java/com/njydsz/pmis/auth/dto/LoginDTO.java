package com.njydsz.pmis.auth.dto;

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

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于 6 位")
    @Schema(description = "密码", example = "admin123")
    private String password;

    @Schema(description = "记住我")
    private Boolean rememberMe;

    @Schema(description = "图形验证码 Key")
    private String captchaKey;

    @Schema(description = "图形验证码")
    private String captchaCode;
}
