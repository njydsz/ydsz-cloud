package com.njydsz.common.safe.csrf.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.csrf.CsrfToken;
import com.njydsz.common.safe.csrf.CsrfTokenRepository;

/**
 * 基于内存的 CSRF 令牌存储库
 *
 * <p>使用 ydsz-common-cache 缓存管理令牌过期，ConcurrentHashMap 存储会话与令牌映射。 内置令牌生成逻辑，避免与 CsrfTokenGenerator
 * 产生循环依赖。
 *
 * <p><b>注意：</b>此实现适用于单机部署。分布式环境下建议使用 Redis 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CsrfTokenRepository
 */
public class InMemoryCsrfTokenRepository implements CsrfTokenRepository {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final long expirationSeconds;
  private final Cache<String, CsrfToken> tokenCache;
  private final ConcurrentMap<String, String> sessionTokenMap;

  public InMemoryCsrfTokenRepository(long expirationSeconds) {
    this.expirationSeconds = expirationSeconds;
    this.tokenCache =
        YdszCache.<String, CsrfToken>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(expirationSeconds * 2L, TimeUnit.SECONDS)
            .build();
    this.sessionTokenMap = new ConcurrentHashMap<>();
  }

  @Override
  public CsrfToken createToken(String sessionId) {
    String tokenValue = generateToken(sessionId);
    CsrfToken token = new CsrfToken(tokenValue, sessionId, expirationSeconds);

    tokenCache.put(tokenValue, token);
    sessionTokenMap.put(sessionId, tokenValue);

    return token;
  }

  @Override
  public CsrfToken getToken(String token) {
    return tokenCache.getIfPresent(token);
  }

  @Override
  public boolean validateToken(String token, String sessionId) {
    if (token == null || sessionId == null) {
      return false;
    }

    CsrfToken csrfToken = tokenCache.getIfPresent(token);
    if (csrfToken == null) {
      return false;
    }

    if (csrfToken.isExpired()) {
      removeToken(token);
      return false;
    }

    return csrfToken.getSessionId().equals(sessionId);
  }

  @Override
  public void removeToken(String token) {
    CsrfToken csrfToken = tokenCache.getIfPresent(token);
    if (csrfToken != null) {
      sessionTokenMap.remove(csrfToken.getSessionId());
    }
    tokenCache.invalidate(token);
  }

  @Override
  public void clearSession(String sessionId) {
    String token = sessionTokenMap.remove(sessionId);
    if (token != null) {
      tokenCache.invalidate(token);
    }
  }

  /**
   * 生成 CSRF 令牌
   *
   * <p>基于 SecureRandom + SHA-256 实现，避免与 CsrfTokenGenerator 循环依赖。
   *
   * @param sessionId 会话 ID
   * @return CSRF 令牌
   */
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
      throw BusinessException.builder()
          .code(CoreExceptionCode.FAIL.getCode())
          .message("SHA-256 algorithm not available")
          .cause(e)
          .build();
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
