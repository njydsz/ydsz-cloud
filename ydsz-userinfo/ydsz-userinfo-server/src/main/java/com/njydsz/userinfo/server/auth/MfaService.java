package com.njydsz.userinfo.server.auth;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.njydsz.common.auth.util.TotpAuthenticator;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.MfaSetupVO;

/**
 * 双因素认证（MFA）服务。
 *
 * <p>提供 TOTP（RFC 6238，兼容 Google/Microsoft Authenticator）绑定与登录校验能力， 未绑定 TOTP 时降级使用短信验证码
 * （复用 {@link VerifyCodeService}，场景类型 {@code MFA_LOGIN}）。
 *
 * <p>算法实现复用 common-auth 的 {@link TotpAuthenticator}，不重复造轮子。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:mfa:setup:{userId}   →  Base32 secret    绑定流程临时密钥，TTL 5 分钟
 *   userinfo:mfa:secret:{userId}  →  Base32 secret    已绑定密钥，TTL 30 天
 *   userinfo:mfa:enabled:{userId} →  "1"              启用标记，TTL 30 天
 * </pre>
 *
 * <p><b>安全说明：</b>生产环境建议将 secret 加密落库（{@code ydsz-common-safe} 的
 * {@code @EncryptField} / CryptoUtils），替代 Redis 存储，本实现以最小侵入保证功能闭环。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

  /** 绑定流程临时密钥 Redis Key 前缀 */
  private static final String SETUP_KEY_PREFIX = "userinfo:mfa:setup:";

  /** 已绑定密钥 Redis Key 前缀 */
  private static final String SECRET_KEY_PREFIX = "userinfo:mfa:secret:";

  /** 启用标记 Redis Key 前缀 */
  private static final String ENABLED_KEY_PREFIX = "userinfo:mfa:enabled:";

  /** 绑定流程临时密钥有效期（5 分钟） */
  private static final Duration SETUP_TTL = Duration.ofMinutes(5);

  /** 已绑定密钥与启用标记有效期（30 天） */
  private static final Duration BOUND_TTL = Duration.ofDays(30);

  /** 启用标记值 */
  private static final String ENABLED_VALUE = "1";

  /** TOTP 发行方名称（出现在 Authenticator 应用中） */
  private static final String TOTP_ISSUER = "Ydsz Cloud";

  /** 登录短信验证码场景类型 */
  public static final String SMS_TYPE_MFA_LOGIN = "MFA_LOGIN";

  private final RedisStringOps redisStringOps;
  private final VerifyCodeService verifyCodeService;

  /**
   * 发起 TOTP 绑定：生成临时密钥与 otpauth URI。
   *
   * <p>密钥仅临时保存（5 分钟有效），用户完成动态码校验后由 {@link #activate(String, String)} 正式启用。
   *
   * @param userId 用户 ID
   * @param username 用户名（用于 otpauth URI 的 account 标识）
   * @return 绑定信息（临时密钥 + otpauth URI）
   */
  public MfaSetupVO setup(String userId, String username) {
    if (isMfaEnabled(userId)) {
      throw new BusinessException(UserInfoExceptionCode.MFA_ALREADY_BOUND);
    }
    String secret = TotpAuthenticator.generateSecret();
    String otpauthUri =
        TotpAuthenticator.buildOtpAuthUri(TOTP_ISSUER, username, secret);
    try {
      redisStringOps.set(SETUP_KEY_PREFIX + userId, secret, SETUP_TTL);
    } catch (Exception e) {
      log.warn("Failed to store MFA setup secret: userId={}, error={}", userId, e.getMessage());
    }
    log.info("MFA setup initiated: userId={}", userId);
    return new MfaSetupVO(secret, otpauthUri);
  }

  /**
   * 激活 TOTP 绑定：校验动态码后将临时密钥转正并标记启用。
   *
   * @param userId 用户 ID
   * @param code 用户输入的动态码（Authenticator 应用生成）
   * @throws BusinessException 未发起绑定或动态码错误时抛出
   */
  public void activate(String userId, String code) {
    String setupSecret = readSecret(SETUP_KEY_PREFIX + userId);
    if (setupSecret == null) {
      throw new BusinessException(UserInfoExceptionCode.MFA_NOT_BOUND);
    }
    if (!TotpAuthenticator.verify(setupSecret, code)) {
      log.warn("MFA activate failed, invalid code: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
    try {
      redisStringOps.set(SECRET_KEY_PREFIX + userId, setupSecret, BOUND_TTL);
      redisStringOps.set(ENABLED_KEY_PREFIX + userId, ENABLED_VALUE, BOUND_TTL);
      redisStringOps.del(SETUP_KEY_PREFIX + userId);
    } catch (Exception e) {
      log.warn("Failed to activate MFA: userId={}, error={}", userId, e.getMessage());
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
    log.info("MFA activated: userId={}", userId);
  }

  /**
   * 解除 TOTP 绑定：校验当前动态码后清除密钥与启用标记。
   *
   * @param userId 用户 ID
   * @param code 用户输入的动态码
   * @throws BusinessException 未启用 MFA 或动态码错误时抛出
   */
  public void disable(String userId, String code) {
    String secret = readSecret(SECRET_KEY_PREFIX + userId);
    if (secret == null || !isMfaEnabled(userId)) {
      throw new BusinessException(UserInfoExceptionCode.MFA_NOT_BOUND);
    }
    if (!TotpAuthenticator.verify(secret, code)) {
      log.warn("MFA disable failed, invalid code: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
    try {
      redisStringOps.del(SECRET_KEY_PREFIX + userId);
      redisStringOps.del(ENABLED_KEY_PREFIX + userId);
    } catch (Exception e) {
      log.warn("Failed to disable MFA: userId={}, error={}", userId, e.getMessage());
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
    log.info("MFA disabled: userId={}", userId);
  }

  /**
   * 判断用户是否已启用 TOTP 绑定。
   *
   * @param userId 用户 ID
   * @return true 表示已启用
   */
  public boolean isMfaEnabled(String userId) {
    if (userId == null || userId.isBlank()) {
      return false;
    }
    try {
      return ENABLED_VALUE.equals(redisStringOps.get(ENABLED_KEY_PREFIX + userId, String.class));
    } catch (Exception e) {
      log.warn("Failed to check MFA enabled: userId={}, error={}", userId, e.getMessage());
      return false;
    }
  }

  /**
   * 登录场景的 MFA 动态码校验（供登录流程在风险为 HIGH 时调用）。
   *
   * <p>已绑定 TOTP → 校验 TOTP 动态码；未绑定但有手机号 → 校验短信验证码；两者均不可用时拒绝登录。
   *
   * @param user 登录用户
   * @param mfaCode 用户提交的动态码（TOTP 或短信验证码）
   * @throws BusinessException 动态码缺失、错误或用户无法完成 MFA 时抛出
   */
  public void validateLoginMfa(UserAccount user, String mfaCode) {
    String userId = user.getId();
    String secret = readSecret(SECRET_KEY_PREFIX + userId);
    if (secret != null && isMfaEnabled(userId)) {
      if (mfaCode == null || mfaCode.isBlank()
          || !TotpAuthenticator.verify(secret, mfaCode)) {
        throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
      }
      return;
    }
    // 未绑定 TOTP：降级短信验证码
    if (user.getPhone() == null || user.getPhone().isBlank()) {
      log.warn("MFA required but user has no TOTP bound and no phone: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.MFA_REQUIRED);
    }
    if (mfaCode == null || mfaCode.isBlank()
        || !verifyCodeService.verifyCode(SMS_TYPE_MFA_LOGIN, user.getPhone(), mfaCode)) {
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
  }

  /**
   * 发送登录场景短信验证码（前端在风险为 HIGH 且未绑定 TOTP 时调用）。
   *
   * @param phone 手机号
   * @throws BusinessException 发送过于频繁时抛出
   */
  public void sendLoginSmsCode(String phone) {
    verifyCodeService.sendCode(SMS_TYPE_MFA_LOGIN, phone);
  }

  private String readSecret(String key) {
    try {
      return redisStringOps.get(key, String.class);
    } catch (Exception e) {
      log.warn("Failed to read MFA secret: key={}, error={}", key, e.getMessage());
      return null;
    }
  }
}
