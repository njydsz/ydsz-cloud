package com.njydsz.pmis.common.auth.token;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.service.TokenBlacklistService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT Token 服务实现
 *
 * <p>基于 jjwt 库实现双令牌机制（access_token + refresh_token）：
 * <ul>
 *   <li>Access Token：短有效期，用于 API 访问授权</li>
 *   <li>Refresh Token：长有效期，用于刷新 Access Token</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>使用 HMAC-SHA256 签名算法</li>
 *   <li>Token 中包含用户基本信息（userId, username, tenantId）</li>
 *   <li>支持 Token 黑名单（登出后失效）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Service
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
@ConditionalOnProperty(prefix = "ydsz.auth.token", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JwtTokenService implements TokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLE_CODE = "roleCode";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final TokenProperties tokenProperties;
    private final SecretKey secretKey;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtTokenService(TokenProperties tokenProperties,
                           @Autowired(required = false) TokenBlacklistService tokenBlacklistService) {
        this.tokenProperties = tokenProperties;
        this.tokenBlacklistService = tokenBlacklistService;
        // 校验密钥非空，避免 NPE 或签名失败
        String secretKeyRaw = tokenProperties.getSecretKey();
        if (secretKeyRaw == null || secretKeyRaw.isBlank()) {
            throw new IllegalStateException("ydsz.auth.token.secret-key 不能为空，请在配置文件中设置 JWT 签名密钥");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretKeyRaw.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issueAccessToken(UserInfo userInfo) {
        return buildToken(userInfo, TOKEN_TYPE_ACCESS, tokenProperties.getAccessTokenExpireSeconds());
    }

    @Override
    public String issueRefreshToken(UserInfo userInfo) {
        return buildToken(userInfo, TOKEN_TYPE_REFRESH, tokenProperties.getRefreshTokenExpireSeconds());
    }

    @Override
    public boolean validateAccessToken(String token) {
        if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
            log.debug("Access token is blacklisted");
            return false;
        }
        return validateToken(token, TOKEN_TYPE_ACCESS);
    }

    @Override
    public boolean validateRefreshToken(String token) {
        if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
            log.debug("Refresh token is blacklisted");
            return false;
        }
        return validateToken(token, TOKEN_TYPE_REFRESH);
    }

    @Override
    public UserInfo parseAccessToken(String token) {
        return parseToken(token, TOKEN_TYPE_ACCESS);
    }

    @Override
    public UserInfo parseRefreshToken(String token) {
        return parseToken(token, TOKEN_TYPE_REFRESH);
    }

    @Override
    public String refreshAccessToken(String refreshToken) {
        if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(refreshToken)) {
            log.warn("Refresh token is blacklisted");
            return null;
        }
        // 获取分布式锁，防止并发刷新导致重放攻击
        if (tokenBlacklistService != null && !tokenBlacklistService.tryAcquireRefreshLock(refreshToken)) {
            log.warn("Refresh token 正在被其他请求刷新，拒绝并发刷新");
            return null;
        }
        try {
            // 再次检查黑名单，防止在获取锁的间隙被其他请求加入黑名单
            if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(refreshToken)) {
                log.warn("Refresh token was blacklisted during lock acquisition");
                return null;
            }
            UserInfo userInfo = parseRefreshToken(refreshToken);
            if (userInfo == null) {
                log.warn("Refresh token validation failed");
                return null;
            }
            String newAccessToken = issueAccessToken(userInfo);
            // 颁发新 token 后将旧 refresh_token 加入黑名单，防止 refresh_token 重放攻击
            if (tokenBlacklistService != null && newAccessToken != null) {
                tokenBlacklistService.addToBlacklist(refreshToken);
                log.debug("旧 refresh_token 已加入黑名单，防止重放");
            }
            return newAccessToken;
        } finally {
            // 释放分布式锁
            if (tokenBlacklistService != null) {
                tokenBlacklistService.releaseRefreshLock(refreshToken);
            }
        }
    }

    /**
     * 构建 JWT Token
     */
    private String buildToken(UserInfo userInfo, String tokenType, long expireSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userInfo.getUserId());
        claims.put(CLAIM_USERNAME, userInfo.getUsername());
        claims.put(CLAIM_TENANT_ID, userInfo.getTenantId());
        claims.put(CLAIM_ROLE_CODE, userInfo.getRoleCode());
        claims.put(CLAIM_TOKEN_TYPE, tokenType);

        return Jwts.builder()
                .claims(claims)
                .issuer(tokenProperties.getIssuer())
                .subject(tokenProperties.getSubject())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 验证 JWT Token
     */
    private boolean validateToken(String token, String expectedTokenType) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return expectedTokenType.equals(tokenType);
        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Token validation error", e);
            return false;
        }
    }

    /**
     * 解析 JWT Token 为用户信息
     */
    private UserInfo parseToken(String token, String expectedTokenType) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!expectedTokenType.equals(tokenType)) {
                log.warn("Token type mismatch: expected={}, actual={}", expectedTokenType, tokenType);
                return null;
            }

            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(claims.get(CLAIM_USER_ID, String.class));
            userInfo.setUsername(claims.get(CLAIM_USERNAME, String.class));
            userInfo.setTenantId(claims.get(CLAIM_TENANT_ID, String.class));
            userInfo.setRoleCode(claims.get(CLAIM_ROLE_CODE, String.class));
            return userInfo;
        } catch (JwtException e) {
            log.debug("Token parse failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Token parse error", e);
            return null;
        }
    }

    /**
     * 解析 JWT Claims
     *
     * <p>校验签名 + issuer + subject，防止跨服务令牌混淆攻击
     */
    private Claims parseClaims(String token) {
        // 快速格式校验：JWT 格式必须包含恰好两个 '.' 分隔符（header.payload.signature）
        // 避免非 JWT 格式的垃圾输入进入昂贵的签名验证流程
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is null or blank");
        }
        long dotCount = token.chars().filter(c -> c == '.').count();
        if (dotCount != 2) {
            throw new IllegalArgumentException("Invalid JWT format: expected 2 dots, found " + dotCount);
        }
        JwtParserBuilder parserBuilder = Jwts.parser()
                .verifyWith(secretKey);
        // 校验 issuer 防止跨服务令牌混淆
        String issuer = tokenProperties.getIssuer();
        if (issuer != null && !issuer.isBlank()) {
            parserBuilder.requireIssuer(issuer);
        }
        // 校验 subject 防止跨服务令牌混淆
        String subject = tokenProperties.getSubject();
        if (subject != null && !subject.isBlank()) {
            parserBuilder.requireSubject(subject);
        }
        return parserBuilder.build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
