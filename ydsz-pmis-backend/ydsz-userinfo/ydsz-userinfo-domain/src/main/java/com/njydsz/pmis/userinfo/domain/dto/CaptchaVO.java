package com.njydsz.userinfo.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 图形验证码返回 VO
 *
 * <p>登录页拉取验证码后，前端保存 captchaKey 并在登录请求中回传。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "图形验证码")
public class CaptchaVO {

    /** 验证码 Key（用于登录时校验） */
    @Schema(description = "验证码 Key（用于登录时校验）")
    private String captchaKey;

    /** 验证码图片 Base64 */
    @Schema(description = "验证码图片 Base64")
    private String captchaImage;
}
