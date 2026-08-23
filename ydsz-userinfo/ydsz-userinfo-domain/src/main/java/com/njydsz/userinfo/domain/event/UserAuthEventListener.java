package com.njydsz.userinfo.domain.event;

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
 * 用户认证事件全局监听器接口。
 *
 * <p>统一处理所有认证相关事件，实现类可注册为 Spring Bean 自动被收集。 监听器按 {@link #getOrder()} 值升序执行（值越小优先级越高）。
 *
 * <p>所有方法均为 {@code default} 空实现，实现类只需覆盖感兴趣的事件方法，无需实现全部。
 *
 * <p><b>注册方式：</b>实现本接口并标注 {@code @Component}，Spring 自动注入到
 * {@code UserAuthEventDispatcher} 的 {@code List<UserAuthEventListener>} 中。
 *
 * <p><b>异常隔离：</b>各监听器异常不会相互影响，{@code UserAuthEventDispatcher} 会捕获并记录异常日志后继续执行后续监听器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserAuthEventListener {

  /**
   * 登录成功
   *
   * @param event LoginSuccess 事件对象
   */
  default void onLoginSuccess(LoginSuccessEvent event) {}

  /**
   * 登录失败
   *
   * @param event LoginFailed 事件对象
   */
  default void onLoginFailed(LoginFailedEvent event) {}

  /**
   * 注销
   *
   * @param event Logout 事件对象
   */
  default void onLogout(LogoutEvent event) {}

  /**
   * MFA 触发
   *
   * @param event MfaTriggered 事件对象
   */
  default void onMfaTriggered(MfaTriggeredEvent event) {}

  /**
   * MFA 验证成功
   *
   * @param event MfaVerified 事件对象
   */
  default void onMfaVerified(MfaVerifiedEvent event) {}

  /**
   * MFA 验证失败
   *
   * @param event MfaFailed 事件对象
   */
  default void onMfaFailed(MfaFailedEvent event) {}

  /**
   * 账号锁定
   *
   * @param event AccountLocked 事件对象
   */
  default void onAccountLocked(AccountLockedEvent event) {}

  /**
   * 账号解锁
   *
   * @param event AccountUnlocked 事件对象
   */
  default void onAccountUnlocked(AccountUnlockedEvent event) {}

  /**
   * 会话驱逐
   *
   * @param event SessionEvicted 事件对象
   */
  default void onSessionEvicted(SessionEvictedEvent event) {}

  /**
   * 密码修改
   *
   * @param event PasswordChanged 事件对象
   */
  default void onPasswordChanged(PasswordChangedEvent event) {}

  /**
   * 账号封禁
   *
   * @param event AccountBanned 事件对象
   */
  default void onAccountBanned(AccountBannedEvent event) {}

  /**
   * 账号解封
   *
   * @param event AccountUnbanned 事件对象
   */
  default void onAccountUnbanned(AccountUnbannedEvent event) {}

  /**
   * 获取监听器优先级（越小优先级越高，默认 100）。
   *
   * @return 优先级值
   */
  default int getOrder() {
    return 100;
  }
}
