package com.njydsz.userinfo.server.event.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.event.UserAuthEventListener;
import com.njydsz.userinfo.domain.event.auth.AccountBannedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountLockedEvent;
import com.njydsz.userinfo.domain.event.auth.MfaFailedEvent;

/**
 * 安全告警事件监听器。
 *
 * <p>监听账号锁定、账号封禁和 MFA 验证失败事件，触发安全告警通知。 实际项目中可对接企业微信/钉钉/邮件等告警渠道。
 *
 * <p>优先级 20（高优先级，安全事件需要及时响应）。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Slf4j
@Order(20)
@Component
public class SecurityAlertEventListener implements UserAuthEventListener {

  @Override
  public void onAccountLocked(AccountLockedEvent event) {
    log.warn(
        "安全告警-账号锁定: userId={}, username={}, lockDuration={}, reason={}, timestamp={}",
        event.userId(),
        event.username(),
        event.lockDuration(),
        event.reason(),
        event.timestamp());
  }

  @Override
  public void onAccountBanned(AccountBannedEvent event) {
    log.warn(
        "安全告警-账号封禁: userId={}, username={}, banType={}, reason={}, bannedBy={}, timestamp={}",
        event.userId(),
        event.username(),
        event.banType(),
        event.reason(),
        event.bannedBy(),
        event.timestamp());
  }

  @Override
  public void onMfaFailed(MfaFailedEvent event) {
    log.warn(
        "安全告警-MFA验证失败: userId={}, username={}, mfaType={}, reason={}, timestamp={}",
        event.userId(),
        event.username(),
        event.mfaType(),
        event.reason(),
        event.timestamp());
  }

  @Override
  public int getOrder() {
    return 20;
  }
}
