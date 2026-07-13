package com.njydsz.pmis.common.sentry.logging;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.njydsz.pmis.common.sentry.domain.LogEvent;

/**
 * LogEvent JSON 序列化器
 *
 * <p>将 LogEvent 序列化为结构化 JSON 字符串，兼容 LogstashEncoder 格式。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public final class LogEventSerializer {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT;

    private LogEventSerializer() {
    }

    /**
     * 序列化为 JSON
     */
    public static String toJson(LogEvent event) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');

        // @timestamp
        appendField(sb, "@timestamp", ISO_FORMATTER.format(event.getTimestamp()), true);

        // level
        if (event.getLevel() != null) {
            sb.append(',');
            appendField(sb, "level", event.getLevel().name(), true);
        }

        // logger
        appendFieldNullable(sb, "logger_name", event.getLogger(), true);
        appendFieldNullable(sb, "thread_name", event.getThread(), true);
        appendFieldNullable(sb, "message", event.getMessage(), true);
        appendFieldNullable(sb, "app_name", event.getAppName(), true);
        appendFieldNullable(sb, "hostname", event.getHostname(), true);
        appendFieldNullable(sb, "profile", event.getProfile(), true);
        appendFieldNullable(sb, "traceId", event.getTraceId(), true);
        appendFieldNullable(sb, "userId", event.getUserId(), true);
        appendFieldNullable(sb, "username", event.getUsername(), true);

        // stack_trace
        if (event.getStackTrace() != null) {
            sb.append(',');
            appendField(sb, "stack_trace", event.getStackTrace(), true);
        }

        // extra fields
        Map<String, Object> extra = event.getExtra();
        if (extra != null && !extra.isEmpty()) {
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                sb.append(',');
                appendField(sb, entry.getKey(),
                        entry.getValue() != null ? entry.getValue().toString() : "null",
                        entry.getValue() instanceof String);
            }
        }

        sb.append('}');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean isString) {
        sb.append('"').append(escapeKey(key)).append("\":");
        if (isString) {
            sb.append('"').append(escapeValue(value)).append('"');
        } else {
            sb.append(value);
        }
    }

    private static void appendFieldNullable(StringBuilder sb, String key, String value, boolean isString) {
        if (value == null) {
            return;
        }
        sb.append(',');
        appendField(sb, key, value, isString);
    }

    private static String escapeKey(String str) {
        return str.replace("\"", "\\\"");
    }

    private static String escapeValue(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() + 16);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
