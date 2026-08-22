package com.njydsz.userinfo.server.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginAttemptContext;
import com.njydsz.userinfo.server.service.LoginHistoryService;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;

/**
 * 凭据校验器。
 *
 * <p>负责登录密码校验（本地 BCrypt 优先，失败回退 LDAP），校验失败时记录登录历史、 发布暴力破解安全事件、原子递增失败计数并触发账号锁定。
 * 从 {@link AuthServiceImpl} 拆分（P0-5），聚焦「凭据校验」单一职责。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see PasswordEncoder BCrypt 密码编码器
 * @see LdapAuthenticationProvider LDAP 域认证提供者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialVerifier {

  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final ObjectProvider<LdapAuthenticationProvider> ldapProviderProvider;
  private final LoginHistoryService loginHistoryService;
  private final ObjectProvider<SecurityEventPublisher> securityEventPublisherProvider;
  private final UserInfoMetrics userInfoMetrics;
  private final UserInfoProperties properties;
  private final UserDomainEventPublisher eventPublisher;

  /**
   * 校验密码（本地 BCrypt 优先，失败后回退 LDAP）。
   *
   * <p>校验失败：原子递增失败计数（达到阈值锁定）→ 记录登录历史 → 发布 BRUTE_FORCE 安全事件 → 埋点并抛出异常。
   *
   * @param user 用户账号
   * @param password 明文密码
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @throws BusinessException 密码校验失败时抛出
   */
  public void verify(UserAccountCredentialVO user, String password, String loginIp, String userAgent) {
    String username = user.getUsername();
    boolean passwordMatched = passwordEncoder.matches(password, user.getPassword());
    if (!passwordMatched) {
      LdapAuthenticationProvider ldapProvider = ldapProviderProvider.getIfAvailable();
      if (ldapProvider != null && ldapProvider.isEnabled()) {
        passwordMatched = ldapProvider.authenticateLdap(username, password);
        if (passwordMatched) {
          log.info("LDAP authentication succeeded for user: {}", username);
        }
      }
    }

    if (!passwordMatched) {
      recordLoginFailure(user);
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), username, loginIp),
          "FAILED",
          "PASSWORD_INCORRECT",
          userAgent);
      publishBruteForceEvent(loginIp, userAgent, username);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    }
  }

  /**
   * 记录登录失败：原子自增失败计数，达到阈值时由 SQL 原子设置锁定时间，并发布登录失败事件。
   *
   * <p>锁定时间戳由 Service 层预计算后传入，避免 Mapper SQL 使用数据库特定的 INTERVAL 语法。
   *
   * @param user 登录失败的用户账号凭据 VO
   */
  private void recordLoginFailure(UserAccountCredentialVO user) {
    int lockMinutes = properties.getLockDurationMinutes();
    java.time.LocalDateTime lockUntil = java.time.LocalDateTime.now().plusMinutes(lockMinutes);
    userAccountRepository.increaseLoginFailCount(
        user.getId(), properties.getMaxLoginFailCount(), lockUntil);
    eventPublisher.publishLoginFailed(
        user.getId(), user.getUsername(), null, "PASSWORD_INCORRECT", properties.getMaxLoginFailCount());
    // 锁定事件：递增后若账号被锁定，发布 AccountLockedEvent
    userAccountRepository.findCredentialById(user.getId()).ifPresent(cred -> {
      if (cred.isLocked()) {
        eventPublisher.publishAccountLocked(
            user.getId(), user.getUsername(), lockMinutes, "TOO_MANY_FAILED_ATTEMPTS");
      }
    });
    log.warn("User [{}] login failed, fail count incremented atomically", user.getUsername());
  }

  /**
   * 发布 BRUTE_FORCE 安全事件（驱动 common-safe 安全事件聚合与告警）。
   *
   * @param sourceIp 请求来源 IP
   * @param userAgent 用户代理
   * @param username 登录尝试的用户名
   */
  private void publishBruteForceEvent(String sourceIp, String userAgent, String username) {
    SecurityEventPublisher publisher = securityEventPublisherProvider.getIfAvailable();
    if (publisher == null || sourceIp == null || sourceIp.isBlank()) {
      return;
    }
    publisher.publish(
        new SecurityEvent(
            SecurityEventType.BRUTE_FORCE,
            "/auth/login",
            sourceIp,
            userAgent,
            "Failed login for user: " + username,
            SecurityEvent.Severity.MEDIUM));
  }
}
