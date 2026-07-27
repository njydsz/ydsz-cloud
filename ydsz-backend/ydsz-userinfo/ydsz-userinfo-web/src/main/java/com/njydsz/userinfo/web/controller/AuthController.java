package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
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
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 认证 Controller
 *
 * <p>提供用户登录、登出、刷新 Token 等基础认证端点。
 * 是整个用户中心服务的对外认证入口，被各业务系统通过 Feign 远程调用（{@code AuthServiceClient}）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/auth}
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>登录接口：启用 {@link RateLimit} 限流 50 QPS + {@link Idempotent} 5s 防重放 + 滑块验证码（可选）</li>
 *   <li>登出接口：将 access_token 加入 Redis 黑名单，使其立即失效</li>
 *   <li>刷新接口：限流 + 5s 幂等 + refresh_token 分布式锁（防并发重放）</li>
 *   <li>密码错误次数超限自动锁定账号（{@code ydsz.auth.login-fail-threshold}）</li>
 * </ul>
 *
 * <p><b>Token 设计：</b>
 * <ul>
 *   <li>access_token：短期（默认 2h），用于业务接口鉴权</li>
 *   <li>refresh_token：长期（默认 7d），仅用于刷新 access_token</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.auth.AuthService 认证业务逻辑
 * @see com.njydsz.userinfo.web.controller.OAuth2Controller OAuth2.0 授权端点
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录/登出/Token 刷新")
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录（账号密码模式）
     *
     * <p>认证流程：账号密码校验 → 滑块验证码校验（可选）→ 签发 access_token / refresh_token。
     * 失败次数超限后账号会被自动锁定 {@code ydsz.auth.lock-duration-minutes} 分钟。
     *
     * @param request 登录请求（含 username/password/captchaKey/captchaCode/tenantId 等）
     * @return 登录结果（accessToken / refreshToken / expiresIn / userInfo）
     */
    @RateLimit(resource = "userinfo.auth.login", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:AuthController:login:lock", ttlSeconds = 5)
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "账号密码登录，返回 access_token 和 refresh_token")
    public BaseResponse<LoginVO> login(@Valid @RequestBody LoginDTO request) {
        LoginVO result = authService.login(request);
        return BaseResponse.success(result);
    }

    /**
     * 用户登出
     *
     * <p>将 access_token 加入 Redis 黑名单（TTL 与 token 剩余有效期对齐），
     * 同时清理服务端会话状态。refresh_token 仍可使用一次以兼容客户端清理逻辑。
     *
     * @param token Authorization 请求头（Bearer xxx 或裸 token）
     * @return 成功响应
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "将 access_token 加入黑名单")
    public BaseResponse<Void> logout(@RequestHeader("Authorization") String token) {
        String accessToken = token != null && token.startsWith("Bearer ")
                ? token.substring(7) : token;
        authService.logout(accessToken);
        return BaseResponse.success();
    }

    /**
     * 刷新 access_token
     *
     * <p>使用 refresh_token 换发新的 access_token（同时返回新的 refresh_token 实现轮换）。
     * 旧 refresh_token 立即失效（一次性）。启用分布式锁防止并发重放。
     *
     * @param request 刷新请求（含 refreshToken）
     * @return 新的登录结果
     */
    @RateLimit(resource = "userinfo.auth.refresh", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:AuthController:refresh:lock", ttlSeconds = 5)
    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 refresh_token 获取新的 access_token")
    public BaseResponse<LoginVO> refresh(@RequestBody RefreshRequest request) {
        LoginVO result = authService.refresh(request.getRefreshToken());
        return BaseResponse.success(result);
    }

    /**
     * 刷新 Token 请求体
     */
    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
