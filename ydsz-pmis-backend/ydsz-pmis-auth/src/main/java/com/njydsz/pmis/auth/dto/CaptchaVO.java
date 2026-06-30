package com.njydsz.pmis.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "图形验证码")
public class CaptchaVO {

    @Schema(description = "验证码 Key（用于登录时校验）")
    private String captchaKey;

    @Schema(description = "验证码图片 Base64")
    private String captchaImage;
}
