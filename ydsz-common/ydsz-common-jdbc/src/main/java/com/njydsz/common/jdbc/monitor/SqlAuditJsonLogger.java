package com.njydsz.common.jdbc.monitor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * SQL 审计 JSON 格式日志输出器
 *
 * <p>将 SQL 审计信息输出为结构化 JSON 格式，便于 ELK/Loki 等日志系统解析和检索。
 *
 * <p>底层使用 {@link YdszJson} 构建 JSON 字符串，替代手工 {@code StringBuilder} 拼接，
 * 保证转义一致性和代码可维护性。JSON 构建异常时会退化为简化的 fallback JSON，
 * 确保原始 SQL 信息不丢失，避免审计盲区。
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
 *   <li>{@code parameter} — SQL 参数（格式化后）
 *   <li>{@code trace_id} — 链路追踪 ID（从 MDC 获取，可能为空）
 *   <li>{@code exception} — 异常信息（存在时）
 *   <li>{@code stack_trace} — 异常堆栈（存在时）
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * SqlAuditJsonLogger.log("com.example.Mapper.select", "SELECT", sql, 5, 10, param);
 * SqlAuditJsonLogger.logError("com.example.Mapper.update", "UPDATE", sql, 100, ex);
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

  /** MDC 中 traceId 的 key */
  private static final String MDC_TRACE_ID = "traceId";

  /** 参数最大长度 */
  private static final int MAX_PARAMETER_LENGTH = 500;

  /**
   * 输出结构化 JSON 审计日志
   *
   * <p>使用 {@link YdszJson} 序列化审计对象，替代手写 JSON 拼接，保证所有字段的 JSON 转义一致性。
   * 若 JSON 序列化异常，会退化为包含原始 SQL 的简化 JSON，避免审计盲区。
   *
   * @param sqlId MappedStatement ID
   * @param commandType SQL 类型
   * @param sql 完整 SQL 语句
   * @param elapsedMs 执行耗时（毫秒）
   * @param affectedRows 影响行数
   * @param parameter SQL 参数对象
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
      Map<String, Object> jsonMap = buildAuditMap(
          sqlId,
          commandType,
          sql,
          elapsedMs,
          affectedRows,
          parameter,
          null);
      String json = YdszJson.toJson(jsonMap);
      AUDIT_JSON_LOG.info(json);
    } catch (Exception e) {
      // 构建失败时输出退化的 fallback JSON（保留 SQL 原始信息）
      String fallback = buildFallbackJson(sqlId, commandType, sql, elapsedMs);
      AUDIT_JSON_LOG.info(fallback);
      AUDIT_JSON_LOG.warn("JSON 审计日志构建失败，已输出降级日志: {}", e.getMessage());
    }
  }

  /**
   * 输出异常场景的审计日志
   *
   * @param sqlId MappedStatement ID
   * @param commandType SQL 类型
   * @param sql 完整 SQL 语句
   * @param elapsedMs 执行耗时（毫秒）
   * @param exception 执行异常对象
   */
  public static void logError(
      String sqlId,
      String commandType,
      String sql,
      long elapsedMs,
      Throwable exception) {
    if (!AUDIT_JSON_LOG.isErrorEnabled()) {
      return;
    }
    try {
      Map<String, Object> jsonMap =
          buildAuditMap(sqlId, commandType, sql, elapsedMs, null, null, exception);
      jsonMap.put("status", "ERROR");
      String json = YdszJson.toJson(jsonMap);
      AUDIT_JSON_LOG.error(json);
    } catch (Exception e) {
      // 构建失败时输出退化的 fallback JSON
      String fallback = buildFallbackJson(sqlId, commandType, sql, elapsedMs);
      AUDIT_JSON_LOG.error(fallback);
      AUDIT_JSON_LOG.warn("JSON 审计日志构建失败，已输出降级日志: {}", e.getMessage());
    }
  }

  // ====================================================================
  // 内部方法
  // ====================================================================

  /**
   * 构建统一的审计 JSON Map，供正常和异常日志复用。
   *
   * @param sqlId MappedStatement ID
   * @param commandType SQL 类型
   * @param sql 原始 SQL
   * @param elapsedMs 执行耗时（毫秒）
   * @param affectedRows 影响行数（可为 null）
   * @param parameter 参数对象（可为 null）
   * @param exception 异常对象（可为 null）
   * @return 有序 Map，可直接传入 {@link YdszJson#toJson(Object)}
   */
  private static Map<String, Object> buildAuditMap(
      String sqlId,
      String commandType,
      String sql,
      long elapsedMs,
      Integer affectedRows,
      Object parameter,
      Throwable exception) {
    Map<String, Object> map = new LinkedHashMap<>(12);
    map.put("timestamp", LocalDateTime.now().format(ISO_FORMATTER));
    map.put("sql_id", sqlId);
    map.put("command_type", commandType);
    map.put("elapsed_ms", elapsedMs);
    map.put("affected_rows", affectedRows);
    map.put("sql_fingerprint", SqlFingerprint.fingerprint(sql));
    map.put("sql", sql);
    map.put("parameter", formatParameter(parameter));
    map.put("trace_id", getTraceId());
    if (exception != null) {
      map.put("exception", exception.getMessage());
      map.put("stack_trace", ExceptionUtils.getStackTrace(exception));
    }
    return map;
  }

  /**
   * 获取当前 traceId（从 MDC）。
   *
   * <p>当没有 traceId 上下文时（如定时任务）返回空字符串而非 null，保持 JSON 字段类型一致性。
   *
   * @return traceId 字符串，不存在时返回空字符串
   */
  private static String getTraceId() {
    String traceId = MDC.get(MDC_TRACE_ID);
    return traceId != null ? traceId : "";
  }

  /**
   * 格式化为字符串（截断过长内容）。
   *
   * @param parameter 参数对象
   * @return 格式化字符串，输入为 null 时返回 null
   */
  private static String formatParameter(Object parameter) {
    if (parameter == null) {
      return null;
    }
    String paramStr = parameter.toString();
    if (paramStr.length() > MAX_PARAMETER_LENGTH) {
      return paramStr.substring(0, MAX_PARAMETER_LENGTH) + "...(已截断)";
    }
    return paramStr;
  }

  /**
   * 构建退化的 fallback JSON。
   *
   * <p>当 {@link YdszJson} 序列化 {@link Map} 异常时使用（极端罕见）。
   * 退化的 JSON 使用简化的手动转义（仅处理 SQL 值），但仍保留最关键的 SQL 原始信息，避免审计盲区。
   *
   * @param sqlId MappedStatement ID
   * @param commandType SQL 类型
   * @param sql 原始 SQL
   * @param elapsedMs 执行耗时
   * @return 简化的 JSON 字符串（可能不完全 JSON 标准合规，但包含完整信息）
   */
  private static String buildFallbackJson(
      String sqlId, String commandType, String sql, long elapsedMs) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\"timestamp\":\"")
        .append(LocalDateTime.now().format(ISO_FORMATTER))
        .append("\",\"sql_id\":\"")
        .append(sqlId != null ? sqlId : "")
        .append("\",\"command_type\":\"")
        .append(commandType != null ? commandType : "")
        .append("\",\"elapsed_ms\":")
        .append(elapsedMs)
        .append(",\"sql\":\"")
        .append(sql != null ? minimalEscape(sql) : "")
        .append("\"}");
    return sb.toString();
  }

  /**
   * 最小化 JSON 字符串转义（仅处理关键字符）。
   *
   * <p>仅供 fallback 路径使用，正常路径由 {@link YdszJson} 处理转义。
   *
   * @param value 原始字符串
   * @return 转义后的字符串
   */
  private static String minimalEscape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
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
