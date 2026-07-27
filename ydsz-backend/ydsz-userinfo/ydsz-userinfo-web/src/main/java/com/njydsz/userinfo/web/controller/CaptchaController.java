package com.njydsz.userinfo.web.controller;

import java.util.Map;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.server.auth.CaptchaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 验证码 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
@Tag(name = "验证码", description = "图形验证码生成与校验")
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping("/generate")
    @Operation(summary = "生成验证码", description = "返回 Base64 PNG 图片和 captchaKey")
    public BaseResponse<Map<String, String>> generate() {
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        String imageBase64 = captchaService.generateCaptcha(captchaKey);
        return BaseResponse.success(Map.of(
                "captchaKey", captchaKey,
                "image", imageBase64
        ));
    }

    @RateLimit(resource = "userinfo.captcha.validate", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:CaptchaController:validate:lock", ttlSeconds = 5)
    @PostMapping("/validate")
    @Operation(summary = "校验验证码", description = "校验用户输入的验证码")
    public BaseResponse<Boolean> validate(
            @RequestParam String captchaKey,
            @RequestParam String captcha) {
        return BaseResponse.success(captchaService.validate(captchaKey, captcha));
    }
}
