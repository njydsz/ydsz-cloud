package com.njydsz.common.base.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域配置属性（Web/App 共享基类）
 *
 * <p>子类通过 {@code @ConfigurationProperties} 的 prefix 属性指定具体前缀， 例如 Web 端使用 {@code ydsz.web.cors}，App
 * 端使用 {@code ydsz.app.cors}。
 *
 * <p>本类为抽象基类，定义 CORS 通用配置项，不直接注册为 Spring Bean。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   web:
 *     cors:
 *       enabled: true
 *       allow-credentials: true
 *       allowed-origin-patterns:
 *         - "https://*.example.com"
 *       allowed-headers:
 *         - "*"
 *       allowed-methods:
 *         - GET
 *         - POST
 *       max-age: 3600
 *       path-pattern: "/**"
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties
public abstract class BaseCorsProperties {

  /**
   * 是否启用 CORS 跨域支持
   *
   * <p>默认值为 true，业务方可通过子类配置关闭。
   */
  private boolean enabled = true;

  /**
   * 是否允许发送 Cookie 等凭证信息
   *
   * <p>默认值为 false。设为 true 时 allowedOriginPatterns 不能包含 "*"， 浏览器会拒绝这种不安全组合。
   */
  private boolean allowCredentials = false;

  /**
   * 允许的跨域来源模式列表
   *
   * <p>支持通配符模式，例如 "https://*.example.com"。 推荐明确指定允许的域名，避免使用 "*" 带来的安全风险。
   */
  private List<String> allowedOriginPatterns = new ArrayList<>();

  /**
   * 允许的 HTTP 请求头列表
   *
   * <p>默认值为 ["*"]，表示允许所有请求头。
   */
  private List<String> allowedHeaders = new ArrayList<>(Arrays.asList("*"));

  /**
   * 允许的 HTTP 请求方法列表
   *
   * <p>默认值为 ["*"]，表示允许所有方法。 生产环境建议显式列出 GET/POST/PUT/DELETE 等。
   */
  private List<String> allowedMethods = new ArrayList<>(Arrays.asList("*"));

  /**
   * 允许暴露给客户端的响应头列表
   *
   * <p>默认为空，即不暴露额外响应头。 常见需要暴露的头：X-Request-Id, X-Total-Count, Content-Disposition 等。
   */
  private List<String> exposedHeaders = new ArrayList<>();

  /**
   * 预检请求（OPTIONS）缓存时间（秒）
   *
   * <p>浏览器在指定时间内不会重复发起预检请求，可降低跨域开销。 默认 3600 秒（1 小时）。
   */
  private long maxAge = 3600L;

  /**
   * CORS 配置生效的 URL 路径模式
   *
   * <p>默认值为 "/**"，即所有路径都应用 CORS 配置。
   */
  private String pathPattern = "/**";

  /**
   * 过滤器注册顺序
   *
   * <p>值越小优先级越高。默认 0，由 WebMvcConfigurer 注册为 CorsFilter。
   */
  private int order = 0;

  /**
   * 校验 CORS 配置的安全性，检测不安全的组合并返回警告信息。
   *
   * <p>检查项：
   *
   * <ul>
   *   <li>allowCredentials=true 且 allowedOriginPatterns 包含 "*"（CSRF 风险）
   *   <li>CORS 启用但 allowedOriginPatterns 为空（静默失败）
   *   <li>allowedOriginPatterns 包含 "*" 且未限制方法/头（过度开放）
   * </ul>
   *
   * @return 警告信息列表，为空表示配置安全
   */
  public List<String> validateSecurity() {
    List<String> warnings = new ArrayList<>();

    if (!enabled) {
      return warnings;
    }

    // 检查 1：allowCredentials=true 且 allowedOriginPatterns 包含 "*"
    if (allowCredentials && containsWildcard(allowedOriginPatterns)) {
      warnings.add(
          "安全风险：allowCredentials=true 且 allowedOriginPatterns 包含 \"*\"，"
              + "可能导致 CSRF 攻击。建议显式指定允许的域名列表。");
    }

    // 检查 2：CORS 启用但未配置任何允许来源
    if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()) {
      warnings.add("配置警告：CORS 已启用但 allowedOriginPatterns 为空，" + "将不会有任何跨域请求被允许。请配置允许的来源或禁用 CORS。");
    }

    // 检查 3：allowedOriginPatterns 包含 "*" 且 allowedMethods/allowedHeaders 也为 "*"
    if (containsWildcard(allowedOriginPatterns)
        && containsWildcard(allowedMethods)
        && containsWildcard(allowedHeaders)) {
      warnings.add(
          "安全风险：allowedOriginPatterns、allowedMethods、allowedHeaders 均为 \"*\"，"
              + "CORS 配置过度开放。生产环境建议显式指定允许的方法和头。");
    }

    return warnings;
  }

  /** 检查列表中是否包含通配符 "*" */
  private boolean containsWildcard(List<String> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    return list.stream().anyMatch("*"::equals);
  }
}
