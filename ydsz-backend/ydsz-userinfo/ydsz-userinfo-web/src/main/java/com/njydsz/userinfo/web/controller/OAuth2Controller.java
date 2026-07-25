package com.njydsz.userinfo.web.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 授权码模式 Controller。
 *
 * <p>实现标准 OAuth2 Authorization Code Grant 流程：
 * <ol>
 *   <li>GET /authorize — 签发授权码（5 分钟有效）</li>
 *   <li>POST /token — 用授权码换取 JWT access_token</li>
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

    private static final long CODE_TTL_SECONDS = 300;
    private static final String CODE_KEY_PREFIX = "oauth2:code:";
    private static final long TOKEN_TTL_SECONDS = 7200;

    @GetMapping("/authorize")
    @Operation(summary = "获取授权码", description = "生成 OAuth2 授权码，5 分钟有效")
    public BaseResponse<String> authorize(
            @RequestParam String clientId,
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String username) {

        String code = UUID.randomUUID().toString().replace("-", "");
        String storedValue = clientId + ":" + (userId != null ? userId : "")
                + ":" + (username != null ? username : "");
        redisStringOps.set(CODE_KEY_PREFIX + code, storedValue, CODE_TTL_SECONDS);
        log.info("OAuth2 authorize: clientId={}, code={}", clientId, code);
        return BaseResponse.success(code);
    }

    @PostMapping("/token")
    @Operation(summary = "用授权码换取 Token", description = "标准 OAuth2 token 端点，返回 JWT")
    public BaseResponse<Map<String, Object>> token(
            @RequestParam String code,
            @RequestParam String clientId,
            @RequestParam(required = false) String clientSecret) {

        String storedValue = redisStringOps.get(CODE_KEY_PREFIX + code, String.class);
        if (storedValue == null) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CODE_INVALID);
        }

        String[] parts = storedValue.split(":", 3);
        String storedClientId = parts[0];
        String userId = parts.length > 1 ? parts[1] : "";
        String username = parts.length > 2 ? parts[2] : "";

        if (!storedClientId.equals(clientId)) {
            throw new BusinessException(UserInfoResultCode.OAUTH2_CLIENT_INVALID);
        }

        redisStringOps.del(CODE_KEY_PREFIX + code);

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId.isEmpty() ? clientId : userId);
        userInfo.setUsername(username.isEmpty() ? clientId : username);
        userInfo.setTenantId("1");

        String accessToken = tokenService.issueAccessToken(userInfo);
        String refreshToken = tokenService.issueRefreshToken(userInfo);

        log.info("OAuth2 token issued: clientId={}, userId={}", clientId, userId);

        return BaseResponse.success(Map.of(
                "access_token", accessToken,
                "refresh_token", refreshToken,
                "token_type", "Bearer",
                "expires_in", TOKEN_TTL_SECONDS,
                "scope", "read write"
        ));
    }
}
