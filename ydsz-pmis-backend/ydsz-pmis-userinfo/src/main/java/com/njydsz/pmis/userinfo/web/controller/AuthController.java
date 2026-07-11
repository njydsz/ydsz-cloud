package com.njydsz.pmis.userinfo.web.controller.auth;

import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.userinfo.domain.dto.auth.LoginDTO;
import com.njydsz.pmis.userinfo.domain.dto.auth.LoginResultVO;
import com.njydsz.pmis.userinfo.domain.dto.auth.CaptchaVO;
import com.njydsz.pmis.userinfo.server.service.auth.AuthService;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "认证管理", description = "认证管理相关接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    /** 认证服务 */
    private final AuthService authService;
    /** JWT Token 工具（用于登出时计算 Token 剩余有效期） */
    private final JwtTokenProvider jwtTokenProvider;

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
    @RateLimit(key = "login", qps = 5, windowSeconds = 60,
            message = "{validation.auth.msg_aea5163a}")
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
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
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/refresh")
    public Result<LoginResultVO> refresh(@Parameter(description = "刷新Token") @RequestParam String refreshToken) {
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
    @IdempotentExempt("认证/会话/2FA 相关接口，无需幂等")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-User-Id", required = false) String userId,
                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 把当前 Token 加入黑名单（TTL 按 Token 实际剩余有效期计算，已过期则无需拉黑）
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            long remainingSeconds = jwtTokenProvider.getRemainingExpirationSeconds(token);
            if (remainingSeconds > 0) {
                authService.blacklistToken(token, remainingSeconds);
            }
        }
        authService.logout(userId);
        return Result.ok();
    }
}
