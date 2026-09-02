package com.njydsz.common.safe.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XSS 安全防护配置属性
 *
 * <p>用于配置 XSS 过滤器的行为，包括启用状态、过滤顺序和排除路径。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   safe:
 *     xss:
 *       enabled: true
 *       order: 2
 *       excludes:
 *         - /error
 *         - /actuator/**
 *       # 自定义 XSS 检测模式（正则表达式）
 *       custom-patterns:
 *         - "&lt;script[^&gt;]*&gt;.*?&lt;/script&gt;"
 *         - "javascript:[^\"']*"
 *       # 白名单标签（允许通过的 HTML 标签）
 *       allowed-tags:
 *         - a
 *         - img
 *         - br
 *       # 白名单属性（允许通过的 HTML 属性）
 *       allowed-attributes:
 *         - href
 *         - src
 *         - alt
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.xss")
public class SafeXssProperties {

  /**
   * 是否启用 XSS 过滤器
   *
   * <p>默认值为 true，即启用 XSS 防护。 设置为 false 可全局关闭 XSS 过滤功能。
   */
  private boolean enabled = true;

  /**
   * 过滤器注册顺序
   *
   * <p>数值越小，优先级越高。 默认值为 2，建议在 Web 过滤器之后、业务过滤器之前执行。
   */
  private int order = 2;

  /**
   * 排除路径列表
   *
   * <p>这些路径的请求不会被 XSS 过滤，支持 Ant 风格路径匹配。
   *
   * <ul>
   *   <li>/error：Spring Boot 错误处理端点
   *   <li>/favicon.ico：网站图标
   *   <li>/actuator/**：Spring Boot Actuator 端点
   * </ul>
   */
  private List<String> excludes =
      new ArrayList<>(Arrays.asList("/error", "/favicon.ico", "/actuator/**"));

  /**
   * XSS 处理模式。
   *
   * <p>可选值：
   *
   * <ul>
   *   <li>{@code filter}：使用 Filter 层进行全局参数清洗
   *   <li>{@code converter}：使用 HttpMessageConverter 在 JSON 反序列化时清洗（默认）
   * </ul>
   *
   * <p>两种模式互斥启用，避免双重清洗。 Filter 模式适用于全局统一清洗，Converter 模式适用于仅对 JSON Body 做 XSS 过滤的场景，是推荐的默认模式。
   */
  private Mode mode = Mode.CONVERTER;

  /** XSS 处理模式枚举。 */
  public enum Mode {
    /** Filter 模式：全局参数清洗，通过 XssHttpServletRequestWrapper 实现。 */
    FILTER,

    /** Converter 模式：JSON 反序列化清洗，通过 XssJsonMessageConverter 实现。 */
    CONVERTER
  }

  /**
   * 是否启用 Jackson JSON Body XSS 防护。
   *
   * <p>启用后，通过注册 {@code XssStringDeserializer} 到 Jackson ObjectMapper， 所有 String 字段在 JSON 反序列化时自动进行
   * XSS 清洗。 默认值为 true。
   */
  private boolean jsonEnabled = true;

  /**
   * 自定义 XSS 检测模式（正则表达式列表）
   *
   * <p>额外的 XSS 检测正则表达式，与内置检测规则配合使用。 适用于业务特定的 XSS 攻击模式。
   *
   * <p>示例：
   *
   * <pre>{@code
   * custom-patterns:
   *   - "<script[^>]*>.*?</script>"
   *   - "javascript:[^\"']*"
   *   - "vbscript:[^\"']*"
   * }</pre>
   */
  private List<String> customPatterns = new ArrayList<>(4);

  /**
   * 白名单 HTML 标签
   *
   * <p>允许通过的 HTML 标签列表，其他标签会被过滤。 适用于富文本编辑器场景，允许特定的 HTML 标签。
   *
   * <p>默认白名单：a, img, br, p, div, span, strong, em, ul, ol, li
   *
   * <p>示例：
   *
   * <pre>{@code
   * allowed-tags:
   *   - a
   *   - img
   *   - br
   *   - p
   * }</pre>
   */
  private Set<String> allowedTags =
      new HashSet<>(
          Arrays.asList("a", "img", "br", "p", "div", "span", "strong", "em", "ul", "ol", "li"));

  /**
   * 白名单 HTML 属性
   *
   * <p>允许通过的 HTML 属性列表，其他属性会被过滤。 适用于富文本编辑器场景，允许特定的 HTML 属性。
   *
   * <p>默认白名单：href, src, alt, title, class, id, target
   *
   * <p>示例：
   *
   * <pre>{@code
   * allowed-attributes:
   *   - href
   *   - src
   *   - alt
   *   - title
   * }</pre>
   */
  private Set<String> allowedAttributes =
      new HashSet<>(Arrays.asList("href", "src", "alt", "title", "class", "id", "target"));

  /**
   * 是否启用 HTML 标签白名单过滤
   *
   * <p>启用后，只有 allowedTags 中的标签会被保留，其他标签会被过滤。 默认值为 false，即使用默认的 HTMLFilter 规则。
   */
  private boolean tagWhitelistEnabled = false;

  /**
   * XSS 检测严格级别
   *
   * <p>可选值：
   *
   * <ul>
   *   <li>{@code LOW}：仅检测明显的 XSS 攻击（script 标签、事件处理器）
   *   <li>{@code MEDIUM}：检测常见 XSS 攻击模式（默认）
   *   <li>{@code HIGH}：严格检测，包括编码绕过、Unicode 绕过等
   * </ul>
   */
  private StrictLevel strictLevel = StrictLevel.MEDIUM;

  /** XSS 检测严格级别枚举。 */
  public enum StrictLevel {
    /** 低严格级别：仅检测明显的 XSS 攻击 */
    LOW,

    /** 中等严格级别：检测常见 XSS 攻击模式（默认） */
    MEDIUM,

    /** 高严格级别：严格检测，包括编码绕过、Unicode 绕过等 */
    HIGH
  }
}
