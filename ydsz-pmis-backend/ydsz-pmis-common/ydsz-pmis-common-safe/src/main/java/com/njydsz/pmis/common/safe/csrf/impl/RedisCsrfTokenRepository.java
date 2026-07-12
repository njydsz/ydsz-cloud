package com.njydsz.pmis.common.safe.csrf.impl;

import com.njydsz.pmis.common.exception.custom.RemiSecurityException;
import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.csrf.CsrfToken;
import com.njydsz.pmis.common.safe.csrf.CsrfTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 基于 Redis 的 CSRF 令牌存储库（分布式环境推荐）
 *
 * <p>使用 Redis 存储 CSRF 令牌，支持分布式部署环境下的令牌共享。
 * 令牌过期由 Redis TTL 自动管理，无需手动清理。
 * 内置令牌生成逻辑，避免与 CsrfTokenGenerator 产生循环依赖。
 *
 * <p><b>Key 设计：</b>
 * <ul>
 *   <li>令牌存储：csrf:token:{tokenValue} -> CsrfToken JSON</li>
 *   <li>会话映射：csrf:session:{sessionId} -> tokenValue</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see CsrfTokenRepository
 */
public class RedisCsrfTokenRepository implements CsrfTokenRepository {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final String TOKEN_PREFIX = "csrf:token:";
    private static final String SESSION_PREFIX = "csrf:session:";

    private final long expirationSeconds;
    private final RedisService redisService;

    public RedisCsrfTokenRepository(long expirationSeconds, RedisService redisService) {
        this.expirationSeconds = expirationSeconds;
        this.redisService = redisService;
    }

    @Override
    public CsrfToken createToken(String sessionId) {
        String tokenValue = generateToken(sessionId);
        CsrfToken token = new CsrfToken(tokenValue, sessionId, expirationSeconds);

        String tokenKey = TOKEN_PREFIX + tokenValue;
        String sessionKey = SESSION_PREFIX + sessionId;

        redisService.set(tokenKey, token, expirationSeconds);
        redisService.set(sessionKey, tokenValue, expirationSeconds);

        return token;
    }

    @Override
    public CsrfToken getToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String tokenKey = TOKEN_PREFIX + token;
        return redisService.get(tokenKey, CsrfToken.class);
    }

    @Override
    public boolean validateToken(String token, String sessionId) {
        if (token == null || sessionId == null) {
            return false;
        }

        CsrfToken csrfToken = getToken(token);
        if (csrfToken == null) {
            return false;
        }

        if (csrfToken.isExpired()) {
            removeToken(token);
            return false;
        }

        return sessionId.equals(csrfToken.getSessionId());
    }

    @Override
    public void removeToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        CsrfToken csrfToken = getToken(token);
        if (csrfToken != null) {
            redisService.del(SESSION_PREFIX + csrfToken.getSessionId());
        }
        redisService.del(TOKEN_PREFIX + token);
    }

    @Override
    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String sessionKey = SESSION_PREFIX + sessionId;
        String tokenValue = redisService.get(sessionKey, String.class);
        if (tokenValue != null) {
            redisService.del(TOKEN_PREFIX + tokenValue);
        }
        redisService.del(sessionKey);
    }

    private String generateToken(String sessionId) {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);

        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String combined = sessionId + ":" + randomPart + ":" + System.currentTimeMillis();

        return sha256(combined);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RemiSecurityException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
