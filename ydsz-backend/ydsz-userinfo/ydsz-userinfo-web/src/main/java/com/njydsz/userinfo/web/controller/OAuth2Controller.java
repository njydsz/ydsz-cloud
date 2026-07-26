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
        if (!properties.getOauth2Clients().containsKey(clientId)) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CLIENT_INVALID);
        }

        // 使用 JSON 存储授权码上下文（替代字符串拼接，避免分隔符冲突）
        String code = UUID.randomUUID().toString().replace("-", "");
        String context = buildCodeContext(clientId, userInfo.getUserId(), userInfo.getUsername());
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

        // 解析 JSON 上下文
        Map<String, String> context = parseCodeContext(storedContext);
        String storedClientId = context.get("clientId");
        String userId = context.get("userId");
        String username = context.get("username");

        if (!clientId.equals(storedClientId)) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CLIENT_INVALID);
        }

        // 授权码一次性使用
        redisStringOps.del(CODE_KEY_PREFIX + code);

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername(username);
        userInfo.setTenantId("1");

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

    /**
     * 构建授权码 JSON 上下文（替代字符串拼接）。
     */
    private String buildCodeContext(String clientId, String userId, String username) {
        return "{\"clientId\":\"" + escapeJson(clientId) + "\""
                + ",\"userId\":\"" + escapeJson(userId != null ? userId : "") + "\""
                + ",\"username\":\"" + escapeJson(username != null ? username : "") + "\"}";
    }

    /**
     * 解析授权码 JSON 上下文。
     */
    private Map<String, String> parseCodeContext(String json) {
        try {
            return parseSimpleJson(json);
        } catch (Exception e) {
            log.error("Failed to parse OAuth2 code context", e);
            throw new BusinessException(UserInfoResultCode.OAUTH2_CODE_INVALID);
        }
    }

    /**
     * 简单 JSON 解析（避免引入额外依赖，格式固定为 {"k":"v","k":"v"}）。
     */
    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        String[] pairs = json.split("\",\"");
        for (String pair : pairs) {
            int colon = pair.indexOf("\":\"");
            if (colon > 0) {
                String key = pair.substring(0, colon).replace("\"", "").trim();
                String value = pair.substring(colon + 2);
                if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
                result.put(key, unescapeJson(value));
            }
        }
        return result;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
