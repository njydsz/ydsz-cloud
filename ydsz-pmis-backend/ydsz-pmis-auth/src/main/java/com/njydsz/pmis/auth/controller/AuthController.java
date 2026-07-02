package com.njydsz.pmis.auth.controller;

import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;
import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.service.AuthService;
import com.njydsz.pmis.auth.service.impl.AuthServiceImpl;
import com.njydsz.pmis.common.api.Result;
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

    /** 认证服务 */
    private final AuthService authService;

    /**
     * 获取图形验证码
     *
     * @return 统一响应结果，包含验证码 Key 与 Base64 图片
     */
    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(authService.generateCaptcha());
    }

    /**
     * 登录
     *
     * @param dto 登录请求参数
     * @return 统一响应结果，包含访问 Token 与刷新 Token
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 统一响应结果，包含新的访问 Token 与刷新 Token
     */
    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<LoginResultVO> refresh(@RequestParam String refreshToken) {
        return Result.ok(authService.refresh(refreshToken));
    }

    /**
     * 登出
     *
     * @param userId        用户 ID（从请求头 X-User-Id 获取）
     * @param authorization 认证头（从请求头 Authorization 获取，用于将 Token 加入黑名单）
     * @return 统一响应结果
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-User-Id", required = false) String userId,
                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 把当前 Token 加入黑名单（防止 8 小时内继续使用）
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            ((AuthServiceImpl) authService).blacklistToken(token, 8 * 3600);
        }
        authService.logout(userId);
        return Result.ok();
    }
}
