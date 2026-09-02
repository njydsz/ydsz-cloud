package com.njydsz.userinfo.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.userinfo.server.config.RememberMeProperties;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * Remember-Me 服务。
 *
 * <p>封装「记住我」功能的核心能力：Remember-Me Cookie 签发/读取/清除、滑动续期判断与执行。
 * 与 {@link SessionManager} 协同，实现 Token TTL 的滑动过期。
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>Cookie 存储的是用户 ID 的 AES-GCM 加密结果（含随机 IV），不存储明文</li>
 *   <li>Cookie 设置 HttpOnly + Secure + SameSite=Lax，防止 XSS 窃取和 CSRF 滥用</li>
 *   <li>滑动续期受 {@code maxExtendDays} 限制，防止无限期延长</li>
 *   <li>续期操作记录审计日志（含 userId、操作时间、续期次数）</li>
 * </ul>
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:remember-me:{userId}  →  Hash  续期审计信息（lastExtendTime、extendCount、firstLoginTime）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RememberMeProperties Remember-Me 配置
 * @see com.njydsz.userinfo.web.filter.RememberMeFilter Remember-Me 过滤器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RememberMeService {
  /** 每天的秒数（1 天 = 86400 秒） */
  private static final int SECONDS_PER_DAY = 86400;

  /** AES 密钥字节长度（256 位） */
  private static final int AES_KEY_LENGTH = 32;


  /** Remember-Me 审计信息 Redis Key 前缀 */
  private static final String REMEMBER_ME_KEY_PREFIX = "userinfo:remember-me:";

  /** 审计 Hash 字段：上次续期时间（Unix 秒） */
  private static final String FIELD_LAST_EXTEND_TIME = "lastExtendTime";

  /** 审计 Hash 字段：续期次数 */
  private static final String FIELD_EXTEND_COUNT = "extendCount";

  /** 审计 Hash 字段：首次登录时间（Unix 秒） */
  private static final String FIELD_FIRST_LOGIN_TIME = "firstLoginTime";

  /** AES-GCM IV 长度（字节） */
  private static final int GCM_IV_LENGTH = 12;

  /** AES-GCM Tag 长度（位） */
  private static final int GCM_TAG_LENGTH = 128;

  private final RememberMeProperties rememberMeProperties;
  private final UserInfoProperties userInfoProperties;
  private final SessionManager sessionManager;
  private final RedisStringOps redisStringOps;
  private final RedisHashOps redisHashOps;

  /**
   * 签发 Remember-Me Cookie。
   *
   * <p>在响应中设置包含 AES-GCM 加密用户 ID 的 Cookie，用于后续自动登录和滑动续期。
   * 同时在 Redis 中记录首次登录时间，作为最大续期天数的计算基准。
   *
   * @param response HTTP 响应，不可为 null
   * @param userId 用户 ID，不可为 null 或空
   */
  public void issueRememberMeCookie(HttpServletResponse response, String userId) {
    if (response == null || userId == null || userId.isBlank()) {
      return;
    }
    try {
      String encryptedUserId = encryptUserId(userId);
      Cookie cookie = new Cookie(rememberMeProperties.getCookieName(), encryptedUserId);
      cookie.setPath("/");
      cookie.setHttpOnly(rememberMeProperties.isCookieHttpOnly());
      cookie.setSecure(rememberMeProperties.isCookieSecure());
      cookie.setMaxAge(rememberMeProperties.getCookieMaxAge());
      cookie.setAttribute("SameSite", rememberMeProperties.getCookieSameSite());
      response.addCookie(cookie);

      // 记录首次登录时间（用于 maxExtendDays 限制）
      String auditKey = buildAuditKey(userId);
      long now = Instant.now().getEpochSecond();
      redisHashOps.hSetIfAbsent(auditKey, FIELD_FIRST_LOGIN_TIME, String.valueOf(now));
      redisHashOps.hSet(auditKey, FIELD_LAST_EXTEND_TIME, String.valueOf(now));
      redisHashOps.hSetIfAbsent(auditKey, FIELD_EXTEND_COUNT, "0");
      redisStringOps.expire(auditKey, Duration.ofSeconds(rememberMeProperties.getCookieMaxAge()));

      log.info("Remember-Me cookie issued for userId={}, maxAge={}",
          userId, rememberMeProperties.getCookieMaxAge());
    } catch (Exception e) {
      log.error("Failed to issue Remember-Me cookie for userId={}", userId, e);
    }
  }

  /**
   * 从 Cookie 中读取并解密用户 ID。
   *
   * <p>解析 Cookie 值，使用 AES-GCM 解密获取用户 ID。
   *
   * @param request HTTP 请求
   * @return 解密后的用户 ID，Cookie 不存在或解密失败时返回 null
   */
  public String resolveUserIdFromCookie(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (rememberMeProperties.getCookieName().equals(cookie.getName())) {
        String value = cookie.getValue();
        if (value != null && !value.isBlank()) {
          try {
            return decryptUserId(value);
          } catch (Exception e) {
            log.warn("Failed to decrypt Remember-Me cookie: {}", e.getMessage());
            return null;
          }
        }
      }
    }
    return null;
  }

  /**
   * 清除 Remember-Me Cookie。
   *
   * <p>将 Cookie 的 maxAge 设为 0，使浏览器立即删除该 Cookie。
   *
   * @param response HTTP 响应，不可为 null
   */
  public void clearRememberMeCookie(HttpServletResponse response) {
    if (response == null) {
      return;
    }
    Cookie cookie = new Cookie(rememberMeProperties.getCookieName(), "");
    cookie.setPath("/");
    cookie.setHttpOnly(rememberMeProperties.isCookieHttpOnly());
    cookie.setSecure(rememberMeProperties.isCookieSecure());
    cookie.setMaxAge(0);
    cookie.setAttribute("SameSite", rememberMeProperties.getCookieSameSite());
    response.addCookie(cookie);
    log.debug("Remember-Me cookie cleared");
  }

  /**
   * 判断当前会话是否需要滑动续期。
   *
   * <p>检查条件：
   *
   * <ol>
   *   <li>会话已标记为 rememberMe</li>
   *   <li>距上次续期超过 {@code slidingWindowSeconds}</li>
   *   <li>从首次登录起未超过 {@code maxExtendDays}</li>
   * </ol>
   *
   * @param accessToken 访问令牌
   * @return true 表示需要滑动续期
   */
  public boolean shouldSlidingExtend(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return false;
    }
    // 检查会话是否标记为 rememberMe
    String rememberMeFlag = redisHashOps.hGet(accessToken, "rememberMe", String.class);
    if (!"true".equals(rememberMeFlag)) {
      return false;
    }
    String userId = redisHashOps.hGet(accessToken, "userId", String.class);
    if (userId == null || userId.isBlank()) {
      return false;
    }
    String auditKey = buildAuditKey(userId);
    String lastExtendTimeStr = redisHashOps.hGet(auditKey, FIELD_LAST_EXTEND_TIME, String.class);
    String firstLoginTimeStr = redisHashOps.hGet(auditKey, FIELD_FIRST_LOGIN_TIME, String.class);

    long now = Instant.now().getEpochSecond();
    if (isWithinSlidingWindow(now, lastExtendTimeStr, userId)) {
      return false;
    }
    if (isMaxExtendReached(now, firstLoginTimeStr, userId)) {
      return false;
    }
    return true;
  }

  /**
   * 检查是否仍处于滑动续期窗口内。
   *
   * @param now 当前时间（秒）
   * @param lastExtendTimeStr 上次续期时间字符串（可为 null）
   * @param userId 用户 ID（日志用）
   * @return true 表示仍在窗口内，无需续期
   */
  private boolean isWithinSlidingWindow(long now, String lastExtendTimeStr, String userId) {
    if (lastExtendTimeStr == null || lastExtendTimeStr.isBlank()) {
      return false;
    }
    try {
      long lastExtendTime = Long.parseLong(lastExtendTimeStr);
      return now - lastExtendTime < rememberMeProperties.getSlidingWindowSeconds();
    } catch (NumberFormatException e) {
      log.warn("Invalid lastExtendTime format for userId={}", userId);
      return false;
    }
  }

  /**
   * 检查是否已达到最大续期天数。
   *
   * @param now 当前时间（秒）
   * @param firstLoginTimeStr 首次登录时间字符串（可为 null）
   * @param userId 用户 ID（日志用）
   * @return true 表示已超过最大续期期限
   */
  private boolean isMaxExtendReached(long now, String firstLoginTimeStr, String userId) {
    if (firstLoginTimeStr == null || firstLoginTimeStr.isBlank()) {
      return false;
    }
    try {
      long firstLoginTime = Long.parseLong(firstLoginTimeStr);
      long maxExtendSeconds = (long) rememberMeProperties.getMaxExtendDays() * SECONDS_PER_DAY;
      if (now - firstLoginTime >= maxExtendSeconds) {
        log.info("Remember-Me max extend days reached for userId={}, firstLoginTime={}",
            userId, firstLoginTime);
        return true;
      }
      return false;
    } catch (NumberFormatException e) {
      log.warn("Invalid firstLoginTime format for userId={}", userId);
      return false;
    }
  }

  /**
   * 执行滑动续期。
   *
   * <p>延长 access_token 对应会话的 Redis TTL，更新续期审计信息。
   *
   * @param accessToken 访问令牌
   */
  public void extendSession(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return;
    }
    String userId = redisHashOps.hGet(accessToken, "userId", String.class);
    if (userId == null || userId.isBlank()) {
      return;
    }
    // 延长会话 TTL
    long ttlSeconds = userInfoProperties.getTokenTtlSeconds();
    sessionManager.extendSession(accessToken, ttlSeconds);

    // 更新审计信息
    String auditKey = buildAuditKey(userId);
    long now = Instant.now().getEpochSecond();
    redisHashOps.hSet(auditKey, FIELD_LAST_EXTEND_TIME, String.valueOf(now));
    String extendCountStr = redisHashOps.hGet(auditKey, FIELD_EXTEND_COUNT, String.class);
    long extendCount = 0;
    if (extendCountStr != null && !extendCountStr.isBlank()) {
      try {
        extendCount = Long.parseLong(extendCountStr);
      } catch (NumberFormatException e) {
        log.warn("Invalid extendCount format for userId={}", userId);
      }
    }
    redisHashOps.hSet(auditKey, FIELD_EXTEND_COUNT, String.valueOf(extendCount + 1));
    redisStringOps.expire(auditKey, Duration.ofSeconds(rememberMeProperties.getCookieMaxAge()));

    log.info("Remember-Me session extended for userId={}, extendCount={}, ttlSeconds={}",
        userId, extendCount + 1, ttlSeconds);
  }

  /**
   * 登录成功后的 Remember-Me 处理。
   *
   * <p>如果用户勾选了「记住我」，签发 Remember-Me Cookie。
   *
   * @param response HTTP 响应，可为 null
   * @param userId 用户 ID
   * @param rememberMe 是否开启 Remember-Me
   */
  public void onLoginSuccess(HttpServletResponse response, String userId, boolean rememberMe) {
    if (!rememberMe) {
      log.debug("Remember-Me not requested for userId={}", userId);
      return;
    }
    if (!rememberMeProperties.isEnabled()) {
      log.debug("Remember-Me disabled, skipping for userId={}", userId);
      return;
    }
    issueRememberMeCookie(response, userId);
  }

  /**
   * 登出处理：清除 Remember-Me Cookie 和审计信息。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   */
  public void onLogout(HttpServletRequest request, HttpServletResponse response) {
    if (request == null || response == null) {
      return;
    }
    String userId = resolveUserIdFromCookie(request);
    clearRememberMeCookie(response);
    if (userId != null && !userId.isBlank()) {
      String auditKey = buildAuditKey(userId);
      redisStringOps.del(auditKey);
      log.info("Remember-Me audit info cleared for userId={}", userId);
    }
  }

  /**
   * 使用 AES-GCM 加密用户 ID。
   *
   * <p>输出格式：Base64(IV + ciphertext + authTag)，IV 为 12 字节随机值。
   *
   * @param userId 用户 ID
   * @return Base64 编码的加密结果
   * @throws GeneralSecurityException 加密失败时抛出
   */
  private String encryptUserId(String userId) throws GeneralSecurityException {
    byte[] iv = new byte[GCM_IV_LENGTH];
    SecureRandom.getInstanceStrong().nextBytes(iv);

    SecretKeySpec keySpec = new SecretKeySpec(deriveKey(), "AES");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

    byte[] ciphertext = cipher.doFinal(userId.getBytes(StandardCharsets.UTF_8));

    // 拼接 IV + ciphertext（含 auth tag）
    byte[] result = new byte[iv.length + ciphertext.length];
    System.arraycopy(iv, 0, result, 0, iv.length);
    System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

    return Base64.getEncoder().encodeToString(result);
  }

  /**
   * 使用 AES-GCM 解密用户 ID。
   *
   * @param encrypted Base64 编码的加密结果
   * @return 解密后的用户 ID
   * @throws GeneralSecurityException 解密失败时抛出
   */
  private String decryptUserId(String encrypted) throws GeneralSecurityException {
    byte[] decoded = Base64.getDecoder().decode(encrypted);

    if (decoded.length <= GCM_IV_LENGTH) {
      throw new IllegalArgumentException("Invalid encrypted cookie value: too short");
    }

    byte[] iv = new byte[GCM_IV_LENGTH];
    byte[] ciphertext = new byte[decoded.length - GCM_IV_LENGTH];
    System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);
    System.arraycopy(decoded, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

    SecretKeySpec keySpec = new SecretKeySpec(deriveKey(), "AES");
    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

    byte[] plaintext = cipher.doFinal(ciphertext);
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  /**
   * 派生 AES 加密密钥。
   *
   * <p>使用 SHA-256 对 Remember-Me Cookie 名称进行哈希，派生 32 字节密钥。
   * 生产环境建议使用独立的密钥配置项。
   *
   * @return 32 字节 AES 密钥
   */
  private byte[] deriveKey() {
    // 复用 common-util 统一摘要能力（DigestUtils.sha256 返回原始字节），禁止业务自建 MessageDigest
    String seed = rememberMeProperties.getCookieName() + ":ydsz-remember-me-aes-key";
    byte[] hash = DigestUtils.sha256(seed.getBytes(StandardCharsets.UTF_8));
    // 取前 32 字节（256 位）作为 AES 密钥
    byte[] key = new byte[AES_KEY_LENGTH];
    System.arraycopy(hash, 0, key, 0, AES_KEY_LENGTH);
    return key;
  }

  /**
   * 构建续期审计 Redis Key。
   *
   * @param userId 用户 ID
   * @return 审计 Redis Key
   */
  private String buildAuditKey(String userId) {
    return REMEMBER_ME_KEY_PREFIX + userId;
  }
}
