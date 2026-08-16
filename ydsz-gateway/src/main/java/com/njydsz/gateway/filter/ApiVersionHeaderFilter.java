package com.njydsz.gateway.filter;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.ApiVersionProperties;
import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * P3-7: API 版本响应头注入过滤器（增强版：版本协商 + 弃用管理）
 *
 * <p>从请求中提取 API 版本，注入响应头：
 *
 * <ul>
 *   <li>{@code X-API-Version}: 当前请求命中的 API 版本（如 v1 / v2）
 *   <li>{@code SunSet}: 命中已废弃版本时输出建议下线日期（RFC 8594）
 *   <li>{@code Deprecation}: 命中已废弃版本时输出 true（RFC 8594 草案）
 *   <li>{@code Link}: 替代版本链接（RFC 5988）
 * </ul>
 *
 * <h3>版本协商优先级</h3>
 *
 * <ol>
 *   <li>Path: {@code /api/v1/users} → v1（最高优先级）
 *   <li>Header: {@code X-API-Version: v2} → v2
 *   <li>Query: {@code ?api-version=v2} → v2
 *   <li>默认: 未指定版本时使用配置默认版本
 * </ol>
 *
 * <h3>弃用版本处理</h3>
 *
 * <p>当请求命中废弃版本时，响应中自动注入：
 *
 * <pre>
 *   Sunset: Sat, 31 Dec 2026 23:59:59 GMT
 *   Deprecation: true
 *   Link: &lt;/api/v2&gt;; rel="successor-version"
 * </pre>
 *
 * @author ydsz-team
 * @since 3.7.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.api-version",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ApiVersionHeaderFilter implements GlobalFilter, Ordered {

  /** 匹配路径版本段：/api/v1/... 或 /v1/... */
  private static final Pattern VERSION_PATTERN = Pattern.compile("/(api/)?v(?<ver>\\d+)(?:[./]|$)");

  /** 版本响应头 */
  private static final String HEADER_API_VERSION = "X-API-Version";

  /** Sunset 响应头（RFC 8594） */
  private static final String HEADER_SUNSET = "Sunset";

  /** Deprecation 响应头 */
  private static final String HEADER_DEPRECATION = "Deprecation";

  /** Link 响应头 */
  private static final String HEADER_LINK = "Link";

  private final ApiVersionProperties properties;

  /**
   * P3-7: 注入 API 版本响应头和弃用信息。
   *
   * <p>版本协商优先级：Path > Header > Query > Default。 使用 {@code then() + doOnSuccess()} 在响应提交前设置头，
   * 不阻塞请求主链路。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain
        .filter(exchange)
        .then(
            Mono.fromRunnable(
                () -> {
                  try {
                    ServerHttpRequest request = exchange.getRequest();
                    String path = request.getURI().getPath();

                    // P3-7: 版本协商
                    String version = resolveApiVersion(request, path);

                    if (version == null || version.isBlank()) {
                      return;
                    }

                    ServerHttpResponse response = exchange.getResponse();
                    HttpHeaders headers = response.getHeaders();

                    // 注入版本响应头
                    if (!headers.containsHeader(HEADER_API_VERSION)) {
                      headers.set(HEADER_API_VERSION, version);
                    }

                    // P3-7: 弃用版本处理
                    injectDeprecationHeaders(headers, version);
                  } catch (Exception e) {
                    // 响应头注入失败不影响主流程
                    log.debug("[ApiVersionHeader] 注入版本响应头失败: {}", e.getMessage());
                  }
                }));
  }

  /**
   * P3-7: 版本协商（Path > Header > Query > Default）
   *
   * @param request HTTP 请求
   * @param path 请求路径
   * @return 协商后的版本号，未匹配时返回 null
   */
  private String resolveApiVersion(ServerHttpRequest request, String path) {
    // 1. Path 版本提取（最高优先级）
    Matcher matcher = VERSION_PATTERN.matcher(path);
    if (matcher.find()) {
      return "v" + matcher.group("ver");
    }

    // 2. Header 版本协商
    ApiVersionProperties.HeaderNegotiationConfig headerConfig = properties.getHeaderNegotiation();
    if (headerConfig.isEnabled()) {
      String headerVersion = request.getHeaders().getFirst(headerConfig.getHeaderName());
      if (headerVersion != null && !headerVersion.isBlank() && isSupportedVersion(headerVersion)) {
        return normalizeVersion(headerVersion);
      }
    }

    // 3. Query 版本协商
    ApiVersionProperties.QueryNegotiationConfig queryConfig = properties.getQueryNegotiation();
    if (queryConfig.isEnabled()) {
      String queryVersion = request.getQueryParams().getFirst(queryConfig.getParamName());
      if (queryVersion != null && !queryVersion.isBlank() && isSupportedVersion(queryVersion)) {
        return normalizeVersion(queryVersion);
      }
    }

    // 4. 默认版本
    return properties.getDefaultVersion();
  }

  /**
   * P3-7: 注入弃用版本响应头
   *
   * @param headers 响应头
   * @param version 当前版本
   */
  private void injectDeprecationHeaders(HttpHeaders headers, String version) {
    Map<String, ApiVersionProperties.DeprecatedVersion> deprecatedMap =
        properties.getDeprecatedVersions();
    if (deprecatedMap == null || deprecatedMap.isEmpty()) {
      return;
    }

    ApiVersionProperties.DeprecatedVersion deprecated = deprecatedMap.get(version);
    if (deprecated == null) {
      // 尝试带 v 前缀的匹配
      deprecated = deprecatedMap.get(version.toLowerCase());
    }

    if (deprecated == null) {
      return;
    }

    // Sunset 头（RFC 8594）
    if (deprecated.getSunset() != null && !deprecated.getSunset().isBlank()) {
      headers.set(HEADER_SUNSET, deprecated.getSunset());
    }

    // Deprecation 头
    headers.set(HEADER_DEPRECATION, "true");

    // Link 头（指向替代版本）
    if (deprecated.getReplacement() != null && !deprecated.getReplacement().isBlank()) {
      String linkValue = "<" + deprecated.getReplacement() + ">; rel=\"successor-version\"";
      if (deprecated.getMessage() != null && !deprecated.getMessage().isBlank()) {
        linkValue += "; title=\"" + deprecated.getMessage() + "\"";
      }
      headers.set(HEADER_LINK, linkValue);
    }

    log.debug("[ApiVersionHeader] 版本 {} 已弃用，注入弃用响应头", version);
  }

  /**
   * P3-7: 检查是否为支持的版本
   *
   * @param version 版本号
   * @return true=支持
   */
  private boolean isSupportedVersion(String version) {
    List<String> supported = properties.getSupportedVersions();
    if (supported == null || supported.isEmpty()) {
      return true;
    }
    String normalized = normalizeVersion(version);
    return supported.contains(normalized);
  }

  /**
   * P3-7: 标准化版本号（统一为小写）
   *
   * @param version 原始版本号
   * @return 标准化版本号
   */
  private String normalizeVersion(String version) {
    if (version == null) {
      return null;
    }
    return version.toLowerCase().trim();
  }

  /**
   * 过滤器顺序：位于过滤器链尾部（响应阶段执行，不影响鉴权/限流）。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.API_VERSION_HEADER.getOrder();
  }
}
