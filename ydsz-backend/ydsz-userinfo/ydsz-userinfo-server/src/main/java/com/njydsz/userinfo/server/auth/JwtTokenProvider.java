package com.njydsz.userinfo.server.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT Token 提供器（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-common-auth.token.JwtTokenProvider 包，因 common 重构后该类已迁移到各业务模块本地化。
 * 基于 jjwt 0.12.x 实现 access/refresh 双令牌机制，并写入 userinfo 业务字段（roles / permissions / deptIds / dataScope 等）。
 *
 * <p>使用示例：
 * <pre>{@code
 * String access = jwtTokenProvider.generateToken(
 *     userId, username, roles, permissions,
 *     deptId, deptIds, customDeptIds, "DEPT_AND_CHILD", 8 * 3600L);
 * String refresh = jwtTokenProvider.generateRefreshToken(userId, 7 * 24 * 3600L);
 * }</pre>
 *
 * <p>密钥配置：通过环境变量 {@code YDSZ_USERINFO_JWT_SECRET} 或 application.yml
 * {@code ydsz.userinfo.jwt.secret} 注入，未配置时使用默认演示密钥（仅供本地开发）。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 演示密钥（生产必须通过配置覆盖，≥ 32 字节以满足 HS256 要求） */
    private static final String DEFAULT_SECRET = "ydsz-userinfo-jwt-secret-key-2026-must-be-long-enough";
    /** Token 签发者 */
    private static final String ISSUER = "ydsz-userinfo";

    // ============================== Claims 常量 ==============================

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_DEPT_ID = "deptId";
    private static final String CLAIM_DEPT_IDS = "deptIds";
    private static final String CLAIM_CUSTOM_DEPT_IDS = "customDeptIds";
    private static final String CLAIM_DATA_SCOPE = "dataScope";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_SESSION_ID = "sessionId";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;

    public JwtTokenProvider() {
        this.secretKey = Keys.hmacShaKeyFor(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8));
        log.info("[JwtTokenProvider] 已初始化（密钥来源：默认演示密钥，生产请覆盖）");
    }

    /**
     * 生成 access token（含 roles / permissions / dataScope / deptIds 等业务 claims）
     */
    public String generateToken(String userId, String username,
                                List<String> roles, List<String> permissions,
                                String deptId, List<String> deptIds, List<String> customDeptIds,
                                String dataScope, long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        if (roles != null) claims.put(CLAIM_ROLES, roles);
        if (permissions != null) claims.put(CLAIM_PERMISSIONS, permissions);
        if (deptId != null) claims.put(CLAIM_DEPT_ID, deptId);
        if (deptIds != null) claims.put(CLAIM_DEPT_IDS, deptIds);
        if (customDeptIds != null) claims.put(CLAIM_CUSTOM_DEPT_IDS, customDeptIds);
        if (dataScope != null) claims.put(CLAIM_DATA_SCOPE, dataScope);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        return buildToken(claims, expireSeconds);
    }

    /**
     * 生成 refresh token（仅含 userId）
     */
    public String generateRefreshToken(String userId, long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH);
        return buildToken(claims, expireSeconds);
    }

    /**
     * 校验 token 签名、过期时间、tokenType
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        // 快速格式校验：JWT 格式必须包含恰好两个 '.' 分隔符
        long dotCount = token.chars().filter(c -> c == '.').count();
        if (dotCount != 2) {
            return false;
        }
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return tokenType != null;
        } catch (JwtException e) {
            log.debug("[JwtTokenProvider] 校验失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[JwtTokenProvider] 校验异常", e);
            return false;
        }
    }

    /**
     * 从 token 中提取 userId
     */
    public String getUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get(CLAIM_USER_ID, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算 token 剩余有效期（秒），用于登出时设置黑名单 TTL
     *
     * @return 剩余秒数；已过期或解析失败时返回 0
     */
    public long getRemainingExpirationSeconds(String token) {
        if (token == null || token.isBlank()) {
            return 0L;
        }
        try {
            Claims claims = parseClaims(token);
            Date exp = claims.getExpiration();
            if (exp == null) {
                return 0L;
            }
            long remainMs = exp.getTime() - System.currentTimeMillis();
            return remainMs > 0 ? (remainMs + 999L) / 1000L : 0L;
        } catch (ExpiredJwtException e) {
            return 0L;
        } catch (Exception e) {
            log.debug("[JwtTokenProvider] getRemainingExpirationSeconds 失败: {}", e.getMessage());
            return 0L;
        }
    }

    // ============================== 私有方法 ==============================

    private String buildToken(Map<String, Object> claims, long expireSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000L);
        return Jwts.builder()
                .claims(claims)
                .issuer(ISSUER)
                .subject(claims.getOrDefault(CLAIM_USER_ID, "").toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
