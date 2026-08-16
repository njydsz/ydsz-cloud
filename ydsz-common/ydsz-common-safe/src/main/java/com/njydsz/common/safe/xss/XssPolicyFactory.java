package com.njydsz.common.safe.xss;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * XSS 清洗策略工厂
 *
 * <p>提供多种预设的 OWASP HTML Sanitizer 策略，支持按场景选择不同清洗力度：
 *
 * <ul>
 *   <li>STRICT：仅保留纯文本，移除所有 HTML 标签（API 接口推荐）
 *   <li>STANDARD：保留基本格式化标签（b/i/em/strong/a 等，普通表单推荐）
 *   <li>RELAXED：保留格式化+图片+链接+样式+表格（富文本编辑器推荐）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class XssPolicyFactory {

  private static final Map<String, PolicyFactory> POLICY_CACHE = new ConcurrentHashMap<>();

  /** 策略枚举 */
  public enum Policy {
    /** 严格模式：仅保留纯文本 */
    STRICT,
    /** 标准模式：保留基本格式化标签 */
    STANDARD,
    /** 宽松模式：保留格式化+图片+链接+样式+表格 */
    RELAXED
  }

  private XssPolicyFactory() {}

  /**
   * 获取预设策略
   *
   * @param policy 策略类型
   * @return OWASP PolicyFactory 实例
   */
  public static PolicyFactory getPolicy(Policy policy) {
    return POLICY_CACHE.computeIfAbsent(policy.name(), k -> buildPolicy(policy));
  }

  /**
   * 根据策略名获取策略
   *
   * @param policyName 策略名（STRICT/STANDARD/RELAXED）
   * @return OWASP PolicyFactory 实例
   */
  public static PolicyFactory getPolicy(String policyName) {
    try {
      return getPolicy(Policy.valueOf(policyName.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return getPolicy(Policy.STANDARD);
    }
  }

  private static PolicyFactory buildPolicy(Policy policy) {
    return switch (policy) {
      case STRICT -> Sanitizers.FORMATTING;
      case STANDARD -> Sanitizers.FORMATTING.and(Sanitizers.LINKS);
      case RELAXED ->
          Sanitizers.FORMATTING
              .and(Sanitizers.LINKS)
              .and(Sanitizers.IMAGES)
              .and(Sanitizers.STYLES)
              .and(Sanitizers.TABLES);
    };
  }
}
