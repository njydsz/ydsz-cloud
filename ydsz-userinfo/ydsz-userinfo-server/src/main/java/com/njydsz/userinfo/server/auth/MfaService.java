package com.njydsz.userinfo.server.auth;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.util.TotpAuthenticator;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.config.MfaSecretEncryptor;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.MfaSetupVO;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;

/**
 * 双因素认证（MFA）服务。
 *
 * <p>提供 TOTP（RFC 6238，兼容 Google/Microsoft Authenticator）绑定与登录校验能力。
 * MFA 校验策略（优先级从高到低）：
 *
 * <ol>
 *   <li>已绑定 TOTP → 校验 TOTP 动态码</li>
 *   <li>未绑定 TOTP 且有手机号 → 降级使用短信验证码（场景类型 {@code MFA_LOGIN}）</li>
 *   <li>未绑定 TOTP 且无手机号但有邮箱 → 降级使用邮件验证码（场景类型 {@code MFA_LOGIN_EMAIL}）</li>
 * </ol>
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
   * <p><b>安全说明：</b>生产环境通过配置 {@code ydsz.userinfo.mfa.encryption-key} 启用
   * AES-256-GCM 加密，密钥在存入 Redis 前自动加密，读取时自动解密。
   * 未配置时明文存储（仅适用于开发/测试环境）。
 *
 * @author ydsz-team
 * @since 1.0.0
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

  /** 登录邮件验证码场景类型 */
  public static final String EMAIL_TYPE_MFA_LOGIN = "MFA_LOGIN_EMAIL";

  private final RedisStringOps redisStringOps;
  private final VerifyCodeService verifyCodeService;
  private final MfaSecretEncryptor mfaSecretEncryptor;

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
    // 加密后存入 Redis（生产环境配置 encryption-key 时自动加密，开发环境明文）
    String encryptedSecret = mfaSecretEncryptor.encrypt(secret);
    try {
      redisStringOps.set(SETUP_KEY_PREFIX + userId, encryptedSecret, SETUP_TTL);
    } catch (Exception e) {
      log.warn("Failed to store MFA setup secret: userId={}, error={}", userId, e.getMessage(), e);
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
    // 加密后正式存储
    String encryptedSecret = mfaSecretEncryptor.encrypt(setupSecret);
    try {
      redisStringOps.set(SECRET_KEY_PREFIX + userId, encryptedSecret, BOUND_TTL);
      redisStringOps.set(ENABLED_KEY_PREFIX + userId, ENABLED_VALUE, BOUND_TTL);
      redisStringOps.del(SETUP_KEY_PREFIX + userId);
    } catch (Exception e) {
      log.warn("Failed to activate MFA: userId={}, error={}", userId, e.getMessage(), e);
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
    // 解绑时清除加密存储的密钥
    try {
      redisStringOps.del(SECRET_KEY_PREFIX + userId);
      redisStringOps.del(ENABLED_KEY_PREFIX + userId);
    } catch (Exception e) {
      log.warn("Failed to disable MFA: userId={}, error={}", userId, e.getMessage(), e);
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
      log.warn("Failed to check MFA enabled: userId={}, error={}", userId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * 登录场景的 MFA 动态码校验（供登录流程在风险为 HIGH 时调用）。
   *
   * <p>MFA 校验策略（优先级从高到低）：
   *
   * <ol>
   *   <li>已绑定 TOTP → 校验 TOTP 动态码</li>
   *   <li>未绑定 TOTP 且有手机号 → 校验短信验证码</li>
   *   <li>未绑定 TOTP 且无手机号但有邮箱 → 校验邮件验证码</li>
   * </ol>
   *
   * @param user 登录用户
   * @param mfaCode 用户提交的动态码（TOTP、短信验证码或邮件验证码）
   * @throws BusinessException 动态码缺失、错误或用户无法完成 MFA 时抛出
   */
  public void validateLoginMfa(UserAccountCredentialVO user, String mfaCode) {
    String userId = user.getId();
    // readSecret 已自动解密，可直接用于 TOTP 校验
    String secret = readSecret(SECRET_KEY_PREFIX + userId);
    if (secret != null && isMfaEnabled(userId)) {
      validateTotp(secret, mfaCode);
      return;
    }
    // 未绑定 TOTP：降级短信验证码
    if (user.getPhone() != null && !user.getPhone().isBlank()) {
      validateSmsCode(user.getPhone(), mfaCode);
      return;
    }
    // 无手机号：降级邮件验证码
    if (user.getEmail() != null && !user.getEmail().isBlank()) {
      validateEmailCode(user.getEmail(), mfaCode);
      return;
    }
    log.warn("MFA required but user has no TOTP bound, no phone, no email: userId={}", userId);
    throw new BusinessException(UserInfoExceptionCode.MFA_REQUIRED);
  }

  /** 校验 TOTP 动态码。 */
  private void validateTotp(String secret, String mfaCode) {
    if (mfaCode == null || mfaCode.isBlank()
        || !TotpAuthenticator.verify(secret, mfaCode)) {
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
  }

  /** 校验短信验证码。 */
  private void validateSmsCode(String phone, String mfaCode) {
    if (mfaCode == null || mfaCode.isBlank()
        || !verifyCodeService.verifyCode(SMS_TYPE_MFA_LOGIN, phone, mfaCode)) {
      throw new BusinessException(UserInfoExceptionCode.MFA_INVALID);
    }
  }

  /** 校验邮件验证码。 */
  private void validateEmailCode(String email, String mfaCode) {
    if (mfaCode == null || mfaCode.isBlank()
        || !verifyCodeService.verifyCode(EMAIL_TYPE_MFA_LOGIN, email, mfaCode)) {
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
    verifyCodeService.sendCode(SMS_TYPE_MFA_LOGIN, VerifyCodeService.TARGET_TYPE_PHONE, phone);
  }

  /**
   * 发送登录场景邮件验证码（前端在风险为 HIGH 且未绑定 TOTP 且无手机号时调用）。
   *
   * @param email 邮箱地址
   * @throws BusinessException 发送过于频繁时抛出
   */
  public void sendLoginEmailCode(String email) {
    verifyCodeService.sendCode(EMAIL_TYPE_MFA_LOGIN, VerifyCodeService.TARGET_TYPE_EMAIL, email);
  }

  /**
   * 从 Redis 读取 MFA 密钥并自动解密。
   *
   * <p>无论生产环境（AES-256-GCM 加密）还是开发环境（明文），均通过
   * {@link MfaSecretEncryptor#decrypt} 处理，对调用方透明。
   *
   * @param key Redis Key
   * @return 解密后的明文密钥；不存在或解密失败返回 null
   */
  private String readSecret(String key) {
    try {
      String encrypted = redisStringOps.get(key, String.class);
      if (encrypted == null || encrypted.isBlank()) {
        return null;
      }
      return mfaSecretEncryptor.decrypt(encrypted);
    } catch (Exception e) {
      log.warn("Failed to read/decrypt MFA secret: key={}, error={}", key, e.getMessage(), e);
      return null;
    }
  }
}
