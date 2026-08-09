package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.core.constant.HeaderConstants;
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
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

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
@ApiVersion("1")
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录（账号密码模式）
     *
     * <p>认证流程：账号密码校验 → 滑块验证码校验（可选）→ 签发 access_token / refresh_token。
     * <p>限流 50 QPS；5 秒幂等保护（防重放）；失败次数超限后账号会被自动锁定
     * {@code ydsz.auth.lock-duration-minutes} 分钟（默认 30 分钟）。
     * <p>成功登录会重置失败计数；失败累加计数到 {@code ydsz.auth.login-fail-threshold}（默认 5 次）触发锁定。
     *
     * @param request 登录请求（含 username / password / captchaKey / captchaCode / tenantId）
     * @return 登录结果（accessToken / refreshToken / expiresIn / userInfo）
     */
    @Audit(module = "认证管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'用户登录: ' + #request.username")
    @RateLimit(resource = "userinfo.auth.login", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:AuthController:login:lock", ttlSeconds = 5)
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "账号密码登录，返回 access_token 和 refresh_token")
    public BaseResponse<LoginVO> login(@Valid @RequestBody LoginDTO request, HttpServletRequest servletRequest) {
        // P1-3: 提取客户端 IP 和 User-Agent 传入 LoginDTO
        request.setLoginIp(extractClientIp(servletRequest));
        request.setUserAgent(servletRequest.getHeader("User-Agent"));
        LoginVO result = authService.login(request);
        return BaseResponse.success(result);
    }

    /**
     * 用户登出
     *
     * <p>将 access_token 加入 Redis 黑名单（TTL 与 token 剩余有效期对齐），
     * 同时清理服务端会话状态（缓存的用户权限 / 角色）。
     * <p>refresh_token 仍可使用一次以兼容客户端清理逻辑，业务方应在登出后主动丢弃 refresh_token。
     *
     * @param token Authorization 请求头（Bearer xxx 或裸 token）
     * @return 成功响应（无业务数据）
     */
    @Audit(module = "认证管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'用户登出'")
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "将 access_token 加入黑名单")
    public BaseResponse<Void> logout(@RequestHeader(HeaderConstants.AUTHORIZATION) String token) {
        String accessToken = token != null && token.startsWith("Bearer ")
                ? token.substring(7) : token;
        authService.logout(accessToken);
        return BaseResponse.success();
    }

    /**
     * 刷新 access_token
     *
     * <p>使用 refresh_token 换发新的 access_token（同时返回<b>新的</b> refresh_token 实现 token 轮换），
     * 旧 refresh_token 立即失效（一次性），防止 token 泄露后的长期滥用。
     * <p>启用分布式锁（{@code ydsz:userinfo:AuthController:refresh:lock}）防止并发重放。
     * <p>限流 50 QPS；5 秒幂等保护。
     *
     * @param request 刷新请求（含 refreshToken 字段）
     * @return 新的登录结果（accessToken / refreshToken / expiresIn / userInfo）
     */
    @Audit(module = "认证管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'刷新Token'")
    @RateLimit(resource = "userinfo.auth.refresh", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:AuthController:refresh:lock", ttlSeconds = 5)
    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 refresh_token 获取新的 access_token")
    public BaseResponse<LoginVO> refresh(@RequestBody RefreshRequest request) {
        LoginVO result = authService.refresh(request.getRefreshToken());
        return BaseResponse.success(result);
    }

    /**
     * 从 HttpServletRequest 中提取客户端真实 IP
     *
     * <p>优先读取 X-Forwarded-For、X-Real-IP、Proxy-Client-IP 等代理头，
     * 兜底使用 getRemoteAddr()。
     *
     * @param request HTTP 请求
     * @return 客户端真实 IP；无 IP 时为 null
     */
    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader(HeaderConstants.X_FORWARDED_FOR);
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理场景：取第一个非 unknown 的 IP
            int idx = ip.indexOf(',');
            return (idx > 0) ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 刷新 Token 请求体
     *
     * <p>封装 refreshToken 字段，避免与 {@link LoginDTO} 耦合。
     * 内部类，使用 Lombok {@code @Data} 自动生成 getter / setter / toString 等。
     */
    @Data
    public static class RefreshRequest {
        /** 刷新令牌（来自上一次登录或上一次 refresh 响应） */
        private String refreshToken;
    }
}
