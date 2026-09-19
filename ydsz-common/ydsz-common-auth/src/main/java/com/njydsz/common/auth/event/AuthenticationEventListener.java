package com.njydsz.common.auth.event;

/**
 * 认证事件监听器标记接口。
 *
 * <p>业务方实现此接口并在方法上添加 {@code @EventListener} 注解（或 {@code @Async + @EventListener} 异步监听），即可订阅认证域事件。
 *
 * <p>推荐实现方式（利用 Spring 原生 {@code @EventHandler} 分发）：
 *
 * <pre>{@code
 * &#64;Component
 * public class AuditAuthEventListener {
 *   &#64;EventListener
 *   public void onLoginSuccess(LoginSuccessEvent event) {
 *     // 记录登录审计日志
 *   }
 *
 *   &#64;EventListener
 *   public void onLoginFailure(LoginFailureEvent event) {
 *     // 写入失败登录日志（防暴力破解）
 *   }
 *
 *   &#64;EventListener
 *   public void onTokenRevoke(TokenRevokeEvent event) {
 *     // 清理用户会话资源
 *   }
 *
 *   // 异步示例：@Async + @EventListener 需配合 @EnableAsync 使用
 *   &#64;Async
 *   &#64;EventListener
 *   public void onLoginSuccessAsync(LoginSuccessEvent event) {
 *     // 异步发送登录通知
 *   }
 * }
 * }</pre>
 *
 * <p>注：此接口为标记接口，无任何约束方法。仅用于统一监听器的 SPI 契约类型。
 *
 * @author ydsz-team
 * @since 26.09.18
 * @see AuthenticationEventPublisher
 * @see AuthenticationEvent
 */
public interface AuthenticationEventListener {
  // 标记接口，业务监听器通过 Spring @EventListener 注解订阅特定事件类型
}
