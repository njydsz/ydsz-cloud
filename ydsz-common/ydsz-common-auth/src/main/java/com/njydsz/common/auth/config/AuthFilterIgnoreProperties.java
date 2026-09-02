package com.njydsz.common.auth.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证过滤器忽略路径配置属性。
 *
 * <p>允许应用通过 {@code ydsz.auth.filter-ignore.*} 配置项， 在运行时动态追加或覆盖认证过滤器忽略的 URL 和服务名称。
 *
 * <p><b>默认值：</b>包含项目内置的常见静态资源 URL 和 web 模块名。 当 {@link #isOverrideMode() overrideMode} 为 {@code
 * false}（默认）, 配置的列表将与默认值合并;当 overrideMode 为 {@code true} 时,将完全替换默认集合。
 *
 * <p><b>使用示例:</b>
 *
 * <pre>{@code
 * ydsz:
 *   auth:
 *     filter-ignore:
 *       common-ignore-urls:
 *         - /custom/assets/**
 *       auth-filter-ignore-service-names:
 *         - ydsz-invoice-web
 *       security-exclude-urls:
 *         - /custom-login
 *       override-mode: false
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AuthFilterConfiguration
 */
@ConfigurationProperties(prefix = "ydsz.auth.filter-ignore")
public class AuthFilterIgnoreProperties {

  /** 默认的公共忽略 URL 模式列表。 */
  private static final List<String> DEFAULT_COMMON_IGNORE_URLS =
      Arrays.asList(
          "/**/css/**",
          "/**/js/**",
          "/**/images/**",
          "/**/fonts/**",
          "/**/swagger**/**",
          "/**/webjars/**",
          "/**/v2/api-docs/**",
          "/**/v3/api-docs/**",
          "/**/v3/api-docs.yaml",
          "/**/error",
          "/**/doc.html",
          "/doc.html",
          "/**/swagger-ui/**",
          "/**/swagger-ui.html",
          "/**/favicon.ico",
          "/**/health",
          "/**/actuator/**");

  /** 默认的认证过滤器忽略服务名称列表。 */
  private static final List<String> DEFAULT_IGNORE_SERVICE_NAMES =
      Arrays.asList(
          "ydsz-gateway",
          "ydsz-system-web",
          "ydsz-userinfo-web",
          "ydsz-message-web",
          "ydsz-cronjob-web",
          "ydsz-agent-web",
          "ydsz-nextwiki-web",
          "ydsz-literule-web",
          "ydsz-workflow-web");

  /** 默认的安全排除 URL 列表。 */
  private static final List<String> DEFAULT_SECURITY_EXCLUDE_URLS =
      Arrays.asList("/login", "/auth/**", "/captcha/**");

  /** 是否使用"替换模式"替代"合并模式" */
  private boolean overrideMode = false;

  /** 公共忽略 URL 模式列表 */
  private List<String> commonIgnoreUrls = new ArrayList<>(DEFAULT_COMMON_IGNORE_URLS);

  /** 认证过滤器忽略的服务名称列表 */
  private List<String> authFilterIgnoreServiceNames = new ArrayList<>(DEFAULT_IGNORE_SERVICE_NAMES);

  /** 安全排除 URL 列表 */
  private List<String> securityExcludeUrls = new ArrayList<>(DEFAULT_SECURITY_EXCLUDE_URLS);

  public boolean isOverrideMode() {
    return overrideMode;
  }

  public void setOverrideMode(boolean overrideMode) {
    this.overrideMode = overrideMode;
  }

  public List<String> getCommonIgnoreUrls() {
    return commonIgnoreUrls;
  }

  public void setCommonIgnoreUrls(List<String> commonIgnoreUrls) {
    this.commonIgnoreUrls =
        commonIgnoreUrls != null ? commonIgnoreUrls : new ArrayList<>(DEFAULT_COMMON_IGNORE_URLS);
  }

  public List<String> getAuthFilterIgnoreServiceNames() {
    return authFilterIgnoreServiceNames;
  }

  public void setAuthFilterIgnoreServiceNames(List<String> authFilterIgnoreServiceNames) {
    this.authFilterIgnoreServiceNames =
        authFilterIgnoreServiceNames != null
            ? authFilterIgnoreServiceNames
            : new ArrayList<>(DEFAULT_IGNORE_SERVICE_NAMES);
  }

  public List<String> getSecurityExcludeUrls() {
    return securityExcludeUrls;
  }

  public void setSecurityExcludeUrls(List<String> securityExcludeUrls) {
    this.securityExcludeUrls =
        securityExcludeUrls != null
            ? securityExcludeUrls
            : new ArrayList<>(DEFAULT_SECURITY_EXCLUDE_URLS);
  }

  /**
   * 获取有效的服务名称集合。
   *
   * @return 服务名称集合（不可变）
   */
  public Set<String> getResolvedAuthFilterIgnoreServiceNames() {
    return Set.copyOf(authFilterIgnoreServiceNames);
  }

  /**
   * 获取有效的公共忽略 URL 集合。
   *
   * @return URL 集合（不可变）
   */
  public Set<String> getResolvedCommonIgnoreUrls() {
    return Set.copyOf(commonIgnoreUrls);
  }

  /**
   * 获取有效的安全排除 URL 集合。
   *
   * @return URL 集合（不可变）
   */
  public Set<String> getResolvedSecurityExcludeUrls() {
    return Set.copyOf(securityExcludeUrls);
  }
}
