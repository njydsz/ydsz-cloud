package com.njydsz.common.thread.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.thread.alarm.AlarmEvent.Severity;

/**
 * 默认线程池告警通知器（仅打印 warn/critical 日志）。
 *
 * <p>作为 {@link AlarmNotifier} 的零依赖 fallback 实现，确保在无业务自定义 Bean 时告警事件仍能被 SLF4J 日志框架捕获。
 *
 * <p>生产环境建议替换为对接企微/钉钉/邮件通道的实现类。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see AlarmNotifier
 */
public class DefaultAlarmNotifier implements AlarmNotifier {

  private static final Logger WARN_LOG = LoggerFactory.getLogger("ydsz.thread.alarm.warn");
  private static final Logger CRITICAL_LOG = LoggerFactory.getLogger("ydsz.thread.alarm.critical");

  @Override
  public void notify(AlarmEvent event) {
    if (event == null) {
      return;
    }
    String logMessage = "[ydsz-thread-alarm] pool={}, dimension={}, severity={}, current={}, threshold={}, message={}";
    if (event.getSeverity() == Severity.CRITICAL) {
      CRITICAL_LOG.warn(
          logMessage,
          event.getPoolName(),
          event.getDimension(),
          event.getSeverity(),
          event.getCurrentValue(),
          event.getThreshold(),
          event.getMessage());
    } else {
      WARN_LOG.warn(
          logMessage,
          event.getPoolName(),
          event.getDimension(),
          event.getSeverity(),
          event.getCurrentValue(),
          event.getThreshold(),
          event.getMessage());
    }
  }
}
