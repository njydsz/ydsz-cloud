package com.njydsz.common.auth.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring ApplicationEventPublisher 的认证事件发布器实现。
 *
 * <p>通过 Spring 原生事件机制传播事件，支持：
 *
 * <ul>
 *   <li>同步监听（默认）：监听器务在事件发布线程执行，异常可回溯</li>
 *   <li>异步监听（@Async）：通过配置 @EnableAsync + ThreadPoolTaskExecutor 实现非阻塞分发</li>
 *   <li>事务绑定（@TransactionalEventListener）：监听器可在事务提交后执行</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Component
@ConditionalOnBean(ApplicationEventPublisher.class)
public class SpringAuthenticationEventPublisher implements AuthenticationEventPublisher {

  private static final Logger LOG =
      LoggerFactory.getLogger(SpringAuthenticationEventPublisher.class);

  private final ApplicationEventPublisher eventPublisher;

  public SpringAuthenticationEventPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void publishLoginSuccess(LoginSuccessEvent event) {
    if (event == null) {
      return;
    }
    LOG.debug("[AuthEvent] 发布登录成功事件: userId={}, userType={}", event.getUserId(), event.getUserType());
    eventPublisher.publishEvent(event);
  }

  @Override
  public void publishLoginFailure(LoginFailureEvent event) {
    if (event == null) {
      return;
    }
    LOG.debug("[AuthEvent] 发布登录失败事件: username={}, reason={}", event.getUsername(), event.getReason());
    eventPublisher.publishEvent(event);
  }

  @Override
  public void publishTokenRevoke(TokenRevokeEvent event) {
    if (event == null) {
      return;
    }
    LOG.debug("[AuthEvent] 发布 Token 撤销事件: userId={}, reason={}", event.getUserId(), event.getReason());
    eventPublisher.publishEvent(event);
  }
}
