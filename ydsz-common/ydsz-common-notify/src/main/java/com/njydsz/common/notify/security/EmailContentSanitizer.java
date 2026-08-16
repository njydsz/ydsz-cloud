package com.njydsz.common.notify.security;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 邮件内容 XSS/HTML 注入防护器（P0-3）
 *
 * <p>基于 OWASP Java HTML Sanitizer 对 HTML 邮件内容进行标签白名单过滤， 阻止 &lt;script&gt;、onload、onerror
 * 等危险标签和属性注入。
 *
 * <p><b>过滤策略：</b>
 *
 * <ul>
 *   <li>允许基本格式化标签（b、i、u、strong、em、br、p 等）
 *   <li>允许超链接（a），自动添加 rel="nofollow" 和 target="_blank"
 *   <li>允许图片（img），但过滤 javascript: 协议
 *   <li>允许表格、样式标签
 *   <li>完全过滤 script、iframe、object、embed 等危险标签
 * </ul>
 *
 * <p>当 owasp-java-html-sanitizer 依赖不存在时，自动降级为简单转义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class EmailContentSanitizer {

  private EmailContentSanitizer() {}

  private static final Logger LOG = LoggerFactory.getLogger(EmailContentSanitizer.class);

  /** OWASP HTML 安全策略：允许格式化、链接、图片、样式、表格 */
  private static final PolicyFactory EMAIL_POLICY =
      Sanitizers.FORMATTING
          .and(Sanitizers.LINKS)
          .and(Sanitizers.IMAGES)
          .and(Sanitizers.STYLES)
          .and(Sanitizers.TABLES);

  /** 标记 OWASP 依赖是否可用 */
  private static final boolean OWASP_AVAILABLE;

  static {
    boolean available;
    try {
      Class.forName("org.owasp.html.Sanitizers");
      available = true;
    } catch (ClassNotFoundException e) {
      available = false;
    }
    OWASP_AVAILABLE = available;
    if (!available) {
      LOG.warn("[EmailContentSanitizer] owasp-java-html-sanitizer 依赖不存在，降级为简单转义模式");
    }
  }

  /**
   * 清洗 HTML 邮件内容
   *
   * @param htmlContent 原始 HTML 内容
   * @return 清洗后的安全 HTML 内容
   */
  public static String sanitize(String htmlContent) {
    if (!StringUtils.hasText(htmlContent)) {
      return htmlContent;
    }
    if (OWASP_AVAILABLE) {
      try {
        return EMAIL_POLICY.sanitize(htmlContent);
      } catch (Exception e) {
        LOG.error("[EmailContentSanitizer] OWASP 清洗失败，降级为简单转义: {}", e.getMessage());
        return simpleEscape(htmlContent);
      }
    }
    return simpleEscape(htmlContent);
  }

  /**
   * 检测 HTML 内容是否包含 XSS 攻击特征
   *
   * @param content HTML 内容
   * @return true 表示检测到 XSS 攻击
   */
  public static boolean containsXss(String content) {
    if (!StringUtils.hasText(content)) {
      return false;
    }
    String lower = content.toLowerCase();
    return lower.contains("<script")
        || lower.contains("javascript:")
        || lower.contains("onload=")
        || lower.contains("onerror=")
        || lower.contains("onclick=")
        || lower.contains("<iframe")
        || lower.contains("<object")
        || lower.contains("<embed");
  }

  /**
   * 简单 HTML 转义（降级方案）
   *
   * @param html 原始 HTML
   * @return 转义后的文本
   */
  private static String simpleEscape(String html) {
    if (html == null) {
      return null;
    }
    return html.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * 判断 OWASP Sanitizer 是否可用
   *
   * @return true 表示 OWASP 依赖在 classpath 中
   */
  public static boolean isOwaspAvailable() {
    return OWASP_AVAILABLE;
  }
}
