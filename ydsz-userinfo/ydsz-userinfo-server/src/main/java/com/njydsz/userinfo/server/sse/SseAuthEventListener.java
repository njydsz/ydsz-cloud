package com.njydsz.userinfo.server.sse;

import java.util.Map;

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

/**
 * 认证事件 SSE 推送监听器（P3-1）。
 *
 * <p>监听所有认证相关事件，通过 {@link SseEmitterRegistry} 实时推送给已连接的客户端。
 * 优先级 50（低优先级，在安全告警监听器之后执行，不影响核心安全流程）。
 *
 * <p><b>推送事件类型：</b>
 *
 * <ul>
 *   <li>{@code auth.login.success} — 登录成功（含设备信息）</li>
 *   <li>{@code auth.login.failed} — 登录失败（含失败原因）</li>
 *   <li>{@code auth.logout} — 注销</li>
 *   <li>{@code auth.mfa.triggered} — MFA 触发</li>
 *   <li>{@code auth.session.evicted} — 会话被驱逐（强制下线）</li>
 *   <li>{@code auth.account.locked} — 账号锁定</li>
 *   <li>{@code auth.account.banned} — 账号封禁</li>
 *   <li>{@code auth.password.changed} — 密码修改</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Order(50)
@Component
@RequiredArgsConstructor
public class SseAuthEventListener implements UserAuthEventListener {

  /** 监听器优先级 */
  private static final int LISTENER_ORDER = 50;

  private final SseEmitterRegistry emitterRegistry;

  @Override
  public void onLoginSuccess(LoginSuccessEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.login.success",
        Map.of(
            "username", event.username(),
            "sourceIp", event.sourceIp() != null ? event.sourceIp() : "",
            "deviceType", event.deviceType() != null ? event.deviceType() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onLoginFailed(LoginFailedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.login.failed",
        Map.of(
            "username", event.username(),
            "sourceIp", event.sourceIp() != null ? event.sourceIp() : "",
            "reason", event.reason() != null ? event.reason() : "",
            "failCount", event.failCount(),
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onLogout(LogoutEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.logout",
        Map.of(
            "username", event.username(),
            "sourceIp", event.sourceIp() != null ? event.sourceIp() : "",
            "sessionDuration", event.sessionDuration(),
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onMfaTriggered(MfaTriggeredEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.mfa.triggered",
        Map.of(
            "username", event.username(),
            "mfaType", event.mfaType(),
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onMfaVerified(MfaVerifiedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.mfa.verified",
        Map.of(
            "username", event.username(),
            "mfaType", event.mfaType(),
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onMfaFailed(MfaFailedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.mfa.failed",
        Map.of(
            "username", event.username(),
            "mfaType", event.mfaType(),
            "reason", event.reason() != null ? event.reason() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onAccountLocked(AccountLockedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.account.locked",
        Map.of(
            "username", event.username(),
            "lockDuration", event.lockDuration(),
            "reason", event.reason() != null ? event.reason() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onAccountUnlocked(AccountUnlockedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.account.unlocked",
        Map.of(
            "username", event.username(),
            "unlockedBy", event.unlockedBy() != null ? event.unlockedBy() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onSessionEvicted(SessionEvictedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.session.evicted",
        Map.of(
            "username", event.username(),
            "evictedBy", event.evictedBy() != null ? event.evictedBy() : "",
            "reason", event.reason() != null ? event.reason() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onPasswordChanged(PasswordChangedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.password.changed",
        Map.of(
            "username", event.username(),
            "changedBy", event.changedBy() != null ? event.changedBy() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onAccountBanned(AccountBannedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.account.banned",
        Map.of(
            "username", event.username(),
            "banType", event.banType(),
            "reason", event.reason() != null ? event.reason() : "",
            "bannedBy", event.bannedBy() != null ? event.bannedBy() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public void onAccountUnbanned(AccountUnbannedEvent event) {
    emitterRegistry.pushToUser(
        event.userId(),
        "auth.account.unbanned",
        Map.of(
            "username", event.username(),
            "unbannedBy", event.unbannedBy() != null ? event.unbannedBy() : "",
            "timestamp", event.timestamp().toString()));
  }

  @Override
  public int getOrder() {
    return LISTENER_ORDER;
  }
}
