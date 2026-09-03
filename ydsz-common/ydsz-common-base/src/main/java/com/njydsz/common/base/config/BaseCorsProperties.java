package com.njydsz.common.base.config;
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
  private List<String> allowedOriginPatterns = new ArrayList<>(4);

  /**
   * 允许的请求头列表
   *
   * <p>默认值为 ["*"]，表示允许所有请求头。
   */
  private List<String> allowedHeaders = Arrays.asList("*");

  /**
   * 允许的请求方法列表
   *
   * <p>默认值为 ["GET", "POST", "PUT", "DELETE", "OPTIONS"]。
   */
  private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");

  /**
   * 预检请求缓存时间（秒）
   *
   * <p>默认值为 3600（1 小时），减少浏览器 OPTIONS 预检请求频率。
   */
  private long maxAge = 3600;

  /**
   * 匹配的路径模式
   *
   * <p>默认值为 "/**"，表示匹配所有路径。
   */
  private String pathPattern = "/**";
}
}