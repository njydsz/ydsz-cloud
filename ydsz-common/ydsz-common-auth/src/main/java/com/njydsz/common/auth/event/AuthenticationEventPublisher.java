package com.njydsz.common.auth.event;

/**
 * 认证事件发布器。
 *
 * <p>封装事件发布逻辑，业务方通过此接口发布认证域事件（登录成功/失败、Token 撤销等）。 默认实现 {@link SpringAuthenticationEventPublisher} 基于 Spring
 * {@link org.springframework.context.ApplicationEventPublisher} 广播事件。
 *
 * <p>业务模块在需要触发事件的路径（认证过滤器、登出接口、密码修改服务）中注入此接口并调用对应事件发布方法。
 *
 * @author ydsz-team
 * @since 26.09.18
 * @see AuthenticationEventListener
 * @see AuthenticationEvent
 */
public interface AuthenticationEventPublisher {

  /**
   * 发布登录成功事件。
   *
   * @param event 事件实例（不可为 null）
   */
  void publishLoginSuccess(LoginSuccessEvent event);

  /**
   * 发布登录失败事件。
   *
   * @param event 事件实例（不可为 null）
   */
  void publishLoginFailure(LoginFailureEvent event);

  /**
   * 发布 Token 撤销事件。
   *
   * @param event 事件实例（不可为 null）
   */
  void publishTokenRevoke(TokenRevokeEvent event);
}
