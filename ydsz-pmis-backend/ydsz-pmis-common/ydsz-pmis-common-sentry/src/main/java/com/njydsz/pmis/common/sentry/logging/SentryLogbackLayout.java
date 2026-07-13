package com.njydsz.pmis.common.sentry.logging;

import java.time.Instant;

import com.njydsz.pmis.common.sentry.domain.LogEvent;
import com.njydsz.pmis.common.sentry.domain.LogLevel;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;

/**
 * Sentry 统一 Logback Layout
 *
 * <p>将 Logback 日志事件转换为结构化 JSON 格式，兼容 LogstashEncoder 输出格式。
 * 自动注入 traceId / userId / username 等 MDC 字段。
 *
 * <p>使用方式（logback-spring.xml）：
 * <pre>
 * &lt;appender name="SENTRY_JSON" class="ch.qos.logback.core.ConsoleAppender"&gt;
 *   &lt;layout class="com.njydsz.pmis.common.sentry.logging.SentryLogbackLayout"&gt;
 *     &lt;appName&gt;pmis-service&lt;/appName&gt;
 *     &lt;profile&gt;${spring.profiles.active:-dev}&lt;/profile&gt;
 *   &lt;/layout&gt;
 * &lt;/appender&gt;
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class SentryLogbackLayout extends LayoutBase<ILoggingEvent> {

    private String appName = "pmis";
    private String hostname;
    private String profile = "dev";

    @Override
    public String doLayout(ILoggingEvent event) {
        LogEvent logEvent = convertEvent(event);
        return LogEventSerializer.toJson(logEvent) + "\n";
    }

    private LogEvent convertEvent(ILoggingEvent event) {
        LogEvent.LogEventBuilder builder = LogEvent.builder()
                .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
                .level(convertLevel(event.getLevel()))
                .logger(event.getLoggerName())
                .thread(event.getThreadName())
                .message(event.getFormattedMessage())
                .appName(appName)
                .hostname(hostname != null ? hostname : detectHostname())
                .profile(profile);

        // MDC 字段提取
        if (event.getMDCPropertyMap() != null) {
            builder.traceId(event.getMDCPropertyMap().get("traceId"));
            builder.userId(event.getMDCPropertyMap().get("userId"));
            builder.username(event.getMDCPropertyMap().get("username"));
        }

        // 异常堆栈
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            builder.stackTrace(formatStackTrace(throwableProxy));
        }

        return builder.build();
    }

    private LogLevel convertLevel(ch.qos.logback.classic.Level level) {
        if (level == null) {
            return LogLevel.INFO;
        }
        switch (level.levelInt) {
            case ch.qos.logback.classic.Level.TRACE_INT:
                return LogLevel.TRACE;
            case ch.qos.logback.classic.Level.DEBUG_INT:
                return LogLevel.DEBUG;
            case ch.qos.logback.classic.Level.INFO_INT:
                return LogLevel.INFO;
            case ch.qos.logback.classic.Level.WARN_INT:
                return LogLevel.WARN;
            case ch.qos.logback.classic.Level.ERROR_INT:
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
            return java.net.InetAddress.getLocalHost().getHostName();
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
}
