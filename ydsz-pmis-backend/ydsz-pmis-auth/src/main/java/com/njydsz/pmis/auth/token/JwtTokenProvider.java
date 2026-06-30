package com.njydsz.pmis.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token 工具
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${pmis.jwt.secret:pmis-default-jwt-secret-key-please-change-in-production-environment-must-be-256-bits}")
    private String secret;

    @Value("${pmis.jwt.issuer:pmis}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成访问 Token
     */
    public String generateToken(Long userId, String username, long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");

        Date now = new Date();
        Date expire = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    /**
     * 生成刷新 Token
     */
    public String generateRefreshToken(Long userId, long expireSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        Date now = new Date();
        Date expire = new Date(now.getTime() + expireSeconds * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("[JWT] Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Claims
     */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }
}
