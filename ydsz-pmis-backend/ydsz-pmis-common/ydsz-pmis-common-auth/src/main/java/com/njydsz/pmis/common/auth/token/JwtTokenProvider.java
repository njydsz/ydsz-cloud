package com.njydsz.pmis.common.auth.token;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.auth.model.UserInfo;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT Token Provider（兼容旧 com.njydsz.pmis.common.token.JwtTokenProvider）。
 *
 * <p>提供旧版 API 接口，内部委托给 {@link JwtTokenService} 实现底层的 JWT 签发与验证。
 * 旧版 API 中的 roles/permissions/deptId 等扩展参数将存入 JWT claims，
 * 但不再由下游服务从 Token 中直接读取（改为通过网关 Header 透传）。
 *
 * @since 1.0.0
 */
@Service
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
@ConditionalOnProperty(prefix = "ydsz.auth.token", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtTokenService jwtTokenService;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtTokenService jwtTokenService,
                            TokenProperties tokenProperties) {
        this.jwtTokenService = jwtTokenService;
        String secretKeyRaw = tokenProperties.getSecretKey();
        if (secretKeyRaw == null || secretKeyRaw.isBlank()) {
            throw new IllegalStateException("ydsz.auth.token.secret-key 不能为空");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretKeyRaw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问令牌（兼容旧 API，多参数版本）。
     *
     * <p>将 userId/username/roles 等信息封装为 {@link UserInfo}，
     * 委托给 {@link JwtTokenService#issueAccessToken} 签发。
     * roles/permissions/deptId 等扩展信息不再写入 JWT claims，
     * 由网关通过 HTTP Header 透传给下游服务。
     *
     * @param userId       用户 ID
     * @param username     用户名
     * @param roles        角色列表（兼容参数，不再写入 Token）
     * @param permissions  权限列表（兼容参数，不再写入 Token）
     * @param deptId       部门 ID（兼容参数）
     * @param deptIds      部门 ID 列表（兼容参数）
     * @param customDeptIds 自定义部门 ID 列表（兼容参数）
     * @param dataScope    数据权限范围（兼容参数）
     * @param expireSeconds 过期时间（秒）
     * @return JWT 访问令牌
     */
    public String generateToken(String userId, String username,
                                List<String> roles, List<String> permissions,
                                String deptId, List<String> deptIds, List<String> customDeptIds,
                                String dataScope,
                                long expireSeconds) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername(username);
        userInfo.setRoleCode(roles != null ? String.join(",", roles) : null);
        return jwtTokenService.issueAccessToken(userInfo);
    }

    /**
     * 生成刷新令牌（兼容旧 API）。
     *
     * @param userId        用户 ID
     * @param expireSeconds 过期时间（秒）
     * @return JWT 刷新令牌
     */
    public String generateRefreshToken(String userId, long expireSeconds) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        return jwtTokenService.issueRefreshToken(userInfo);
    }

    /**
     * 验证令牌（兼容旧 API，不区分 access/refresh）。
     *
     * @param token JWT 令牌
     * @return 有效返回 true
     */
    public boolean validateToken(String token) {
        return jwtTokenService.validateAccessToken(token)
                || jwtTokenService.validateRefreshToken(token);
    }

    /**
     * 从令牌中获取用户 ID（兼容旧 API）。
     *
     * @param token JWT 令牌
     * @return 用户 ID，解析失败返回 null
     */
    public String getUserId(String token) {
        UserInfo userInfo = jwtTokenService.parseAccessToken(token);
        if (userInfo != null) {
            return userInfo.getUserId();
        }
        userInfo = jwtTokenService.parseRefreshToken(token);
        return userInfo != null ? userInfo.getUserId() : null;
    }

    /**
     * 从令牌中获取用户名（兼容旧 API）。
     *
     * @param token JWT 令牌
     * @return 用户名，解析失败返回 null
     */
    public String getUsername(String token) {
        UserInfo userInfo = jwtTokenService.parseAccessToken(token);
        if (userInfo != null) {
            return userInfo.getUsername();
        }
        userInfo = jwtTokenService.parseRefreshToken(token);
        return userInfo != null ? userInfo.getUsername() : null;
    }

    /**
     * 获取令牌剩余有效时间（秒）。
     *
     * @param token JWT 令牌
     * @return 剩余秒数，解析失败返回 0
     */
    public long getRemainingExpirationSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            if (expiration == null) {
                return 0;
            }
            long remainingMs = expiration.getTime() - System.currentTimeMillis();
            return remainingMs > 0 ? remainingMs / 1000 : 0;
        } catch (Exception e) {
            log.debug("获取 Token 剩余有效时间失败: {}", e.getMessage());
            return 0;
        }
    }
}
