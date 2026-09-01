package com.njydsz.userinfo.server.auth;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.password.PasswordStrengthValidator;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 密码策略校验器。
 *
 * <p>基于 common-safe 的 {@link PasswordStrengthValidator} 进行基础密码强度校验，
 * 并扩展用户中心特有的业务规则。
 *
 * <h3>校验分层</h3>
 *
 * <ul>
 *   <li><b>基础强度</b>（委托 common-safe）：长度下限、字符多样性（大写/小写/数字/特殊字符）、
 *       连续字符（如 abc、123）、重复字符（如 aaa、111）、常见弱密码字典（Top 100+）
 *   <li><b>业务扩展</b>（本类实现）：最大长度限制、键盘序列（如 qwer、asdf）、
 *       连续字母序列双向检测（abc/cba）、用户名包含检查、历史密码防重用
 * </ul>
 *
 * <p>异常码统一使用 {@link UserInfoExceptionCode}，common-safe 抛出的 {@link
 * SecurityExceptionCode#PASSWORD_TOO_WEAK} 会在内部转换为 {@link
 * UserInfoExceptionCode#PASSWORD_TOO_WEAK}，对调用方透明。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see PasswordStrengthValidator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {
  /** 连续字符检测窗口大小 */
  private static final int SEQUENCE_WINDOW = 3;


  private final UserInfoProperties properties;
  private final PasswordStrengthValidator passwordStrengthValidator;

  /** P2-4: 常见键盘序列（连续 3 位及以上视为弱口令特征） */
  private static final List<String> KEYBOARD_ROWS =
      List.of(
          "qwertyuiop",
          "asdfghjkl",
          "zxcvbnm",
          "1234567890",
          "poiuytrewq",
          "lkjhgfdsa",
          "mnbvcxz");

  /**
   * 校验密码强度（不检查历史密码，用于创建用户等无需检查历史的场景）。
   *
   * @param password 待校验密码
   * @param username 用户名（用于检查密码是否包含用户名）
   * @throws BusinessException 密码不符合策略时抛出
   */
  public void validate(String password, String username) {
    validate(password, username, null, null);
  }

  /**
   * 校验密码强度（含历史密码检查，用于修改密码场景）。
   *
   * <p>当 {@code passwordHistoryService} 和 {@code userId} 均不为 null 时， 额外校验新密码是否与用户最近 {@code
   * historyCount} 条历史密码重复。
   *
   * @param password 待校验密码
   * @param username 用户名（用于检查密码是否包含用户名）
   * @param userId 用户 ID（用于查询历史密码，为 null 时跳过历史检查）
   * @param passwordHistoryService 密码历史服务，为 null 时跳过历史检查
   * @throws BusinessException 密码不符合策略或与历史密码重复时抛出
   */
  public void validate(
      String password,
      String username,
      String userId,
      UserPasswordHistoryService passwordHistoryService) {
    int maxLength = properties.getPasswordMaxLength();

    // 1. 基础强度校验（委托 common-safe PasswordStrengthValidator）
    validateBasicStrength(password);

    // 2. 最大长度校验（common-safe 不校验最大长度）
    validateMaxLength(password, maxLength);

    // 3. 键盘序列检测
    validateNoKeyboardSequence(password);

    // 4. 连续字母序列双向检测（abc / cba）
    validateNoSequentialAlphabet(password);

    // 5. 用户名包含检查
    validateNotContainUsername(password, username);

    // 6. 历史密码防重用
    validateNotReusedFromHistory(password, userId, passwordHistoryService);
  }

  /**
   * 基础密码强度校验（委托 common-safe {@link PasswordStrengthValidator}）。
   *
   * <p>校验失败时将 common-safe 的 {@link SecurityExceptionCode#PASSWORD_TOO_WEAK}（C01071） 转换为用户中心模块的
   * {@link UserInfoExceptionCode#PASSWORD_TOO_WEAK}（B30012），保持对外错误码一致。
   *
   * @param password 明文密码
   * @throws BusinessException 密码强度不足时抛出
   */
  private void validateBasicStrength(String password) {
    try {
      passwordStrengthValidator.validate(password);
    } catch (BusinessException e) {
      // 将 common-safe 的安全异常码转换为用户中心模块的业务异常码
      if (SecurityExceptionCode.PASSWORD_TOO_WEAK.getCode().equals(e.getCode())) {
        throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK);
      }
      throw e;
    }
  }

  /**
   * 校验密码最大长度。
   *
   * <p>common-safe 的密码强度校验仅校验下限，最大长度由业务模块自行控制。 BCrypt 编码器对输入长度有限制（72 字节），超过时需截断或拒绝。
   *
   * @param password 待校验密码
   * @param maxLength 最大长度
   * @throws BusinessException 密码超过最大长度时抛出
   */
  private void validateMaxLength(String password, int maxLength) {
    if (password != null && password.length() > maxLength) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.PASSWORD_TOO_WEAK)
          .params(maxLength)
          .build();
    }
  }

  /**
   * P2-4: 校验密码不包含常见键盘序列（如 qwer、asdf、1234 连续 3 位及以上）。
   *
   * @param password 待校验密码
   * @throws BusinessException 含键盘序列时抛出
   */
  private void validateNoKeyboardSequence(String password) {
    if (password == null) {
      return;
    }
    String lower = password.toLowerCase();
    for (String row : KEYBOARD_ROWS) {
      for (int i = 0; i + SEQUENCE_WINDOW <= row.length(); i++) {
        String seq = row.substring(i, i + SEQUENCE_WINDOW);
        if (lower.contains(seq)) {
          throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK);
        }
      }
    }
  }

  /**
   * P2-4: 校验密码不包含连续 3 位升序/降序字母序列（如 abc、cba）。
   *
   * <p>common-safe 的 DefaultPasswordStrengthChecker 仅检测升序连续字符（abc、123）， 本类额外检测降序（cba、321），覆盖更严格的策略。
   *
   * @param password 待校验密码
   * @throws BusinessException 含连续字母序列时抛出
   */
  private void validateNoSequentialAlphabet(String password) {
    if (password == null || password.length() < SEQUENCE_WINDOW) {
      return;
    }
    String lower = password.toLowerCase();
    for (int i = 0; i + 2 < lower.length(); i++) {
      char c1 = lower.charAt(i);
      char c2 = lower.charAt(i + 1);
      char c3 = lower.charAt(i + 2);
      if (isLetter(c1) && c1 + 1 == c2 && c2 + 1 == c3) {
        throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK);
      }
      if (isLetter(c1) && c1 - 1 == c2 && c2 - 1 == c3) {
        throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK);
      }
    }
  }

  /**
   * 判断字符是否为字母。
   *
   * @param c 字符
   * @return true 表示字母
   */
  private boolean isLetter(char c) {
    return c >= 'a' && c <= 'z';
  }

  /**
   * 校验密码不包含用户名（忽略大小写）。
   *
   * @param password 待校验密码
   * @param username 用户名
   * @throws BusinessException 密码包含用户名时抛出
   */
  private void validateNotContainUsername(String password, String username) {
    if (username != null && !username.isBlank()) {
      if (password.toLowerCase().contains(username.toLowerCase())) {
        throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK);
      }
    }
  }

  /**
   * 校验密码未在最近历史密码中重复使用。
   *
   * @param password 待校验密码
   * @param userId 用户 ID
   * @param passwordHistoryService 密码历史服务
   * @throws BusinessException 密码与历史密码重复时抛出
   */
  private void validateNotReusedFromHistory(
      String password, String userId, UserPasswordHistoryService passwordHistoryService) {
    if (userId == null || passwordHistoryService == null) {
      return;
    }
    int historyCount = properties.getPasswordHistoryCount();
    if (historyCount > 0
        && passwordHistoryService.isPasswordReused(userId, password, historyCount)) {
      throw BusinessException.builder()
          .resultCode(UserInfoExceptionCode.PASSWORD_REUSED)
          .params(historyCount)
          .build();
    }
  }
}
