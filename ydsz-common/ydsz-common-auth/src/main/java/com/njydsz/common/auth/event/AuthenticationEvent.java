package com.njydsz.common.auth.event;

import java.io.Serializable;

import org.springframework.context.ApplicationEvent;

/**
 * 认证事件基类。
 *
 * <p>所有认证域事件（登录、登出、Token 刷新、密码变更等）的父类， 继承 Spring {@link ApplicationEvent} 以复用 Spring 事件机制（同步/异步监听、事务绑定等）。
 *
 * <p>认证域事件预留体系，业务方可通过 Spring {@code @EventListener} 订阅并扩展。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public abstract class AuthenticationEvent extends ApplicationEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 事件时间戳（毫秒）。 */
  private final long timestamp;

  /**
   * 构造认证事件。
   *
   * @param source 事件源标识（节点 ID 或类名）
   */
  protected AuthenticationEvent(String source) {
    super(source != null ? source : AuthenticationEvent.class.getName());
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 获取事件时间戳。
   *
   * @return 时间戳（毫秒）
   */
  public long getEventTimestamp() {
    return timestamp;
  }

  /**
   * 获取事件类型标识。
   *
   * @return 事件类型名（简单类名）
   */
  public String getEventType() {
    return getClass().getSimpleName();
  }
}
