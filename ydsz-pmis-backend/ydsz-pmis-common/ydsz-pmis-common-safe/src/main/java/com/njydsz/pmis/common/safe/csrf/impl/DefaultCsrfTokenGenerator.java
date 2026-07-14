package com.njydsz.pmis.common.safe.csrf.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import com.njydsz.pmis.common.exception.custom.YdszSecurityException;
import com.njydsz.pmis.common.safe.csrf.CsrfToken;
import com.njydsz.pmis.common.safe.csrf.CsrfTokenGenerator;
import com.njydsz.pmis.common.safe.csrf.CsrfTokenRepository;

/**
 * 默认 CSRF 令牌生成器
 *
 * <p>基于 SecureRandom + SHA-256 实现安全的令牌生成。
 * 验证时需要配合 {@link CsrfTokenRepository} 校验令牌是否存在于存储中。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see CsrfTokenGenerator
 * @see CsrfTokenRepository
 */
public class DefaultCsrfTokenGenerator implements CsrfTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int SHA256_HEX_LENGTH = 64;

    private final CsrfTokenRepository csrfTokenRepository;

    public DefaultCsrfTokenGenerator(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @Override
    public String generate(String sessionId) {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);

        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String combined = sessionId + ":" + randomPart + ":" + System.currentTimeMillis();

        return sha256(combined);
    }

    @Override
    public boolean validate(String token, String sessionId) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (token.length() != SHA256_HEX_LENGTH) {
            return false;
        }
        CsrfToken storedToken = csrfTokenRepository.getToken(token);
        if (storedToken == null) {
            return false;
        }
        return sessionId != null && sessionId.equals(storedToken.getSessionId());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new YdszSecurityException("SHA-256 algorithm not available", e);
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
