package com.njydsz.common.sentry.logging;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.sentry.domain.LogEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LogEvent JSON 序列化器
 *
 * <p>将 LogEvent 序列化为结构化 JSON 字符串，兼容 LogstashEncoder 格式。 底层委托 {@link YdszJson} 统一 JSON 引擎。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LogEventSerializer {

  private LogEventSerializer() {}

  /** 序列化为 JSON */
  public static String toJson(LogEvent event) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("@timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : null);

    if (event.getLevel() != null) {
      map.put("level", event.getLevel().name());
    }
    putIfNotNull(map, "logger_name", event.getLogger());
    putIfNotNull(map, "thread_name", event.getThread());
    putIfNotNull(map, "message", event.getMessage());
    putIfNotNull(map, "app_name", event.getAppName());
    putIfNotNull(map, "hostname", event.getHostname());
    putIfNotNull(map, "profile", event.getProfile());
    putIfNotNull(map, "traceId", event.getTraceId());
    putIfNotNull(map, "userId", event.getUserId());
    putIfNotNull(map, "username", event.getUsername());
    putIfNotNull(map, "stack_trace", event.getStackTrace());

    if (event.getExtra() != null && !event.getExtra().isEmpty()) {
      for (Map.Entry<String, Object> entry : event.getExtra().entrySet()) {
        map.put(entry.getKey(), entry.getValue());
      }
    }

    return YdszJson.toJson(map);
  }

  private static void putIfNotNull(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
