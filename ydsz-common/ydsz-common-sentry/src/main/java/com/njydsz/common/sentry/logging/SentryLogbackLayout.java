package com.njydsz.common.sentry.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;
import com.njydsz.common.sentry.domain.LogEvent;
import com.njydsz.common.sentry.domain.LogLevel;
import java.net.InetAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sentry 统一 Logback Layout
 *
 * <p>将 Logback 日志事件转换为结构化 JSON 格式，兼容 LogstashEncoder 输出格式。 自动注入 traceId / userId / username 等 MDC
 * 字段。
 *
 * <p>使用方式（logback-spring.xml）：
 *
 * <pre>
 * &lt;appender name="SENTRY_JSON" class="ch.qos.logback.core.ConsoleAppender"&gt;
 *   &lt;layout class="com.njydsz.common.sentry.logging.SentryLogbackLayout"&gt;
 *     &lt;appName&gt;ydsz-service&lt;/appName&gt;
 *     &lt;profile&gt;${spring.profiles.active:-dev}&lt;/profile&gt;
 *   &lt;/layout&gt;
 * &lt;/appender&gt;
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SentryLogbackLayout extends LayoutBase<ILoggingEvent> {

  private String appName = "ydsz";
  private String hostname;
  private String profile = "dev";
  private String mdcFields = "traceId,userId,username";

  @Override
  public void start() {
    if (appName == null || appName.isBlank()) {
      appName = "ydsz";
    }
    if (hostname == null || hostname.isBlank()) {
      hostname = detectHostname();
    }
    super.start();
  }

  @Override
  public String doLayout(ILoggingEvent event) {
    LogEvent logEvent = convertEvent(event);
    return LogEventSerializer.toJson(logEvent) + "\n";
  }

  private LogEvent convertEvent(ILoggingEvent event) {
    LogEvent.LogEventBuilder builder =
        LogEvent.builder()
            .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
            .level(convertLevel(event.getLevel()))
            .logger(event.getLoggerName())
            .thread(event.getThreadName())
            .message(event.getFormattedMessage())
            .appName(appName)
            .hostname(hostname)
            .profile(profile);

    // MDC 字段提取（支持可配置字段列表）
    if (event.getMDCPropertyMap() != null) {
      builder.traceId(event.getMDCPropertyMap().get("traceId"));
      builder.userId(event.getMDCPropertyMap().get("userId"));
      builder.username(event.getMDCPropertyMap().get("username"));
      // 可配置的额外 MDC 字段
      if (mdcFields != null && !mdcFields.isBlank()) {
        Map<String, Object> extraMdc = new LinkedHashMap<>();
        for (String field : mdcFields.split(",")) {
          String trimmed = field.trim();
          if (!trimmed.isEmpty()
              && !"traceId".equals(trimmed)
              && !"userId".equals(trimmed)
              && !"username".equals(trimmed)) {
            String value = event.getMDCPropertyMap().get(trimmed);
            if (value != null) {
              extraMdc.put(trimmed, value);
            }
          }
        }
        if (!extraMdc.isEmpty()) {
          builder.extra(extraMdc);
        }
      }
    }

    // 异常堆栈
    IThrowableProxy throwableProxy = event.getThrowableProxy();
    if (throwableProxy != null) {
      builder.stackTrace(formatStackTrace(throwableProxy));
    }

    return builder.build();
  }

  private LogLevel convertLevel(Level level) {
    if (level == null) {
      return LogLevel.INFO;
    }
    switch (level.levelInt) {
      case Level.TRACE_INT:
        return LogLevel.TRACE;
      case Level.DEBUG_INT:
        return LogLevel.DEBUG;
      case Level.INFO_INT:
        return LogLevel.INFO;
      case Level.WARN_INT:
        return LogLevel.WARN;
      case Level.ERROR_INT:
        return LogLevel.ERROR;
      default:
        return LogLevel.INFO;
    }
  }

  private String formatStackTrace(IThrowableProxy proxy) {
    if (proxy == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(512);
    sb.append(proxy.getClassName()).append(": ").append(proxy.getMessage()).append('\n');
    if (proxy.getStackTraceElementProxyArray() != null) {
      for (StackTraceElementProxy step : proxy.getStackTraceElementProxyArray()) {
        sb.append("\tat ").append(step.toString()).append('\n');
      }
    }
    return sb.toString();
  }

  private String detectHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public void setProfile(String profile) {
    this.profile = profile;
  }

  public void setMdcFields(String mdcFields) {
    this.mdcFields = mdcFields;
  }
}
