package com.njydsz.common.safe.xss;

import org.owasp.html.PolicyFactory;

/**
 * 基于 OWASP 的 XSS 清理器实现
 *
 * <p>替代自定义 HTMLFilter，使用 OWASP Java HTML Sanitizer 实现，提供更好的安全性和可维护性。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li>业界标准 XSS 防护
 *   <li>可配置的清洗策略
 *   <li>高性能和安全性
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class OwaspXssCleaner {

  private OwaspXssCleaner() {}

  private static final PolicyFactory DEFAULT_POLICY =
      XssPolicyFactory.getPolicy(XssPolicyFactory.Policy.STANDARD);

  /**
   * 使用默认策略清洗 HTML 内容
   *
   * @param dirtyHtml 不可信的 HTML 内容
   * @return 清洗后的 HTML 内容
   */
  public static String clean(String dirtyHtml) {
    return clean(dirtyHtml, DEFAULT_POLICY);
  }

  /**
   * 使用指定策略清洗 HTML 内容
   *
   * @param dirtyHtml 不可信的 HTML 内容
   * @param policy XSS 清洗策略
   * @return 清洗后的 HTML 内容
   */
  public static String clean(String dirtyHtml, XssPolicyFactory.Policy policy) {
    return clean(dirtyHtml, XssPolicyFactory.getPolicy(policy));
  }

  /**
   * 使用指定的 PolicyFactory 清洗 HTML 内容
   *
   * @param dirtyHtml 不可信的 HTML 内容
   * @param policy OWASP PolicyFactory 实例
   * @return 清洗后的 HTML 内容
   */
  public static String clean(String dirtyHtml, PolicyFactory policy) {
    if (dirtyHtml == null || dirtyHtml.isEmpty()) {
      return dirtyHtml;
    }
    return policy.sanitize(dirtyHtml);
  }

  /**
   * 清洗 JSON 字符串值，防止 XSS 攻击
   *
   * @param jsonString 可能包含 XSS 的 JSON 字符串
   * @return 清洗后的 JSON 字符串
   */
  public static String cleanJsonValue(String jsonString) {
    if (jsonString == null || jsonString.isEmpty()) {
      return jsonString;
    }
    // For JSON, we sanitize the entire string
    return DEFAULT_POLICY.sanitize(jsonString);
  }

  /**
   * 检查内容是否包含潜在的 XSS 攻击
   *
   * @param content 待检查的内容
   * @return 检测到 XSS 返回 true，否则返回 false
   */
  public static boolean containsXSS(String content) {
    if (content == null || content.isEmpty()) {
      return false;
    }
    String sanitized = clean(content);
    return !content.equals(sanitized);
  }
}
