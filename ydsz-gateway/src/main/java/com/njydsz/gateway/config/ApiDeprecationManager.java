package com.njydsz.gateway.config;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * API 弃用管理器。
 *
 * <p>从配置中加载已弃用的 API 路径列表，提供两个核心能力：
 *
 * <ul>
 *   <li>{@link #isDeprecated(String)}：判断请求路径是否命中已弃用 API（前缀匹配）
 *   <li>{@link #getDeprecationHeaders(String)}：根据弃用配置生成 Deprecation 系列响应头
 * </ul>
 *
 * <p>响应头格式遵循 IETF RFC 9745 / W3C deprecation 头规范：
 *
 * <pre>
 *   Deprecation: @v1                          -- 弃用标识（@since 版本号）
 *   Sunset: Sat, 31 Dec 2026 23:59:59 GMT     -- HTTP 日期格式（RFC 1123）
 *   Link: &lt;/api/v2/message/send&gt;; rel="successor-version"
 *   X-API-Deprecation-Message: 请使用新接口替代
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiDeprecationManager {

  /** RFC 1123 日期格式（Sunset 头要求） */
  private static final DateTimeFormatter RFC1123_FORMATTER =
      DateTimeFormatter.RFC_1123_DATE_TIME;

  private final DeprecationProperties deprecationProperties;

  /**
   * 判断请求路径是否属于已弃用的 API。
   *
   * <p>使用前缀匹配：请求路径以任一配置项的 {@code path} 为前缀即视为命中。
   *
   * @param path 请求路径（如 /api/v1/message/send）
   * @return true=已弃用
   */
  public boolean isDeprecated(String path) {
    if (!deprecationProperties.isEnabled()) {
      return false;
    }
    if (path == null || path.isBlank()) {
      return false;
    }
    List<DeprecatedApiConfig> entries = getMatchedEntries(path);
    return !entries.isEmpty();
  }

  /**
   * 获取指定请求路径对应的 Deprecation 响应头集合。
   *
   * <p>当路径匹配多个弃用配置时，优先返回最长前缀匹配的配置。头字段说明：
   *
   * <ul>
   *   <li>{@code Deprecation}：使用 {@code @<since>} 格式，标识弃用版本（RFC 9745）
   *   <li>{@code Sunset}：RFC 1123 HTTP-Date 格式的移除截止日
   *   <li>{@code Link}：替代版本链接（RFC 5988）
   *   <li>{@code X-API-Deprecation-Message}：自定义弃用说明（非标扩展头）
   * </ul>
   *
   * @param requestPath 请求路径
   * @return 弃用响应头 Map，未匹配时返回空 Map
   */
  public Map<String, String> getDeprecationHeaders(String requestPath) {
    if (!deprecationProperties.isEnabled()) {
      return Collections.emptyMap();
    }
    if (requestPath == null || requestPath.isBlank()) {
      return Collections.emptyMap();
    }

    // 最长前缀匹配，取最精确的弃用配置
    DeprecatedApiConfig bestMatch = findBestMatch(requestPath);
    if (bestMatch == null) {
      return Collections.emptyMap();
    }

    return buildHeaders(bestMatch);
  }

  /**
   * 寻找与请求路径最精确匹配的弃用配置（最长前缀匹配）。
   *
   * @param requestPath 请求路径
   * @return 最佳匹配配置，未命中返回 null
   */
  private DeprecatedApiConfig findBestMatch(String requestPath) {
    List<DeprecationProperties.DeprecatedApiEntry> entries = deprecationProperties.getApis();
    if (entries == null || entries.isEmpty()) {
      return null;
    }

    DeprecationProperties.DeprecatedApiEntry bestEntry = null;
    int bestLen = -1;

    for (DeprecationProperties.DeprecatedApiEntry entry : entries) {
      String path = entry.getPath();
      if (path == null || path.isBlank()) {
        continue;
      }
      if (requestPath.startsWith(path) && path.length() > bestLen) {
        bestEntry = entry;
        bestLen = path.length();
      }
    }

    return bestEntry != null ? new DeprecatedApiConfig(bestEntry) : null;
  }

  /**
   * 获取所有匹配路径前缀的弃用配置。
   *
   * @param path 请求路径
   * @return 匹配的配置列表
   */
  private List<DeprecatedApiConfig> getMatchedEntries(String path) {
    List<DeprecationProperties.DeprecatedApiEntry> entries = deprecationProperties.getApis();
    if (entries == null || entries.isEmpty()) {
      return Collections.emptyList();
    }

    List<DeprecatedApiConfig> matched = new ArrayList<>(entries.size());
    for (DeprecationProperties.DeprecatedApiEntry entry : entries) {
      String entryPath = entry.getPath();
      if (entryPath != null && !entryPath.isBlank() && path.startsWith(entryPath)) {
        matched.add(new DeprecatedApiConfig(entry));
      }
    }
    return matched;
  }

  /**
   * 根据弃用配置构建响应头 Map。
   *
   * @param config 弃用 API 配置
   * @return 响应头 Map
   */
  private Map<String, String> buildHeaders(DeprecatedApiConfig config) {
    HashMap<String, String> headers = new HashMap<>(8);

    // Deprecation 头（RFC 9745）：@since 格式
    if (config.since != null && !config.since.isBlank()) {
      headers.put("Deprecation", "@" + config.since.trim());
    }

    // Sunset 头：RFC 1123 HTTP-Date 格式
    String sunsetHttpDate = toRfc1123HttpDate(config.removalDate);
    if (sunsetHttpDate != null) {
      headers.put("Sunset", sunsetHttpDate);
    }

    // Link 头（RFC 5988）：指向替代版本
    if (config.replacement != null && !config.replacement.isBlank()) {
      String linkValue = "<" + config.replacement.trim() + ">; rel=\"successor-version\"";
      if (config.message != null && !config.message.isBlank()) {
        linkValue += "; title=\"" + config.message.trim() + "\"";
      }
      headers.put("Link", linkValue);
    }

    // X-API-Deprecation-Message 自定义说明
    if (config.message != null && !config.message.isBlank()) {
      headers.put("X-API-Deprecation-Message", config.message.trim());
    }

    log.debug(
        "[ApiDeprecation] 路径 '{}' 命中弃用配置，响应头: {}",
        config.path,
        headers.keySet());

    return headers;
  }

  /**
   * 将字符串格式的日期转换为 RFC 1123 HTTP-Date。
   *
   * <p>支持的输入格式：
   *
   * <ul>
   *   <li>{@code "2026-12-31"} → 补全为 {@code Sat, 31 Dec 2026 23:59:59 GMT}
   *   <li>{@code "2026-12-31T23:59:59Z"} → RFC 1123
   *   <li>已经是 RFC 1123 格式则原样返回
   * </ul>
   *
   * @param dateStr 日期字符串
   * @return RFC 1123 格式化字符串，解析失败返回 null
   */
  private String toRfc1123HttpDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) {
      return null;
    }
    String trimmed = dateStr.trim();

    // 直接尝试解析为 RFC 1123（已经是标准格式则直接返回）
    try {
      ZonedDateTime parsed = ZonedDateTime.parse(trimmed, RFC1123_FORMATTER);
      return parsed.format(RFC1123_FORMATTER);
    } catch (DateTimeParseException ignored) {
      // 继续尝试其他格式
    }

    // 尝试 ISO 8601（含时区）
    try {
      ZonedDateTime parsed = ZonedDateTime.parse(trimmed);
      return parsed.withZoneSameInstant(ZoneOffset.UTC).format(RFC1123_FORMATTER);
    } catch (DateTimeParseException ignored) {
      // 继续尝试 LocalDate
    }

    // 尝试纯日期格式（补全为当天 UTC 23:59:59）
    try {
      LocalDate date = LocalDate.parse(trimmed);
      ZonedDateTime zdt =
          date.atTime(23, 59, 59).atZone(ZoneOffset.UTC);
      return zdt.format(RFC1123_FORMATTER);
    } catch (DateTimeParseException e) {
      log.warn("[ApiDeprecation] 无法解析移除日期 '{}', 跳过 Sunset 头", trimmed);
      return null;
    }
  }

  /**
   * 内部轻量不可变视图，封装单条弃用配置。
   */
  private record DeprecatedApiConfig(
      String path,
      String since,
      String replacement,
      String removalDate,
      String message) {
    DeprecatedApiConfig(DeprecationProperties.DeprecatedApiEntry entry) {
      this(
          entry.getPath(),
          entry.getSince(),
          entry.getReplacement(),
          entry.getRemovalDate(),
          entry.getMessage());
    }
  }
}
