package com.njydsz.common.jdbc.monitor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL 审计 JSON 格式日志输出器
 *
 * <p>将 SQL 审计信息输出为结构化 JSON 格式，便于 ELK/Loki 等日志系统解析和检索。
 *
 * <p><b>输出字段：</b>
 *
 * <ul>
 *   <li>{@code timestamp} — ISO-8601 时间戳
 *   <li>{@code sql_id} — MyBatis MappedStatement ID
 *   <li>{@code command_type} — SQL 类型（SELECT/INSERT/UPDATE/DELETE）
 *   <li>{@code elapsed_ms} — 执行耗时（毫秒）
 *   <li>{@code affected_rows} — 影响行数
 *   <li>{@code sql_fingerprint} — SQL 指纹（归一化）
 *   <li>{@code sql} — 完整 SQL 语句
 *   <li>{@code exception} — 异常信息（存在时）
 *   <li>{@code stack_trace} — 异常堆栈（存在时）
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * SqlAuditJsonLogger.log(auditLog, "com.example.Mapper.select", "SELECT ...", elapsed);
 * }</pre>
 *
 * <p>日志 logger name: {@code sql.audit.json} — 可独立配置 appender 输出到专用文件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqlAuditJsonLogger {

  /** 审计 JSON 日志专用 Logger，可独立配置 appender */
  private static final Logger AUDIT_JSON_LOG = LoggerFactory.getLogger("sql.audit.json");

  /** ISO-8601 时间格式 */
  private static final DateTimeFormatter ISO_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

  /** JSON 特殊字符转义 */
  private static final char ESCAPE_QUOTE = '"';

  private static final char ESCAPE_BACKSLASH = '\\';
  private static final char ESCAPE_NEWLINE = '\n';
  private static final char ESCAPE_TAB = '\t';
  private static final char ESCAPE_CR = '\r';

  /**
   * 输出结构化 JSON 审计日志
   *
   * @param sqlId MappedStatement ID
   * @param commandType SQL 类型
   * @param sql 完整 SQL 语句
   * @param elapsedMs 执行耗时（毫秒）
   * @param affectedRows 影响行数
   * @param parameter SQL 参数
   */
  public static void log(
      String sqlId,
      String commandType,
      String sql,
      long elapsedMs,
      Integer affectedRows,
      Object parameter) {
    if (!AUDIT_JSON_LOG.isInfoEnabled()) {
      return;
    }
    try {
      StringBuilder json = new StringBuilder(256);
      json.append('{');
      appendField(json, "timestamp", LocalDateTime.now().format(ISO_FORMATTER));
      appendField(json, "sql_id", sqlId);
      appendField(json, "command_type", commandType);
      appendField(json, "elapsed_ms", elapsedMs);
      appendField(json, "affected_rows", affectedRows);
      appendField(json, "sql_fingerprint", SqlFingerprint.fingerprint(sql));
      appendField(json, "sql", sql);
      appendField(json, "parameter", formatParameter(parameter));
      json.append('}');
      AUDIT_JSON_LOG.info(json.toString());
    } catch (Exception e) {
      AUDIT_JSON_LOG.warn("JSON 审计日志构建失败: {}", e.getMessage());
    }
  }

  /**
   * 输出异常场景的审计日志
   *
   * @param sqlId MappedStatement ID
   * @param commandType SQL 类型
   * @param sql 完整 SQL 语句
   * @param elapsedMs 执行耗时（毫秒）
   * @param exception 执行异常
   */
  public static void logError(
      String sqlId, String commandType, String sql, long elapsedMs, Throwable exception) {
    if (!AUDIT_JSON_LOG.isErrorEnabled()) {
      return;
    }
    try {
      StringBuilder json = new StringBuilder(512);
      json.append('{');
      appendField(json, "timestamp", LocalDateTime.now().format(ISO_FORMATTER));
      appendField(json, "sql_id", sqlId);
      appendField(json, "command_type", commandType);
      appendField(json, "elapsed_ms", elapsedMs);
      appendField(json, "status", "ERROR");
      appendField(json, "sql_fingerprint", SqlFingerprint.fingerprint(sql));
      appendField(json, "sql", sql);
      String message = exception != null ? exception.getMessage() : null;
      appendField(json, "exception", message);
      appendField(json, "stack_trace", getStackTraceString(exception));
      json.append('}');
      AUDIT_JSON_LOG.error(json.toString());
    } catch (Exception e) {
      AUDIT_JSON_LOG.warn("JSON 审计日志构建失败: {}", e.getMessage());
    }
  }

  // ====================================================================
  // 内部方法
  // ====================================================================

  /**
   * 追加一个 JSON 字段（字符串类型）
   *
   * @param json StringBuilder
   * @param key 字段名
   * @param value 字段值（字符串类型，会被转义）
   */
  private static void appendField(StringBuilder json, String key, String value) {
    json.append(',').append('"').append(escapeJson(key)).append("\":");
    json.append(value == null ? "null" : '"' + escapeJson(value) + '"');
  }

  /**
   * 追加一个 JSON 字段（数值类型）
   *
   * @param json StringBuilder
   * @param key 字段名
   * @param value 数值
   */
  private static void appendField(StringBuilder json, String key, long value) {
    json.append(',').append('"').append(escapeJson(key)).append("\":").append(value);
  }

  /**
   * 追加一个 JSON 字段（整数类型，可为 null）
   *
   * @param json StringBuilder
   * @param key 字段名
   * @param value 整数值（可为 null）
   */
  private static void appendField(StringBuilder json, String key, Integer value) {
    json.append(',').append('"').append(escapeJson(key)).append("\":");
    json.append(value == null ? "null" : value);
  }

  /**
   * 追加一个 JSON 字段（Map 类型，作为嵌套 JSON 对象）
   *
   * @param json StringBuilder
   * @param key 字段名
   * @param value Map 值
   */
  private static void appendField(StringBuilder json, String key, Map<String, ?> value) {
    json.append(',').append('"').append(escapeJson(key)).append("\":");
    if (value == null) {
      json.append("null");
    } else {
      json.append('{');
      boolean first = true;
      for (Map.Entry<String, ?> entry : value.entrySet()) {
        if (!first) {
          json.append(',');
        }
        first = false;
        appendField(
            json,
            entry.getKey(),
            entry.getValue() != null ? entry.getValue().toString() : null);
      }
      json.append('}');
    }
  }

  /**
   * 转义 JSON 特殊字符
   *
   * @param value 原始字符串
   * @return 转义后的字符串
   */
  private static String escapeJson(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case ESCAPE_QUOTE -> sb.append("\\\"");
        case ESCAPE_BACKSLASH -> sb.append("\\\\");
        case ESCAPE_NEWLINE -> sb.append("\\n");
        case ESCAPE_TAB -> sb.append("\\t");
        case ESCAPE_CR -> sb.append("\\r");
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

  /**
   * 格式化参数为字符串（截断过长内容）
   *
   * @param parameter 参数对象
   * @return 参数字符串
   */
  private static String formatParameter(Object parameter) {
    if (parameter == null) {
      return null;
    }
    String paramStr = parameter.toString();
    return paramStr.length() > 500 ? paramStr.substring(0, 500) + "...(已截断)" : paramStr;
  }

  /**
   * 获取异常堆栈字符串
   *
   * @param exception 异常对象
   * @return 堆栈字符串
   */
  private static String getStackTraceString(Throwable exception) {
    if (exception == null) {
      return null;
    }
    return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(exception);
  }
}
