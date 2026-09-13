package com.njydsz.common.safe.csrf.impl;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.safe.cache.ConcurrentTtlSafeCache;
import com.njydsz.common.safe.cache.SafeCache;
import com.njydsz.common.safe.csrf.CsrfToken;
import com.njydsz.common.safe.csrf.CsrfTokenRepository;
import com.njydsz.common.util.security.DigestUtils;

/**
 * 基于内存的 CSRF 令牌存储库
 *
 * <p>使用 SafeCache 抽象管理令牌过期，ConcurrentHashMap 存储会话与令牌映射。 内置令牌生成逻辑，避免与 CsrfTokenGenerator 产生循环依赖。
 *
 * <p>当 ydzs-common-cache 在 classpath 时，底层使用 Caffeine 语义的缓存实现；
 * 否则退化为 ConcurrentHashMap + TTL 的兜底实现。
 *
 * <p><b>注意：</b>此实现适用于单机部署。分布式环境下建议使用 Redis 实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CsrfTokenRepository
 */
public class InMemoryCsrfTokenRepository implements CsrfTokenRepository {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryCsrfTokenRepository.class);

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final long expirationSeconds;
  private final SafeCache<String, CsrfToken> tokenCache;
  private final ConcurrentMap<String, String> sessionTokenMap;

  /**
   * 构造基于内存的 CSRF 令牌存储库（兜底模式）。
   *
   * <p>底层使用 ConcurrentTtlSafeCache，适用于 ydzs-common-cache 不可用或无 Spring 容器的场景。
   *
   * @param expirationSeconds 令牌过期时间（秒）
   */
  public InMemoryCsrfTokenRepository(long expirationSeconds) {
    this.expirationSeconds = expirationSeconds;
    this.tokenCache = new ConcurrentTtlSafeCache<>(
        expirationSeconds * 2L, TimeUnit.SECONDS);
    this.sessionTokenMap = new ConcurrentHashMap<>();
    LOG.info("InMemoryCsrfTokenRepository 已初始化(兜底模式): expiration={}s", expirationSeconds);
  }

  /**
   * 构造基于内存的 CSRF 令牌存储库（托管模式）。
   *
   * <p>接受外部注入的 SafeCache 实现，由 Spring 容器根据 ydzs-common-cache 可用性选择合适的底层实现。
   *
   * @param expirationSeconds 令牌过期时间（秒）
   * @param tokenCache 令牌缓存实现
   */
  public InMemoryCsrfTokenRepository(long expirationSeconds, SafeCache<String, CsrfToken> tokenCache) {
    this.expirationSeconds = expirationSeconds;
    this.tokenCache = tokenCache;
    this.sessionTokenMap = new ConcurrentHashMap<>();
    LOG.info("InMemoryCsrfTokenRepository 已初始化(托管模式): expiration={}s, cacheClass={}",
        expirationSeconds, tokenCache.getClass().getSimpleName());
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
    // 云顶规范 §22.5：复用 common-util 的 DigestUtils，禁止自建哈希
    return DigestUtils.sha256Hex(input);
  }
}
