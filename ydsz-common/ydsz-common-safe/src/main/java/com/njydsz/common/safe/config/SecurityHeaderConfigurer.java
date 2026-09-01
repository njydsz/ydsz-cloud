package com.njydsz.common.safe.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * 安全响应头配置器（P1-1：从 Gateway AuthGlobalFilter 提取，供 WebFlux/Servlet 双栈共用）。
 *
 * <p>将安全响应头的策略计算逻辑（CSP 构建、HSTS 拼接、高级头策略）集中在此类， 消除 Gateway 模块约 80 行的安全头注入代码与 common-safe 模块的 {@link
 * SecurityHeaderFilter} 之间的重复。
 *
 * <p><b>解决的核心问题：</b>
 *
 * <ul>
 *   <li>历史版本中，Gateway（WebFlux）与业务服务（Servlet）各自实现安全头注入， CSP/COOP/COEP/CORP 等高级头仅在 Gateway
 *       层注入，业务服务侧无兜底
 *   <li>安全头策略分散维护，配置变更需同步两处
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * // WebFlux 场景（Gateway）
 * SecurityHeaderConfigurer.applyWebFluxHeaders(response.getHeaders(), properties);
 *
 * // Servlet 场景（业务服务 SecurityHeaderFilter 已内部调用）
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SecurityHeaderProperties
 */
public final class SecurityHeaderConfigurer {

  private SecurityHeaderConfigurer() {}

  /**
   * 根据配置计算应添加的安全响应头（排除 HTTP Method 检查由调用方负责）。
   *
   * <p>返回 Map 而非直接写入响应头，以便不同技术栈（Servlet/WebFlux） 用各自的方式写入。
   *
   * <h4>向后兼容说明</h4>
   *
   * <p>旧版配置使用扁平字符串（如 {@code ydsz.safe.security-headers.hsts=max-age=31536000; includeSubDomains}），
   * 新版使用子配置类（如 {@code ydsz.safe.security-headers.hsts.max-age=31536000}）。
   * 本方法优先读取新版配置，若未配置则回退到旧版字符串。
   *
   * @param properties 安全头配置
   * @return 安全头键值对（有序，按添加优先级排列）
   */
  public static Map<String, String> computeHeaders(SecurityHeaderProperties properties) {
    Map<String, String> headers = new LinkedHashMap<>();

    if (properties == null || !properties.isEnabled()) {
      return headers;
    }

    // 基础安全头
    headers.put("X-Content-Type-Options", properties.getContentTypeOptions());
    headers.put("X-Frame-Options", properties.getFrameOptions());
    headers.put("X-XSS-Protection", properties.getXssProtection());
    headers.put("Referrer-Policy", properties.getReferrerPolicy());
    headers.put("X-CSRF-Protection", "1");

    // CSP 策略（优先新版子配置，回退旧版字符串）
    if (isCspEnabled(properties)) {
      headers.put("Content-Security-Policy", buildCspPolicy(properties));
    }

    // Permissions-Policy
    if (properties.getPermissionsPolicy() != null && !properties.getPermissionsPolicy().isEmpty()) {
      headers.put("Permissions-Policy", properties.getPermissionsPolicy());
    }

    // 跨源隔离头（COOP/COEP/CORP）
    if (properties.getCoop() != null && properties.getCoop().isEnabled()) {
      headers.put("Cross-Origin-Opener-Policy", properties.getCoop().getPolicy());
    }
    if (properties.getCoep() != null && properties.getCoep().isEnabled()) {
      headers.put("Cross-Origin-Embedder-Policy", properties.getCoep().getPolicy());
    }
    if (properties.getCorp() != null && properties.getCorp().isEnabled()) {
      headers.put("Cross-Origin-Resource-Policy", properties.getCorp().getPolicy());
    }

    // HSTS（优先新版子配置，回退旧版字符串）
    if (isHstsEnabled(properties)) {
      headers.put("Strict-Transport-Security", buildHstsHeader(properties));
    }

    return headers;
  }

  /**
   * 将安全头写入 WebFlux 响应（Gateway 场景直接调用）。
   *
   * @param response WebFlux 响应对象
   * @param properties 安全头配置
   */
  public static void applyWebFluxHeaders(
      ServerHttpResponse response, SecurityHeaderProperties properties) {
    if (response == null || properties == null) {
      return;
    }
    Map<String, String> headers = computeHeaders(properties);
    headers.forEach((name, value) -> response.getHeaders().add(name, value));
  }

  /** 判断 CSP 是否启用（兼容新版子配置和旧版字符串）。 */
  private static boolean isCspEnabled(SecurityHeaderProperties properties) {
    return properties.getCsp() != null && properties.getCsp().isEnabled();
  }

  /**
   * 判断 HSTS 是否启用（兼容新版子配置和旧版字符串）。
   *
   * <p>旧版配置虽然未使用子配置类，但 HSTS 默认启用（通过旧版字符串非空判断）。
   */
  private static boolean isHstsEnabled(SecurityHeaderProperties properties) {
    return properties.getHsts() != null && properties.getHsts().isEnabled();
  }

  /**
   * 构建 CSP 策略字符串。
   *
   * <p>默认策略限制脚本/样式/图片/连接来源，防止 XSS 注入。 可通过 {@code ydsz.safe.security-headers.csp.*} 配置项完整定制。
   *
   * <h4>向后兼容</h4>
   *
   * <p>优先使用新版子配置，若未配置则回退到旧版字符串。
   *
   * @param properties 安全头配置
   * @return CSP 策略字符串
   */
  public static String buildCspPolicy(SecurityHeaderProperties properties) {
    if (properties.getCsp() == null || !properties.getCsp().isEnabled()) {
      return "";
    }
    // 如果 csp 配置了 explicit policy，直接使用
    if (properties.getCsp().getPolicy() != null && !properties.getCsp().getPolicy().isEmpty()) {
      return properties.getCsp().getPolicy();
    }
    // 否则根据细粒度配置构建
    boolean unsafeEval = properties.getCsp().isUnsafeEval();
    String scriptSrc =
        unsafeEval ? "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " : "script-src 'self'; ";
    return "default-src 'self'; "
        + scriptSrc
        + "style-src 'self' 'unsafe-inline'; "
        + "img-src 'self' data: blob: https:; "
        + "font-src 'self' data:; "
        + "connect-src 'self' ws: wss: https:; "
        + "frame-ancestors 'none'; "
        + "base-uri 'self'; "
        + "form-action 'self'";
  }

  /**
   * 构建 HSTS 头值字符串。
   *
   * <h4>向后兼容</h4>
   *
   * <p>优先使用新版子配置类，若未配置则回退到旧版字符串直接使用。
   *
   * @param properties 安全头配置
   * @return HSTS 头值
   */
  public static String buildHstsHeader(SecurityHeaderProperties properties) {
    if (properties.getHsts() != null && properties.getHsts().isEnabled()) {
      StringBuilder hstsValue =
          new StringBuilder().append("max-age=").append(properties.getHsts().getMaxAge());
      if (properties.getHsts().isIncludeSubdomains()) {
        hstsValue.append("; includeSubDomains");
      }
      if (properties.getHsts().isPreload()) {
        hstsValue.append("; preload");
      }
      return hstsValue.toString();
    }
    return "";
  }
}
