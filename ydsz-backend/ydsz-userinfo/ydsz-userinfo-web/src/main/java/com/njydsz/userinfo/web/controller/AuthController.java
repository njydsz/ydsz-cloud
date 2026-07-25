package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.server.auth.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 认证 Controller - 登录/登出/刷新 Token。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录/登出/Token 刷新")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "账号密码登录，返回 access_token 和 refresh_token")
    public BaseResponse<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        LoginVO result = authService.login(request.getUsername(), request.getPassword());
        return BaseResponse.success(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "将 access_token 加入黑名单")
    public BaseResponse<Void> logout(@RequestHeader("Authorization") String token) {
        String accessToken = token != null && token.startsWith("Bearer ")
                ? token.substring(7) : token;
        authService.logout(accessToken);
        return BaseResponse.success();
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 refresh_token 获取新的 access_token")
    public BaseResponse<LoginVO> refresh(@RequestBody RefreshRequest request) {
        LoginVO result = authService.refresh(request.getRefreshToken());
        return BaseResponse.success(result);
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
