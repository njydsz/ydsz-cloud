package com.njydsz.common.web.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

/**
 * API 版本路由条件
 *
 * <p>实现 Spring MVC 的 RequestCondition 接口，用于根据 URL 路径中的 API 版本进行路由匹配。 支持 URL 路径模式（如 {@code
 * /v1/api/users}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {

  private final String version;
  private final ApiVersionProperties properties;
  private static final Pattern VERSION_PATTERN = Pattern.compile("/v(\\d+(?:\\.\\d+)?)");

  public ApiVersionCondition(String version, ApiVersionProperties properties) {
    this.version = version;
    this.properties = properties;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public ApiVersionCondition combine(ApiVersionCondition other) {
    // 方法级别的版本优先于类级别
    return other;
  }

  @Override
  public ApiVersionCondition getMatchingCondition(HttpServletRequest request) {
    if (!properties.isEnabled()) {
      return this;
    }

    String requestVersion = extractVersionFromUrl(request);
    if (requestVersion == null) {
      // 未指定版本时使用默认版本
      requestVersion = properties.getDefaultVersion();
    }

    if (matchesVersion(requestVersion)) {
      return this;
    }

    return null;
  }

  @Override
  public int compareTo(ApiVersionCondition other, HttpServletRequest request) {
    // 版本号大的优先（v2 > v1）
    return compareVersions(this.version, other.version);
  }

  /** 从 URL 路径提取版本号（/v1/api/users → "1"） */
  private String extractVersionFromUrl(HttpServletRequest request) {
    String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (path == null) {
      path = request.getRequestURI();
    }

    Matcher matcher = VERSION_PATTERN.matcher(path);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  /**
   * 判断请求版本是否匹配
   *
   * @param requestVersion 请求版本
   * @return 匹配返回 true
   */
  private boolean matchesVersion(String requestVersion) {
    if (requestVersion == null) {
      return false;
    }
    // 精确匹配
    if (requestVersion.equals(version)) {
      return true;
    }
    // 主版本前缀匹配：请求 "1.0" 匹配接口 "1"
    if (requestVersion.startsWith(version + ".")) {
      return true;
    }
    // 灵活匹配模式：双向匹配（开启时请求 "1" 也能匹配接口 "1.0"）
    if (properties.isFlexibleMatching() && version.startsWith(requestVersion + ".")) {
      return true;
    }
    return false;
  }

  /**
   * 比较两个版本号
   *
   * @param v1 版本 1
   * @param v2 版本 2
   * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等
   */
  private int compareVersions(String v1, String v2) {
    String[] parts1 = v1.split("\\.");
    String[] parts2 = v2.split("\\.");
    int length = Math.max(parts1.length, parts2.length);

    for (int i = 0; i < length; i++) {
      int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
      int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
      if (p1 != p2) {
        return Integer.compare(p1, p2);
      }
    }
    return 0;
  }

  /** 解析版本号部分（支持数字和空字符串） */
  private int parseVersionPart(String part) {
    try {
      return Integer.parseInt(part);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
