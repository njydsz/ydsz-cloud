package com.njydsz.userinfo.server.event;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 用户认证事件分发器。
 *
 * <p>收集所有 {@link UserAuthEventListener} 实现（Spring 自动注入 {@code List}），
 * 按 {@link UserAuthEventListener#getOrder()} 升序排列后同步分发事件。
 *
 * <p><b>异常隔离：</b>单个监听器抛出异常不会影响其他监听器，异常会被捕获并记录 WARN 日志后继续执行。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * dispatch.publish(new LoginSuccessEvent(userId, username, LocalDateTime.now(), ip, ua, "PC"));
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthEventDispatcher {

  private final List<UserAuthEventListener> listeners;

  /**
   * 发布事件（同步调用所有监听器）。
   *
   * <p>监听器按 {@link UserAuthEventListener#getOrder()} 升序执行。若某个监听器抛出异常，捕获并记录 WARN 日志后继续执行后续监听器。
   *
   * @param event 认证事件对象
   * @param <T> 事件类型
   */
  public <T> void publish(T event) {
    listeners.stream()
        .sorted(Comparator.comparingInt(UserAuthEventListener::getOrder))
        .forEach(listener -> dispatchToListener(listener, event));
  }

  /**
   * 发布事件（异步，不阻塞主流程）。
   *
   * <p>使用 {@link CompletableFuture#runAsync()} 在公共 ForkJoinPool 中执行，适用于非关键路径事件（如指标统计）。
   *
   * @param event 认证事件对象
   * @param <T> 事件类型
   * @return CompletableFuture，可用于链式处理或异常回调
   */
  public <T> CompletableFuture<Void> publishAsync(T event) {
    return CompletableFuture.runAsync(() -> publish(event));
  }

  /**
   * 将单个事件分发到单个监听器，按事件类型调用对应方法。
   *
   * @param listener 监听器实例
   * @param event 事件对象
   */
  private void dispatchToListener(UserAuthEventListener listener, Object event) {
    try {
      switch (event) {
        case LoginSuccessEvent e -> listener.onLoginSuccess(e);
        case LoginFailedEvent e -> listener.onLoginFailed(e);
        case LogoutEvent e -> listener.onLogout(e);
        case MfaTriggeredEvent e -> listener.onMfaTriggered(e);
        case MfaVerifiedEvent e -> listener.onMfaVerified(e);
        case MfaFailedEvent e -> listener.onMfaFailed(e);
        case AccountLockedEvent e -> listener.onAccountLocked(e);
        case AccountUnlockedEvent e -> listener.onAccountUnlocked(e);
        case SessionEvictedEvent e -> listener.onSessionEvicted(e);
        case PasswordChangedEvent e -> listener.onPasswordChanged(e);
        case AccountBannedEvent e -> listener.onAccountBanned(e);
        case AccountUnbannedEvent e -> listener.onAccountUnbanned(e);
        default -> log.warn("未知事件类型，跳过分发: {}", event.getClass().getName());
      }
    } catch (Exception e) {
      log.warn(
          "事件监听器执行异常: listener={}, event={}",
          listener.getClass().getSimpleName(),
          event.getClass().getSimpleName(),
          e);
    }
  }
}
