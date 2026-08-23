package com.njydsz.userinfo.server.event.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.event.UserAuthEventListener;
import com.njydsz.userinfo.domain.event.auth.AccountBannedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountLockedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountUnbannedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountUnlockedEvent;
import com.njydsz.userinfo.domain.event.auth.LoginFailedEvent;
import com.njydsz.userinfo.domain.event.auth.LoginSuccessEvent;
import com.njydsz.userinfo.domain.event.auth.LogoutEvent;
import com.njydsz.userinfo.domain.event.auth.MfaFailedEvent;
import com.njydsz.userinfo.domain.event.auth.MfaTriggeredEvent;
import com.njydsz.userinfo.domain.event.auth.MfaVerifiedEvent;
import com.njydsz.userinfo.domain.event.auth.PasswordChangedEvent;
import com.njydsz.userinfo.domain.event.auth.SessionEvictedEvent;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;

/**
 * 认证指标事件监听器。
 *
 * <p>监听所有认证事件，通过 {@link UserInfoMetrics} 记录认证指标统计。
 * 实际项目中可对接 Micrometer 输出到 Prometheus 等监控系统。
 *
 * <p>优先级 200（低优先级，指标统计不影响关键业务逻辑）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Order(200)
@Component
@RequiredArgsConstructor
public class MetricsEventListener implements UserAuthEventListener {

  /** 监听器优先级（越小越先执行） */
  private static final int LISTENER_ORDER = 200;


  private final UserInfoMetrics userInfoMetrics;

  @Override
  public void onLoginSuccess(LoginSuccessEvent event) {
    log.debug("认证指标-登录成功: userId={}, deviceType={}", event.userId(), event.deviceType());
    userInfoMetrics.recordLoginSuccess();
  }

  @Override
  public void onLoginFailed(LoginFailedEvent event) {
    log.debug("认证指标-登录失败: userId={}, reason={}", event.userId(), event.reason());
    userInfoMetrics.recordLoginFail();
  }

  @Override
  public void onLogout(LogoutEvent event) {
    log.debug("认证指标-注销: userId={}, sessionDuration={}", event.userId(), event.sessionDuration());
    userInfoMetrics.recordLogout();
  }

  @Override
  public void onMfaTriggered(MfaTriggeredEvent event) {
    log.debug("认证指标-MFA触发: userId={}, mfaType={}", event.userId(), event.mfaType());
  }

  @Override
  public void onMfaVerified(MfaVerifiedEvent event) {
    log.debug("认证指标-MFA验证成功: userId={}, mfaType={}", event.userId(), event.mfaType());
  }

  @Override
  public void onMfaFailed(MfaFailedEvent event) {
    log.debug("认证指标-MFA验证失败: userId={}, mfaType={}", event.userId(), event.mfaType());
  }

  @Override
  public void onAccountLocked(AccountLockedEvent event) {
    log.debug("认证指标-账号锁定: userId={}", event.userId());
  }

  @Override
  public void onAccountUnlocked(AccountUnlockedEvent event) {
    log.debug("认证指标-账号解锁: userId={}", event.userId());
  }

  @Override
  public void onSessionEvicted(SessionEvictedEvent event) {
    log.debug("认证指标-会话驱逐: userId={}, evictedBy={}", event.userId(), event.evictedBy());
  }

  @Override
  public void onPasswordChanged(PasswordChangedEvent event) {
    log.debug("认证指标-密码修改: userId={}, changedBy={}", event.userId(), event.changedBy());
  }

  @Override
  public void onAccountBanned(AccountBannedEvent event) {
    log.debug("认证指标-账号封禁: userId={}, banType={}", event.userId(), event.banType());
  }

  @Override
  public void onAccountUnbanned(AccountUnbannedEvent event) {
    log.debug("认证指标-账号解封: userId={}", event.userId());
  }

  @Override
  public int getOrder() {
    return LISTENER_ORDER;
  }
}
