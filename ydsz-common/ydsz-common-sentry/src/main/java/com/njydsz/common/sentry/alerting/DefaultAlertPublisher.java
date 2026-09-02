package com.njydsz.common.sentry.alerting;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.common.sentry.spi.AlertPublisher;

/**
 * 默认告警发布器
 *
 * <p>将告警事件转发到 Prometheus Alertmanager 或直接记录日志。 支持告警级别路由。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class DefaultAlertPublisher implements AlertPublisher {

  private final Map<AlertSeverity, List<AlertHandler>> handlers = new ConcurrentHashMap<>();
  private final boolean logAlerts;

  /**
   * default alert publisher。
   * @param logAlerts 参数
 */
  public DefaultAlertPublisher(boolean logAlerts) {
    this.logAlerts = logAlerts;
    log.info("[Sentry] DefaultAlertPublisher 初始化: logAlerts={}", logAlerts);
  }

  /** 注册告警处理器 */
  /**
   * register handler。
   * @param severity 参数
   * @param handler 参数
   */
  public void registerHandler(AlertSeverity severity, AlertHandler handler) {
    handlers.computeIfAbsent(severity, s -> new CopyOnWriteArrayList<>()).add(handler);
    log.info(
        "[Sentry] 告警处理器注册: severity={}, handler={}", severity, handler.getClass().getSimpleName());
  }

  @Override
  /**
   * publish。
   * @param event 参数
   * @return 结果
   */
  public boolean publish(AlertEvent event) {
    if (logAlerts) {
      log.warn(
          "[Sentry] 告警触发: name={}, severity={}, summary={}",
          event.getName(),
          event.getSeverity(),
          event.getSummary());
    }

    List<AlertHandler> severityHandlers = handlers.get(event.getSeverity());
    if (severityHandlers == null || severityHandlers.isEmpty()) {
      return true;
    }

    for (AlertHandler handler : severityHandlers) {
      try {
        handler.handle(event);
      } catch (Exception e) {
        log.error(
            "[Sentry] 告警处理器异常: handler={}, err={}",
            handler.getClass().getSimpleName(),
            e.getMessage());
      }
    }
    return true;
  }

  @Override
  /**
   * is available。
   * @return 结果
   */
  public boolean isAvailable() {
    return true;
  }

  @Override
  /**
   * get name。
   * @return 结果
   */
  public String getName() {
    return "default-alert-publisher";
  }

  /** 告警处理器接口 */
  @FunctionalInterface
  public interface AlertHandler {
    void handle(AlertEvent event);
  }
}
