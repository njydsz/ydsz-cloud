package com.njydsz.pmis.auth.controller;

import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;
import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.service.AuthService;
import com.njydsz.pmis.auth.service.impl.AuthServiceImpl;
import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "认证授权")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(authService.generateCaptcha());
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginResultVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public R<LoginResultVO> refresh(@RequestParam String refreshToken) {
        return R.ok(authService.refresh(refreshToken));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "X-User-Id", required = false) String userId,
                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 把当前 Token 加入黑名单（防止 8 小时内继续使用）
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            ((AuthServiceImpl) authService).blacklistToken(token, 8 * 3600);
        }
        authService.logout(userId);
        return R.ok();
    }
}
