package com.njydsz.common.safe.password;

import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.password.PasswordStrengthChecker;
import com.njydsz.common.util.password.PwdUtils;

/**
 * 密码强度校验器（P0-1：统一封装 common-util 的 PasswordStrengthChecker SPI）。
 *
 * <p>业务模块（注册/修改密码）应直接使用此类校验密码强度，避免各自实现正则规则。 内部委托给 {@link
 * PwdUtils#checkPasswordStrengthLevel(String)}， 支持通过 {@code META-INF/services} 注册自定义校验器覆盖默认策略。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 注入
 * private final PasswordStrengthValidator passwordStrengthValidator;
 *
 * // 校验（使用默认最低要求 MEDIUM）
 * passwordStrengthValidator.validate(password);
 *
 * // 校验（自定义最低要求 STRONG）
 * passwordStrengthValidator.validate(password, PasswordStrengthChecker.PasswordStrengthLevel.STRONG);
 * }</pre>
 *
 * <h3>对比业务模块自实现</h3>
 *
 * <p>历史版本中，userinfo 模块的 {@code PasswordPolicyValidator} 自写了正则规则 （如 {@code HAS_LOWER}、{@code
 * HAS_UPPER}），与 common-util 的 {@link PasswordStrengthChecker} 存在策略不一致风险。现统一委托后：
 *
 * <ul>
 *   <li>common-util 的弱密码字典、连续字符、重复字符等规则由 common-util 统一维护
 *   <li>业务模块仅保留业务特有逻辑（如密码历史校验、用户名包含检查）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see PwdUtils
 * @see PasswordStrengthChecker
 */
@Slf4j
public class PasswordStrengthValidator {

  /**
   * 默认最低密码强度要求。
   *
   * <p>MEDIUM（中等）为大多数业务场景的推荐阈值， 金融/高安全场景可通过构造时配置提升为 STRONG。
   */
  public static final PasswordStrengthChecker.PasswordStrengthLevel DEFAULT_MIN_LEVEL =
      PasswordStrengthChecker.PasswordStrengthLevel.MEDIUM;

  private final PasswordStrengthChecker.PasswordStrengthLevel minLevel;

  /** 构造器（使用默认最低强度 MEDIUM）。 */
  public PasswordStrengthValidator() {
    this(DEFAULT_MIN_LEVEL);
  }

  /**
   * 构造器（自定义最低强度）。
   *
   * @param minLevel 最低密码强度要求，不可为 null
   */
  public PasswordStrengthValidator(PasswordStrengthChecker.PasswordStrengthLevel minLevel) {
    if (minLevel == null) {
      throw new IllegalArgumentException("minLevel must not be null");
    }
    this.minLevel = minLevel;
  }

  /**
   * 校验密码强度是否达到最低要求。
   *
   * <p>校验失败时抛出 {@link BusinessException}， 错误码为 {@link
   * SecurityExceptionCode#PASSWORD_TOO_WEAK}（C01071）。
   *
   * @param password 明文密码
   * @throws BusinessException 密码强度不足时抛出
   */
  public void validate(String password) {
    validate(password, minLevel);
  }

  /**
   * 校验密码强度是否达到指定最低要求。
   *
   * @param password 明文密码
   * @param customLevel 自定义最低强度要求
   * @throws BusinessException 密码强度不足时抛出
   */
  public void validate(String password, PasswordStrengthChecker.PasswordStrengthLevel customLevel) {
    PasswordStrengthChecker.PasswordStrengthLevel actualLevel =
        PwdUtils.checkPasswordStrengthLevel(password);

    if (actualLevel.ordinal() < customLevel.ordinal()) {
      String suggestion = PwdUtils.suggestPasswordImprovement(password, Locale.CHINESE);
      log.debug(
          "[PasswordStrengthValidator] 密码强度不足: actual={}, required={}, suggestion={}",
          actualLevel,
          customLevel,
          suggestion);

      throw BusinessException.builder()
          .code(SecurityExceptionCode.PASSWORD_TOO_WEAK.getCode())
          .key(SecurityExceptionCode.PASSWORD_TOO_WEAK.getKey())
          .message(buildErrorMessage(actualLevel, customLevel, suggestion))
          .build();
    }
  }

  /**
   * 计算密码强度等级（仅供前端展示或日志记录，不抛异常）。
   *
   * @param password 明文密码
   * @return 密码强度等级（不会返回 null）
   */
  public PasswordStrengthChecker.PasswordStrengthLevel evaluate(String password) {
    return PwdUtils.checkPasswordStrengthLevel(password);
  }

  /**
   * 获取改进建议（国际化）。
   *
   * @param password 明文密码
   * @return 改进建议文本
   */
  public String getSuggestion(String password) {
    return PwdUtils.suggestPasswordImprovement(password, Locale.CHINESE);
  }

  /** 构建错误消息。 */
  private String buildErrorMessage(
      PasswordStrengthChecker.PasswordStrengthLevel actual,
      PasswordStrengthChecker.PasswordStrengthLevel required,
      String suggestion) {
    StringBuilder sb =
        new StringBuilder("密码强度不足（当前：").append(actual).append("，要求：≥").append(required).append("）");
    if (suggestion != null && !suggestion.isEmpty()) {
      sb.append("。建议：").append(suggestion);
    }
    return sb.toString();
  }
}
