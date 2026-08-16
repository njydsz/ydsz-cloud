package com.njydsz.common.auth.constant;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 认证过滤器忽略路径常量。
 *
 * <p>定义了过滤器需要忽略的 URL 模式和服务名称，用于：
 *
 * <ul>
 *   <li>公共资源过滤：CSS、JS、图片、字体等静态资源
 *   <li>API 文档过滤：Swagger、OpenAPI 等文档页面
 *   <li>认证过滤器忽略的服务名称（网关/SSO 等中转服务）
 *   <li>安全相关的排除 URL（登录、认证、验证码等）
 * </ul>
 *
 * <p><b>注意：</b>默认值硬编码在此处。建议通过配置文件 {@code ydsz.auth.filter-ignore.auth-filter-ignore-service-names}
 * 覆盖， 避免新增/移除 web 模块时修改代码。 通过 {@link
 * com.njydsz.common.auth.config.AuthFilterIgnoreProperties#getResolvedAuthFilterIgnoreServiceNames()}
 * 获取配置覆盖后的值。
 *
 * <p><b>线程安全性：</b>所有常量集合均为不可变 Set（{@link Collections#unmodifiableSet(Set)}）， 多线程并发访问安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class FilterIgnoreConstants {

  private FilterIgnoreConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** 默认全部忽略的URL模式 */
  private static final Set<String> COMMON_IGNORE_URL =
      Collections.unmodifiableSet(
          Set.of(
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
              "/**/actuator/**"));

  /** 认证过滤器忽略的服务名称 */
  private static final Set<String> AUTH_FILTER_IGNORE_SERVICE_NAME =
      Collections.unmodifiableSet(
          Set.of(
              "ydsz-gateway",
              "ydsz-system-web",
              "ydsz-userinfo-web",
              "ydsz-message-web",
              "ydsz-cronjob-web",
              "ydsz-agent-web",
              "ydsz-nextwiki-web",
              "ydsz-literule-web",
              "ydsz-workflow-web"));

  /** 安全相关的排除URL模式（登录、认证、验证码等） */
  private static final Set<String> SECURITY_EXCLUDE_URL =
      Collections.unmodifiableSet(Set.of("/login", "/auth/**", "/captcha/**"));

  /** 全部排除路径（预计算，避免每次调用 Stream.concat 重建） */
  private static final Set<String> ALL_EXCLUDE_URLS =
      Stream.concat(COMMON_IGNORE_URL.stream(), SECURITY_EXCLUDE_URL.stream())
          .collect(Collectors.toUnmodifiableSet());

  /**
   * 获取过滤器忽略的URL模式集合
   *
   * @return URL模式集合
   */
  public static Set<String> getCommonIgnoreUrls() {
    return COMMON_IGNORE_URL;
  }

  /**
   * 获取安全相关的排除URL模式集合
   *
   * @return 安全排除URL模式集合
   */
  public static Set<String> getSecurityExcludeUrls() {
    return SECURITY_EXCLUDE_URL;
  }

  /**
   * 获取全部排除路径（公共静态资源 + 安全排除路径）
   *
   * @return 全部排除路径集合
   */
  public static Set<String> getAllExcludeUrls() {
    return ALL_EXCLUDE_URLS;
  }

  /**
   * 获取认证过滤器忽略的服务名称集合
   *
   * @return 服务名称集合
   */
  public static Set<String> getAuthFilterIgnoreServiceNames() {
    return AUTH_FILTER_IGNORE_SERVICE_NAME;
  }
}
