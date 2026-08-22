package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.annotation.SensitiveLevel;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;

/**
 * 编程式二级认证服务。
 *
 * <p>提供灵活的编程式 API，允许开发者在任意业务点触发二次认证，弥补 {@link SensitiveVerifyService} 注解驱动模式的不足。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>{@link #openSafe} — 校验密码后写入 Redis 安全标记，支持多场景并发
 *   <li>{@link #checkSafe} — 检查当前是否处于安全操作模式
 *   <li>{@link #closeSafe} — 清除安全标记
 *   <li>{@link #executeSafe} — 模板方法，在安全模式下执行回调，执行完毕后自动清除标记
 * </ul>
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:safe:{scene}:{userId}  →  "1"   安全操作标记，TTL 按级别区分
 * </pre>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 方式1：手动控制
 * secondaryAuthService.openSafe(password, "password_change");
 * try {
 *     // 执行业务操作
 * } finally {
 *     secondaryAuthService.closeSafe("password_change");
 * }
 *
 * // 方式2：模板方法（推荐）
 * secondaryAuthService.executeSafe(password, "data_export", () -> {
 *     return exportService.doExport(data);
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SensitiveVerifyService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecondaryAuthService {

  /** 安全操作标记 Redis Key 前缀 */
  private static final String SAFE_KEY_PREFIX = "userinfo:safe:";

  /** 默认安全标记有效期（5 分钟，HIGH / MEDIUM 级别） */
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  /** CRITICAL 级别安全标记有效期（2 分钟，更短时效以降低风险窗口） */
  private static final Duration CRITICAL_TTL = Duration.ofMinutes(2);

  /** 安全标记值 */
  private static final String SAFE_VALUE = "1";

  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final RedisStringOps redisStringOps;

  /**
   * 开启安全操作模式（编程式 API）。
   *
   * <p>校验当前登录用户的密码，通过后写入 Redis 安全标记。后续业务代码可调用 {@link #checkSafe(String)} 验证。
   *
   * @param password 当前登录用户的明文密码
   * @param scene 场景标识（如 "password_change", "role_assign", "data_export"）
   * @param ttl 安全标记有效期
   * @throws BusinessException 用户不存在、密码错误或未登录时抛出
   */
  public void openSafe(String password, String scene, Duration ttl) {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SECONDARY_AUTH_REQUIRED);
    }

    validatePassword(userId, password);

    String key = buildKey(scene, userId);
    redisStringOps.set(key, SAFE_VALUE, ttl);
    log.info("安全操作模式已开启: userId={}, scene={}, ttl={}", userId, scene, ttl);
  }

  /**
   * 开启安全操作模式（使用默认 HIGH 级别 5 分钟 TTL）。
   *
   * @param password 当前登录用户的明文密码
   * @param scene 场景标识
   * @throws BusinessException 用户不存在、密码错误或未登录时抛出
   */
  public void openSafe(String password, String scene) {
    openSafe(password, scene, DEFAULT_TTL);
  }

  /**
   * 检查当前是否处于安全操作模式。
   *
   * @param scene 场景标识
   * @return true 表示已通过二级认证且在有效期内
   */
  public boolean checkSafe(String scene) {
    return checkSafe(scene, SensitiveLevel.HIGH);
  }

  /**
   * 检查安全操作模式（指定级别）。
   *
   * <p>CRITICAL 级别使用 2 分钟短时效，其他级别使用 5 分钟时效。
   *
   * @param scene 场景标识
   * @param level 敏感操作等级
   * @return true 表示已通过二级认证且在有效期内
   */
  public boolean checkSafe(String scene, SensitiveLevel level) {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      return false;
    }
    String key = buildKey(scene, userId);
    try {
      String value = redisStringOps.get(key, String.class);
      boolean safe = SAFE_VALUE.equals(value);
      if (!safe) {
        log.warn("安全操作模式检查未通过: userId={}, scene={}, level={}", userId, scene, level);
      }
      return safe;
    } catch (Exception e) {
      log.warn("读取安全操作标记异常: userId={}, scene={}, error={}", userId, scene, e.getMessage());
      return false;
    }
  }

  /**
   * 关闭安全操作模式（清除标记）。
   *
   * @param scene 场景标识
   */
  public void closeSafe(String scene) {
    String userId = RequestContext.getUserId();
    if (userId == null || userId.isBlank()) {
      return;
    }
    String key = buildKey(scene, userId);
    try {
      redisStringOps.del(key);
      log.debug("安全操作模式已关闭: userId={}, scene={}", userId, scene);
    } catch (Exception e) {
      log.warn("清除安全操作标记异常: userId={}, scene={}, error={}", userId, scene, e.getMessage());
    }
  }

  /**
   * 执行安全操作模板方法。
   *
   * <p>校验密码后执行回调，执行完毕后自动清除安全标记（即使异常也清除）。
   * 使用默认 HIGH 级别 5 分钟 TTL。
   *
   * @param password 当前登录用户的明文密码
   * @param scene 场景标识
   * @param action 要执行的操作
   * @param <T> 返回值类型
   * @return 回调的返回值
   * @throws BusinessException 用户不存在、密码错误或未登录时抛出
   */
  public <T> T executeSafe(String password, String scene, Supplier<T> action) {
    return executeSafe(password, scene, SensitiveLevel.HIGH, action);
  }

  /**
   * 执行安全操作模板方法（带级别）。
   *
   * <p>校验密码后执行回调，执行完毕后自动清除安全标记（即使异常也清除）。
   * CRITICAL 级别使用 2 分钟短时效，其他级别使用 5 分钟时效。
   *
   * @param password 当前登录用户的明文密码
   * @param scene 场景标识
   * @param level 敏感操作等级
   * @param action 要执行的操作
   * @param <T> 返回值类型
   * @return 回调的返回值
   * @throws BusinessException 用户不存在、密码错误或未登录时抛出
   */
  public <T> T executeSafe(String password, String scene, SensitiveLevel level, Supplier<T> action) {
    Duration ttl = level == SensitiveLevel.CRITICAL ? CRITICAL_TTL : DEFAULT_TTL;
    openSafe(password, scene, ttl);
    try {
      return action.get();
    } finally {
      closeSafe(scene);
    }
  }

  /**
   * 校验当前登录用户的密码。
   *
   * @param userId 用户 ID
   * @param password 明文密码
   * @throws BusinessException 用户不存在或密码错误时抛出
   */
  private void validatePassword(String userId, String password) {
    Optional<UserAccountCredentialVO> credentialOpt = userAccountRepository.findCredentialById(userId);
    if (credentialOpt.isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }
    UserAccountCredentialVO credential = credentialOpt.get();
    if (!passwordEncoder.matches(password, credential.getPassword())) {
      log.warn("二级认证密码错误: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.SENSITIVE_VERIFY_PASSWORD_INCORRECT);
    }
  }

  /**
   * 构建 Redis Key。
   *
   * @param scene 场景标识
   * @param userId 用户 ID
   * @return Redis Key
   */
  private String buildKey(String scene, String userId) {
    return SAFE_KEY_PREFIX + scene + ":" + userId;
  }
}
