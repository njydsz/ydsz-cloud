package com.njydsz.userinfo.web.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.server.config.UserInfoProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 授权码模式 Controller。
 *
 * <p>实现标准 OAuth2 Authorization Code Grant 流程：
 * <ol>
 *   <li>GET /authorize — 需携带已登录的 access_token，签发授权码（5 分钟有效）</li>
 *   <li>POST /token — 用授权码 + clientSecret 换取 JWT access_token</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "OAuth2 授权码模式")
public class OAuth2Controller {

    private final RedisStringOps redisStringOps;
    private final TokenService tokenService;
    private final UserInfoProperties properties;

    private static final long CODE_TTL_SECONDS = 300;
    private static final String CODE_KEY_PREFIX = "oauth2:code:";

    @GetMapping("/authorize")
    @Operation(summary = "获取授权码", description = "需携带已登录的 access_token，生成 OAuth2 授权码，5 分钟有效")
    public BaseResponse<String> authorize(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String clientId,
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state) {

        // 认证检查：必须携带有效的 access_token
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(UserInfoResultCode.TOKEN_INVALID);
        }
        String accessToken = authorization.substring(7);
        UserInfo userInfo = tokenService.parseAccessToken(accessToken);
        if (userInfo == null || !tokenService.validateAccessToken(accessToken)) {
            throw new BusinessException(UserInfoResultCode.TOKEN_INVALID);
        }

        // 验证 clientId 是否已注册
        UserInfoProperties.OAuth2Client clientConfig = properties.getOauth2Clients().get(clientId);
        if (clientConfig == null) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CLIENT_INVALID);
        }

        // 校验 redirect_uri 在客户端注册白名单中（RFC 6749 §3.1.2.3）
        if (clientConfig.getRedirectUris() != null
                && !clientConfig.getRedirectUris().isEmpty()
                && !clientConfig.getRedirectUris().contains(redirectUri)) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_REDIRECT_URI_MISMATCH);
        }

        // 使用 YdszJson 序列化授权码上下文（含 tenantId）
        String code = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> contextMap = new HashMap<>();
        contextMap.put("clientId", clientId);
        contextMap.put("userId", userInfo.getUserId());
        contextMap.put("username", userInfo.getUsername());
        contextMap.put("tenantId", userInfo.getTenantId() != null ? userInfo.getTenantId() : "1");
        contextMap.put("redirectUri", redirectUri);
        String context = YdszJson.toJson(contextMap);
        redisStringOps.set(CODE_KEY_PREFIX + code, context, CODE_TTL_SECONDS);
        log.info("OAuth2 authorize: clientId={}, userId={}, code={}", clientId, userInfo.getUserId(), code);
        return BaseResponse.success(code);
    }

    @PostMapping("/token")
    @Operation(summary = "用授权码换取 Token", description = "标准 OAuth2 token 端点，需校验 clientSecret")
    public BaseResponse<Map<String, Object>> token(
            @RequestParam String code,
            @RequestParam String clientId,
            @RequestParam String clientSecret) {

        // 强制校验 clientSecret
        if (!properties.validateOAuth2Client(clientId, clientSecret)) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CLIENT_INVALID);
        }

        String storedContext = redisStringOps.get(CODE_KEY_PREFIX + code, String.class);
        if (storedContext == null) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CODE_INVALID);
        }

        // 使用 YdszJson 解析上下文
        Map<String, Object> context;
        try {
            context = YdszJson.parseMap(storedContext);
        } catch (Exception e) {
            log.error("Failed to parse OAuth2 code context", e);
            throw new BusinessException(UserInfoResultCode.OAUTH2_CODE_INVALID);
        }

        String storedClientId = getString(context, "clientId");
        String userId = getString(context, "userId");
        String username = getString(context, "username");
        String tenantId = getString(context, "tenantId");

        if (!clientId.equals(storedClientId)) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CLIENT_INVALID);
        }

        // 授权码一次性使用
        redisStringOps.del(CODE_KEY_PREFIX + code);

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername(username);
        userInfo.setTenantId(tenantId != null ? tenantId : "1");

        String newAccessToken = tokenService.issueAccessToken(userInfo);
        String refreshToken = tokenService.issueRefreshToken(userInfo);

        log.info("OAuth2 token issued: clientId={}, userId={}", clientId, userId);

        return BaseResponse.success(Map.of(
                "access_token", newAccessToken,
                "refresh_token", refreshToken,
                "token_type", "Bearer",
                "expires_in", properties.getTokenTtlSeconds(),
                "scope", "read write"
        ));
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
