package com.njydsz.userinfo.server.auth;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.infra.repository.UserAccountRepository;

/**
 * 敏感操作二次认证服务。
 *
 * <p>提供敏感操作前的身份验证能力：校验当前登录用户的密码，验证通过后写入 Redis 短期标记（5 分钟）， 后续敏感操作接口通过 AOP 切面检查该标记。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:sensitive:verified:{userId}  →  "1"   验证通过标记，TTL 5 分钟
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveVerifyService {

  /** 敏感操作验证标记 Redis Key 前缀 */
  private static final String SENSITIVE_VERIFIED_KEY_PREFIX = "userinfo:sensitive:verified:";

  /** 验证标记有效期（5 分钟） */
  private static final Duration VERIFY_TTL = Duration.ofMinutes(5);

  /** 验证标记值 */
  private static final String VERIFIED_VALUE = "1";

  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final RedisStringOps redisStringOps;

  /**
   * 执行二次认证：校验当前登录用户的密码，通过后写入 Redis 验证标记。
   *
   * @param password 当前登录用户的明文密码
   * @throws BusinessException 用户不存在或密码错误时抛出
   */
  public void verify(String password) {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SENSITIVE_VERIFY_REQUIRED);
    }

    UserAccount user = userAccountRepository.findById(userId);
    if (user == null) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }

    if (!passwordEncoder.matches(password, user.getPassword())) {
      log.warn("敏感操作二次认证密码错误: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.SENSITIVE_VERIFY_PASSWORD_INCORRECT);
    }

    String key = buildKey(userId);
    redisStringOps.set(key, VERIFIED_VALUE, VERIFY_TTL);
    log.info("敏感操作二次认证通过: userId={}", userId);
  }

  /**
   * 检查当前登录用户是否已通过二次认证（Redis 标记是否存在）。
   *
   * @return true 表示已通过且在有效期内；false 表示未验证或已过期
   */
  public boolean isVerified() {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      return false;
    }
    String key = buildKey(userId);
    try {
      String value = redisStringOps.get(key, String.class);
      return VERIFIED_VALUE.equals(value);
    } catch (Exception e) {
      log.warn("读取敏感操作验证标记异常: userId={}, error={}", userId, e.getMessage());
      return false;
    }
  }

  /**
   * 清除当前登录用户的验证标记（敏感操作执行后调用，确保一次性使用）。
   */
  public void clearVerification() {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      return;
    }
    String key = buildKey(userId);
    try {
      redisStringOps.del(key);
      log.debug("敏感操作验证标记已清除: userId={}", userId);
    } catch (Exception e) {
      log.warn("清除敏感操作验证标记异常: userId={}, error={}", userId, e.getMessage());
    }
  }

  private String buildKey(String userId) {
    return SENSITIVE_VERIFIED_KEY_PREFIX + userId;
  }
}
