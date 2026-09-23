package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.system.api.client.UserCredentialClient;
import com.njydsz.system.domain.dto.VerifyPasswordRequest;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.SecondaryAuthVO;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.service.SecondaryAuthService;

/**
 * 二次身份验证服务实现。
 *
 * <p>校验当前登录用户的密码（通过 Feign 调用用户中心服务），校验通过后生成 UUID 令牌并存储在 Redis 中， TTL 由配置驱动。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   system:secondary-auth:token:{userId}:{token}  →  "1"   验证通过标记，TTL 30 分钟
 *   system:secondary-auth:fail-count:{userId}      →  失败计数（仅失败时写入），TTL 15 分钟
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecondaryAuthServiceImpl implements SecondaryAuthService {

  /** 二次认证令牌 Redis Key 前缀 */
  private static final String TOKEN_KEY_PREFIX = "system:secondary-auth:token:";

  /** 验证失败计数 Redis Key 前缀 */
  private static final String FAIL_COUNT_KEY_PREFIX = "system:secondary-auth:fail-count:";

  /** 令牌值常量 */
  private static final String TOKEN_VERIFIED_VALUE = "1";

  private final UserCredentialClient userCredentialClient;
  private final RedisStringOps redisStringOps;
  private final SystemProperties systemProperties;

  /**
   * 发起二次身份验证：校验密码，通过后颁发令牌。
   *
   * @param userId 当前登录用户 ID
   * @param password 明文密码（HTTPS 传输）
   * @param scene 场景标识（如 config-edit / dict-delete），用于审计
   * @return 二次认证令牌（含 Token 和过期时间）
   * @throws BusinessException 密码错误/用户不存在/账号锁定时抛出
   */
  @Override
  public SecondaryAuthVO verify(String userId, String password, String scene) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(SystemExceptionCode.SECONDARY_AUTH_USER_NOT_FOUND);
    }

    // 检查失败锁定
    if (isLocked(userId)) {
      log.warn("二次认证被锁定: userId={}, scene={}", userId, scene);
      throw new BusinessException(SystemExceptionCode.SECONDARY_AUTH_TOKEN_INVALID);
    }

    // 调用用户中心校验密码
    boolean passwordValid = verifyPasswordThroughClient(userId, password);

    if (!passwordValid) {
      // 记录失败计数
      incrementFailCount(userId);
      log.warn("二次认证密码错误: userId={}, scene={}", userId, scene);
      throw new BusinessException(SystemExceptionCode.SECONDARY_AUTH_PASSWORD_INCORRECT);
    }

    // 密码校验通过：清除失败计数、颁发令牌
    clearFailCount(userId);
    SecondaryAuthVO vo = issueToken(userId);
    log.info("二次认证通过: userId={}, scene={}, expiresAt={}", userId, scene, vo.getExpiresAt());
    return vo;
  }

  /**
   * 校验二次认证令牌是否有效。
   *
   * @param userId 用户 ID
   * @param token 待验证的令牌
   * @return true 表示令牌有效且未过期；false 表示无效或已过期
   */
  @Override
  public boolean isTokenValid(String userId, String token) {
    if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
      return false;
    }
    String key = buildTokenKey(userId, token);
    try {
      String value = redisStringOps.get(key, String.class);
      return TOKEN_VERIFIED_VALUE.equals(value);
    } catch (Exception e) {
      log.warn("读取二次认证令牌异常: userId={}, error={}", userId, e.getMessage());
      return false;
    }
  }

  /**
   * 通过 Feign 客户端调用用户中心校验密码。
   *
   * @param userId 用户 ID
   * @param password 明文密码
   * @return true 表示密码正确；false 表示密码错误或用户不存在
   */
  private boolean verifyPasswordThroughClient(String userId, String password) {
    VerifyPasswordRequest request = new VerifyPasswordRequest();
    request.setUserId(userId);
    request.setPassword(password);
    try {
      YdszResponse<Boolean> response = userCredentialClient.verifyPassword(request);
      return response != null && response.getData() != null && Boolean.TRUE.equals(response.getData());
    } catch (Exception e) {
      log.warn("调用用户中心密码校验服务异常: userId={}, error={}", userId, e.getMessage());
      return false;
    }
  }

  /**
   * 颁发二次认证令牌：生成 UUID 并存入 Redis。
   *
   * @param userId 用户 ID
   * @return 令牌 VO
   */
  private SecondaryAuthVO issueToken(String userId) {
    String token = UUID.randomUUID().toString();
    Duration ttl = Duration.ofMinutes(systemProperties.getSecondaryAuth().getTokenTtlMinutes());
    String key = buildTokenKey(userId, token);
    redisStringOps.set(key, TOKEN_VERIFIED_VALUE, ttl);

    long ttlMillis = ttl.toMillis();
    SecondaryAuthVO vo = new SecondaryAuthVO();
    vo.setToken(token);
    vo.setExpiresIn(ttlMillis);
    vo.setExpiresAt(System.currentTimeMillis() + ttlMillis);
    return vo;
  }

  /**
   * 检查用户是否被二次认证失败锁定。
   *
   * @param userId 用户 ID
   * @return true 表示处于锁定状态；false 表示未锁定
   */
  private boolean isLocked(String userId) {
    try {
      String key = buildFailCountKey(userId);
      String countStr = redisStringOps.get(key, String.class);
      if (countStr == null) {
        return false;
      }
      int count = Integer.parseInt(countStr);
      return count >= systemProperties.getSecondaryAuth().getMaxFailCount();
    } catch (NumberFormatException e) {
      return false;
    } catch (Exception e) {
      log.warn("读取二次认证失败计数异常: userId={}, error={}", userId, e.getMessage());
      return false;
    }
  }

  /**
   * 递增失败计数并设置过期时间。
   *
   * @param userId 用户 ID
   */
  private void incrementFailCount(String userId) {
    try {
      String key = buildFailCountKey(userId);
      String countStr = redisStringOps.get(key, String.class);
      int count = countStr == null ? 0 : Integer.parseInt(countStr);
      count++;
      Duration ttl = Duration.ofMinutes(systemProperties.getSecondaryAuth().getFailLockMinutes());
      redisStringOps.set(key, String.valueOf(count), ttl);
    } catch (Exception e) {
      log.warn("更新二次认证失败计数异常: userId={}, error={}", userId, e.getMessage());
    }
  }

  /**
   * 清除失败计数（密码校验通过后调用）。
   *
   * @param userId 用户 ID
   */
  private void clearFailCount(String userId) {
    try {
      String key = buildFailCountKey(userId);
      redisStringOps.delete(key);
    } catch (Exception e) {
      log.warn("清除二次认证失败计数异常: userId={}, error={}", userId, e.getMessage());
    }
  }

  /**
   * 构建二次认证令牌 Redis Key。
   *
   * @param userId 用户 ID
   * @param token 令牌值
   * @return Redis Key
   */
  private String buildTokenKey(String userId, String token) {
    return TOKEN_KEY_PREFIX + userId + ":" + token;
  }

  /**
   * 构建二次认证失败计数 Redis Key。
   *
   * @param userId 用户 ID
   * @return Redis Key
   */
  private String buildFailCountKey(String userId) {
    return FAIL_COUNT_KEY_PREFIX + userId;
  }
}
