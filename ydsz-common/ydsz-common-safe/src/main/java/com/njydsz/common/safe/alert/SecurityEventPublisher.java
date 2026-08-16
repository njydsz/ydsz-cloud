package com.njydsz.common.safe.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * 安全事件发布器
 *
 * <p>通过 Spring {@link ApplicationEventPublisher} 发布事件， 同时通过 {@link ServiceLoader} 调用所有 SPI 实现的监听器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SecurityEventPublisher implements ApplicationEventPublisherAware {

  private static final Logger log = LoggerFactory.getLogger(SecurityEventPublisher.class);

  private final List<SecurityAlertListener> spiListeners;
  private ApplicationEventPublisher applicationEventPublisher;

  /** 最近一次发布耗时（纳秒），用于监控 */
  private volatile long lastPublishNanos;

  /** 构造方法 */
  public SecurityEventPublisher() {
    this.spiListeners = loadSpiListeners();
  }

  @Override
  public void setApplicationEventPublisher(
      @NonNull ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  /**
   * 发布安全事件
   *
   * @param event 安全事件
   */
  public void publish(@Nullable SecurityEvent event) {
    if (event == null) {
      return;
    }

    long startNanos = System.nanoTime();

    // 发布 Spring 应用事件
    if (applicationEventPublisher != null) {
      applicationEventPublisher.publishEvent(event);
    }

    // 调用 SPI 监听器
    for (SecurityAlertListener listener : spiListeners) {
      try {
        listener.onSecurityEvent(event);
      } catch (Exception e) {
        log.warn("安全事件监听器处理异常: {}", listener.getClass().getName(), e);
      }
    }

    lastPublishNanos = System.nanoTime() - startNanos;
  }

  /**
   * 最近一次发布耗时（纳秒）
   *
   * @return 发布耗时（纳秒）
   */
  public long getLastPublishNanos() {
    return lastPublishNanos;
  }

  private List<SecurityAlertListener> loadSpiListeners() {
    List<SecurityAlertListener> listeners = new ArrayList<>();
    ServiceLoader<SecurityAlertListener> loader = ServiceLoader.load(SecurityAlertListener.class);
    for (SecurityAlertListener listener : loader) {
      listeners.add(listener);
      log.info("加载安全事件告警监听器: {}", listener.getClass().getName());
    }
    return listeners;
  }
}
