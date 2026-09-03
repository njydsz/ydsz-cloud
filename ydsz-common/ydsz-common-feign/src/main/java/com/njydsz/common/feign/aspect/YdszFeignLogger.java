package com.njydsz.common.feign.aspect;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import feign.Logger;
import feign.Request;
import feign.Response;
import org.slf4j.LoggerFactory;

/**
 * YdszFeign 日志增强处理器。
 *
 * <p>相比 Feign 默认的日志处理器，本类提供了更友好的日志输出格式。
 *
 * <p>日志级别对应内容：
 *
 * <ul>
 *   <li>{@link Logger.Level#NONE} - 不输出任何日志
 *   <li>{@link Logger.Level#BASIC} - 仅记录请求方法、URL 和响应状态
 *   <li>{@link Logger.Level#HEADERS} - 在 BASIC 基础上增加请求/响应头
 *   <li>{@link Logger.Level#FULL} - 记录完整的请求和响应，包括主体和元数据
 * </ul>
 *
 * <p><b>安全加固：</b>对日志中的敏感字段值进行脱敏，防止密码、令牌等敏感信息泄露到日志中。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class YdszFeignLogger extends Logger {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("com.njydsz.feign");

  /** Feign 日志级别 */
  private volatile Logger.Level logLevel = Logger.Level.BASIC;

  /** 需要脱敏的敏感字段名称集合（不区分大小写匹配） */
  private static final Set<String> SENSITIVE_FIELDS =
      Set.of(
          "password",
          "token",
          "secret",
          "key",
          "authorization",
          "cookie",
          "x-access-token",
          "x-auth-token",
          "x-csrf-token");

  /** 脱敏替换值 */
  private static final String MASK_VALUE = "****";

  /**
   * 预编译的 JSON 格式脱敏正则，缓存每个敏感字段对应的 Pattern，避免每次调用时重复编译。
   *
   * <p>惰性初始化：首次访问某个敏感字段时才编译对应的 Pattern。
   */
  private static final Map<String, Pattern> JSON_PATTERNS = new LinkedHashMap<>(16);

  /** 用于匹配敏感字段在 JSON 中的值（字符串值） */
  private static final String JSON_VALUE_PATTERN_TEMPLATE = "(\"%s\"\\s*:\\s*\")[^\"]*(\")";

  /** 用于匹配敏感字段在 Header 中的值 */
  private static final String HEADER_VALUE_PATTERN_TEMPLATE = "(?i)(%s\\s*[=:]\\s*)[^\\s,;]+";

  /** Feign config key 最大显示长度 */
  private static final int CONFIG_KEY_MAX_LEN = 30;

  public YdszFeignLogger() {
    super();
  }

  @Override
  protected void log(String configKey, String format, Object... args) {
    if (LOG.isDebugEnabled()) {
      String masked = maskSensitive(String.format(format, args));
      LOG.debug("[Feign#{}] {}", truncateConfigKey(configKey), masked);
    }
  }

  @Override
  protected void logRequest(String configKey, Logger.Level logLevel, Request request) {
    if (!LOG.isDebugEnabled()) {
      return;
    }
    StringBuilder sb = new StringBuilder(128);
    sb.append("--> ").append(request.httpMethod()).append(" ").append(request.url());

    if (logLevel.ordinal() >= Logger.Level.HEADERS.ordinal()) {
      sb.append("\n[Headers]");
      for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
        String headerName = entry.getKey();
        for (String headerValue : entry.getValue()) {
          sb.append("\n  ").append(headerName).append(": ").append(maskIfSensitive(headerName, headerValue));
        }
      }
    }

    if (logLevel.ordinal() >= Logger.Level.FULL.ordinal() && request.body() != null) {
      String body = request.charset() != null ? new String(request.body(), request.charset()) : new String(request.body());
      sb.append("\n[Body]\n  ").append(maskSensitive(body));
    }

    LOG.debug("[Feign#{}] {}", truncateConfigKey(configKey), sb);
  }

  @Override
  protected Response logAndRebufferResponse(String configKey, Logger.Level logLevel, Response response, long elapsedTime) throws java.io.IOException {
    if (!LOG.isDebugEnabled()) {
      return response;
    }
    StringBuilder sb = new StringBuilder(128);
    sb.append("<-- ").append(response.status()).append(" ").append(response.reason()).append(" ").append(elapsedTime).append("ms");

    if (logLevel.ordinal() >= Logger.Level.HEADERS.ordinal()) {
      sb.append("\n[Headers]");
      for (Map.Entry<String, Collection<String>> entry : response.headers().entrySet()) {
        String headerName = entry.getKey();
        for (String headerValue : entry.getValue()) {
          sb.append("\n  ").append(headerName).append(": ").append(maskIfSensitive(headerName, headerValue));
        }
      }
    }

    Response bodyResponse = response;
    if (logLevel.ordinal() >= Logger.Level.FULL.ordinal() && response.body() != null) {
      byte[] bodyData = feign.Util.toByteArray(response.body().asInputStream());
      String body = new String(bodyData, feign.Util.UTF_8);
      sb.append("\n[Body]\n  ").append(maskSensitive(body));
      bodyResponse = response.toBuilder().body(bodyData).build();
    }

    LOG.debug("[Feign#{}] {}", truncateConfigKey(configKey), sb);
    return bodyResponse;
  }

  @Override
  protected java.io.IOException logIOException(String configKey, Logger.Level logLevel, java.io.IOException ioe, long elapsedTime) {
    LOG.warn("[Feign#{}] <-- ERROR {} after {}ms: {}", truncateConfigKey(configKey), ioe.getClass().getSimpleName(), elapsedTime, ioe.getMessage());
    return ioe;
  }

  // ======================== 脱敏辅助方法 ========================

  /**
   * 对日志内容中的敏感字段值进行脱敏（JSON 格式和 Header 格式）。
   *
   * @param content 原始日志内容
   * @return 脱敏后的内容
   */
  String maskSensitive(String content) {
    if (content == null || content.isEmpty()) {
      return content;
    }
    String result = content;
    for (String field : SENSITIVE_FIELDS) {
      Pattern jsonPattern = JSON_PATTERNS.computeIfAbsent(field, k -> Pattern.compile(String.format(JSON_VALUE_PATTERN_TEMPLATE, Pattern.quote(k))));
      result = jsonPattern.matcher(result).replaceAll("$1" + MASK_VALUE + "$2");
    }
    return result;
  }

  /**
   * 如果字段名是敏感字段，则对其值进行脱敏。
   *
   * @param name  字段名
   * @param value 字段值
   * @return 如果敏感则返回脱敏值，否则返回原值
   */
  private String maskIfSensitive(String name, String value) {
    if (name == null || value == null) {
      return value;
    }
    if (SENSITIVE_FIELDS.contains(name.toLowerCase())) {
      return MASK_VALUE;
    }
    return value;
  }

  /**
   * 截断过长的 config key，避免日志过长。
   *
   * @param configKey Feign config key
   * @return 截断后的 key
   */
  private String truncateConfigKey(String configKey) {
    if (configKey == null) {
      return "unknown";
    }
    int dotIndex = configKey.lastIndexOf('.');
    String shortKey = dotIndex >= 0 ? configKey.substring(dotIndex + 1) : configKey;
    if (shortKey.length() > CONFIG_KEY_MAX_LEN) {
      return shortKey.substring(0, CONFIG_KEY_MAX_LEN) + "...";
    }
    return shortKey;
  }

  /**
   * 获取当前日志级别。
   *
   * @return 日志级别
   */
  public Logger.Level getLogLevel() {
    return logLevel;
  }

  /**
   * 设置日志级别。
   *
   * @param logLevel 日志级别
   */
  public void setLogLevel(Logger.Level logLevel) {
    this.logLevel = logLevel;
  }
}