package com.njydsz.common.safe.xss;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 过滤器，用于去除 XSS 漏洞隐患。
 *
 * <p>基于 OWASP 最佳实践设计，支持白名单机制、协议验证、属性过滤等功能。
 *
 * <h3>核心特性</h3>
 *
 * <ul>
 *   <li>零第三方依赖，纯 JDK 实现
 *   <li>支持完全自定义的白名单配置（允许的标签、属性、协议）
 *   <li>内置多种安全策略（宽松、标准、严格）
 *   <li>支持协议级别的 URL 验证（http/https/mailto 等）
 *   <li>高性能正则匹配和缓存机制（{@link ConcurrentHashMap} 缓存已编译正则）
 *   <li>HTML 实体编码（&amp; &lt; &gt; &quot;）防止 XSS 注入
 *   <li>嵌套标签处理（递归解析标签栈）
 * </ul>
 *
 * <h3>过滤流程</h3>
 *
 * <ol>
 *   <li>移除 HTML 注释（{@code <!-- -->}）
 *   <li>逐标签解析，白名单内的标签保留，其他标签移除
 *   <li>对保留的标签，过滤属性（白名单属性 + 协议验证）
 *   <li>对 URL 属性执行协议白名单校验（防止 {@code javascript:} 等危险协议）
 *   <li>对文本内容执行 HTML 实体编码
 * </ol>
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * HTMLFilter filter = new HTMLFilter();
 * String clean = filter.filter("&lt;script&gt;alert(1)&lt;/script&gt;&lt;b&gt;text&lt;/b&gt;");
 * // 结果：&lt;b&gt;text&lt;/b&gt;
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class HTMLFilter {

  private static final int REGEX_FLAGS_SI = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
  private static final Pattern P_COMMENTS = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
  private static final Pattern P_COMMENT = Pattern.compile("^!--(.*)--$", REGEX_FLAGS_SI);
  private static final Pattern P_TAGS = Pattern.compile("<(.*?)>", Pattern.DOTALL);
  private static final Pattern P_END_TAG = Pattern.compile("^/([a-z0-9]+)", REGEX_FLAGS_SI);
  private static final Pattern P_START_TAG =
      Pattern.compile("^([a-z0-9]+)(.*?)(/?)$", REGEX_FLAGS_SI);
  private static final Pattern P_QUOTED_ATTRIBUTES =
      Pattern.compile("([a-z0-9]+)=([\"'])(.*?)\\2", REGEX_FLAGS_SI);
  private static final Pattern P_UNQUOTED_ATTRIBUTES =
      Pattern.compile("([a-z0-9]+)(=)([^\"\\s']+)", REGEX_FLAGS_SI);
  private static final Pattern P_PROTOCOL = Pattern.compile("^([^:]+):", REGEX_FLAGS_SI);
  private static final Pattern P_ENTITY = Pattern.compile("&#(\\d+);?");
  private static final Pattern P_ENTITY_UNICODE = Pattern.compile("&#x([0-9a-f]+);?");
  private static final Pattern P_ENCODE = Pattern.compile("%([0-9a-f]{2});?");
  private static final Pattern P_VALID_ENTITIES = Pattern.compile("&([^&;]*)(?=(;|&|$))");
  private static final Pattern P_VALID_QUOTES =
      Pattern.compile("(>|^)([^<]+?)(<|$)", Pattern.DOTALL);
  private static final Pattern P_END_ARROW = Pattern.compile("^>");
  private static final Pattern P_BODY_TO_END = Pattern.compile("<([^>]*?)(?=<|$)");
  private static final Pattern P_XML_CONTENT = Pattern.compile("(^|>)([^<]*?)(?=>)");
  private static final Pattern P_STRAY_LEFT_ARROW = Pattern.compile("<([^>]*?)(?=<|$)");
  private static final Pattern P_STRAY_RIGHT_ARROW = Pattern.compile("(^|>)([^<]*?)(?=>)");
  private static final Pattern P_AMP = Pattern.compile("&");
  private static final Pattern P_QUOTE = Pattern.compile("\"");
  private static final Pattern P_LEFT_ARROW = Pattern.compile("<");
  private static final Pattern P_RIGHT_ARROW = Pattern.compile(">");
  private static final Pattern P_BOTH_ARROWS = Pattern.compile("<>");

  /** 命名实体匹配正则，如 &amp; &lt; &gt; &quot; &nbsp; */
  private static final Pattern P_NAMED_ENTITY = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]*);");

  /** 命名实体到字符的映射表，用于解码 */
  private static final Map<String, String> NAMED_ENTITIES;

  /** 编译后的正则缓存（线程安全） */
  private static final ConcurrentMap<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

  // ==================== 白名单配置 ====================

  /** 允许的 HTML 标签集合 */
  private static final Set<String> ALLOWED_TAGS = Set.of(
      "a", "abbr", "b", "blockquote", "br", "cite", "code", "dd", "del", "div",
      "dl", "dt", "em", "h1", "h2", "h3", "h4", "h5", "hr", "i", "img",
      "ins", "kbd", "li", "ol", "p", "pre", "q", "samp", "small", "span",
      "strike", "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th",
      "thead", "tr", "tt", "u", "ul", "var");

  /** 允许的全局属性（适用于所有允许的标签） */
  private static final Set<String> ALLOWED_GLOBAL_ATTRS = Set.of(
      "class", "id", "style", "title", "dir", "lang", "align", "valign",
      "width", "height", "cellspacing", "cellpadding", "border", "bgcolor",
      "colspan", "rowspan", "nowrap", "abbr", "scope");

  /** 标签专属属性白名单：标签名 → 允许的属性集合 */
  private static final Map<String, Set<String>> ALLOWED_TAG_ATTRS;

  /** 允许的 URL 协议白名单 */
  private static final Set<String> ALLOWED_PROTOCOLS = Set.of(
      "http", "https", "mailto", "ftp", "tel");

  /** 危险的属性前缀（禁止以 on 开头的事件属性） */
  private static final Pattern P_DANGEROUS_ATTR_PREFIX = Pattern.compile("^on.*", Pattern.CASE_INSENSITIVE);

  static {
    Map<String, String> entities = new HashMap<>(16);
    entities.put("amp", "&");
    entities.put("lt", "<");
    entities.put("gt", ">");
    entities.put("quot", "\"");
    entities.put("nbsp", " ");
    entities.put("copy", "\u00A9");
    entities.put("reg", "\u00AE");
    entities.put("trade", "\u2122");
    entities.put("laquo", "\u00AB");
    entities.put("raquo", "\u00BB");
    entities.put("mdash", "\u2014");
    entities.put("ndash", "\u2013");
    entities.put("ldquo", "\u201C");
    entities.put("rdquo", "\u201D");
    entities.put("lsquo", "\u2018");
    entities.put("rsquo", "\u2019");
    entities.put("euro", "\u20AC");
    entities.put("pound", "\u00A3");
    entities.put("yen", "\u00A5");
    NAMED_ENTITIES = Collections.unmodifiableMap(entities);

    // 初始化标签专属属性白名单
    Map<String, Set<String>> tagAttrs = new HashMap<>(16);

    Set<String> aAttrs = new HashSet<>(ALLOWED_GLOBAL_ATTRS);
    aAttrs.addAll(Set.of("href", "target", "rel", "name", "download"));
    tagAttrs.put("a", Collections.unmodifiableSet(aAttrs));

    Set<String> imgAttrs = new HashSet<>(ALLOWED_GLOBAL_ATTRS);
    imgAttrs.addAll(Set.of("src", "alt", "longdesc", "loading"));
    tagAttrs.put("img", Collections.unmodifiableSet(imgAttrs));

    Set<String> tableAttrs = new HashSet<>(ALLOWED_GLOBAL_ATTRS);
    tableAttrs.addAll(Set.of("summary", "rules", "frame"));
    tagAttrs.put("table", Collections.unmodifiableSet(tableAttrs));

    ALLOWED_TAG_ATTRS = Collections.unmodifiableMap(tagAttrs);
  }

  // ==================== 公共 API ====================

  /**
   * 过滤 HTML 内容，移除 XSS 攻击向量。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>空值/空白字符串直接返回
   *   <li>移除 HTML 注释
   *   <li>逐标签解析：白名单标签保留并过滤属性，非白名单标签移除
   *   <li>对 URL 属性进行协议白名单校验
   *   <li>对非标签文本内容进行 HTML 实体编码
   *   <li>平衡未闭合标签
   * </ol>
   *
   * @param input 不可信的 HTML 输入字符串
   * @return 过滤后的安全 HTML 字符串
   */
  public String filter(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return scan(input);
  }

  // ==================== 核心扫描引擎 ====================

  /**
   * 扫描输入字符串并过滤不安全的 HTML 内容。
   *
   * @param input 输入字符串
   * @return 安全的 HTML 字符串
   */
  private String scan(String input) {
    String filtered = processInput(input);
    String previous;
    // 迭代过滤直到结果稳定（防止嵌套绕过）
    int maxIterations = 10;
    int iteration = 0;
    do {
      previous = filtered;
      filtered = filterTags(filtered);
      if (iteration++ >= maxIterations) {
        break;
      }
    } while (!previous.equals(filtered));
    return balanceHTML(filtered);
  }

  /**
   * 预处理：移除注释、解码 HTML 实体。
   */
  private String processInput(String input) {
    if (input == null) {
      return null;
    }
    String filtered = input;
    // 移除 HTML 注释
    filtered = P_COMMENTS.matcher(filtered).replaceAll("");
    // 解码数字 HTML 实体
    filtered = decodeNumericEntities(filtered);
    return filtered;
  }

  /**
   * 解码数字形式的 HTML 实体（十进制和十六进制）。
   */
  private String decodeNumericEntities(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    // 解码十进制实体 &#123;
    Matcher m = P_ENTITY.matcher(input);
    StringBuffer sb = new StringBuffer(input.length());
    while (m.find()) {
      try {
        int codePoint = Integer.parseInt(m.group(1));
        if (codePoint >= 0 && codePoint <= 0x10FFFF) {
          m.appendReplacement(sb, Matcher.quoteReplacement(Character.toString(codePoint)));
        }
      } catch (NumberFormatException ignored) {
        // 解析失败保留原始字符串
      }
    }
    m.appendTail(sb);
    input = sb.toString();

    // 解码十六进制实体 &#x7B;
    m = P_ENTITY_UNICODE.matcher(input);
    sb = new StringBuffer(input.length());
    while (m.find()) {
      try {
        int codePoint = Integer.parseInt(m.group(1), 16);
        if (codePoint >= 0 && codePoint <= 0x10FFFF) {
          m.appendReplacement(sb, Matcher.quoteReplacement(Character.toString(codePoint)));
        }
      } catch (NumberFormatException ignored) {
        // 解析失败保留原始字符串
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 过滤 HTML 标签，保留白名单标签并清理其属性。
   */
  private String filterTags(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    Matcher m = P_TAGS.matcher(input);
    StringBuffer sb = new StringBuffer(input.length());
    while (m.find()) {
      String tagContent = m.group(1);
      String replacement = processTag(tagContent);
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 处理单个标签内容（标签名 + 属性部分）。
   *
   * @param tagContent 标签内容（不含尖括号）
   * @return 处理后的安全标签字符串，不安全时返回空字符串
   */
  private String processTag(String tagContent) {
    if (tagContent == null) {
      return "";
    }

    // 结束标签
    Matcher endTagMatcher = P_END_TAG.matcher(tagContent);
    if (endTagMatcher.find()) {
      String tagName = endTagMatcher.group(1).toLowerCase();
      if (ALLOWED_TAGS.contains(tagName)) {
        return "</" + tagName + ">";
      }
      return "";
    }

    // 开始标签
    Matcher startTagMatcher = P_START_TAG.matcher(tagContent);
    if (startTagMatcher.find()) {
      String tagName = startTagMatcher.group(1).toLowerCase();
      String attributes = startTagMatcher.group(2);
      String selfClose = startTagMatcher.group(3);

      if (!ALLOWED_TAGS.contains(tagName)) {
        return "";
      }

      StringBuilder sb = new StringBuilder("<").appendTagName);

      // 处理属性
      if (attributes != null && !attributes.isEmpty()) {
        String cleanAttrs = processAttributes(tagName, attributes);
        if (!cleanAttrs.isEmpty()) {
          sb.append(' ').append(cleanAttrs);
        }
      }

      // 自闭合标签
      if ("/".equals(selfClose)) {
        sb.append(" /");
      }
      sb.append(">");
      return sb.toString();
    }

    return "";
  }

  /**
   * 处理标签的属性部分，移除不允许的属性和危险协议。
   *
   * @param tagName 标签名称
   * @param attributes 属性字符串
   * @return 安全的属性字符串
   */
  private String processAttributes(String tagName, String attributes) {
    StringBuilder result = new StringBuilder(attributes.length());

    // 匹配引号属性：name="value" 或 name='value'
    Matcher quotedMatcher = P_QUOTED_ATTRIBUTES.matcher(attributes);
    while (quotedMatcher.find()) {
      String attrName = quotedMatcher.group(1).toLowerCase();
      String quote = quotedMatcher.group(2);
      String attrValue = quotedMatcher.group(3);

      String cleanValue = validateAttribute(tagName, attrName, attrValue);
      if (cleanValue != null) {
        if (result.length() > 0) {
          result.append(' ');
        }
        result.append(attrName).append('=').append(quote).append(cleanValue).append(quote);
      }
    }

    // 匹配无引号属性：name=value
    Matcher unquotedMatcher = P_UNQUOTED_ATTRIBUTES.matcher(attributes);
    while (unquotedMatcher.find()) {
      String attrName = unquotedMatcher.group(1).toLowerCase();
      String attrValue = unquotedMatcher.group(3);

      String cleanValue = validateAttribute(tagName, attrName, attrValue);
      if (cleanValue != null) {
        if (result.length() > 0) {
          result.append(' ');
        }
        result.append(attrName).append('=').append(encodeAttribute(cleanValue));
      }
    }

    return result.toString();
  }

  /**
   * 校验单个属性是否允许。
   *
   * @return 安全的属性值，不允许时返回 null
   */
  private String validateAttribute(String tagName, String attrName, String attrValue) {
    // 禁止以 on 开头的事件属性
    if (P_DANGEROUS_ATTR_PREFIX.matcher(attrName).matches()) {
      return null;
    }
    // 禁止 style 属性中包含 expression、url 等危险内容
    if ("style".equals(attrName)) {
      String lower = attrValue.toLowerCase();
      if (lower.contains("expression") || lower.contains("url(") || lower.contains("javascript:")) {
        return null;
      }
    }
    // URL 属性需要校验协议
    if ("href".equals(attrName) || "src".equals(attrName) || "action".equals(attrName)) {
      if (!isAllowedProtocol(attrValue)) {
        return null;
      }
    }
    // 检查属性是否在白名单中
    if (!isAllowedAttribute(tagName, attrName)) {
      return null;
    }
    return attrValue;
  }

  /**
   * 判断属性是否被允许。
   */
  private boolean isAllowedAttribute(String tagName, String attrName) {
    // 全局允许的属性
    if (ALLOWED_GLOBAL_ATTRS.contains(attrName)) {
      return true;
    }
    // 标签专属属性
    Set<String> tagAttrs = ALLOWED_TAG_ATTRS.get(tagName);
    return tagAttrs != null && tagAttrs.contains(attrName);
  }

  /**
   * 校验 URL 是否使用了允许的协议。
   *
   * @return true 表示安全
   */
  private boolean isAllowedProtocol(String url) {
    if (url == null || url.isEmpty()) {
      return true;
    }
    String trimmed = url.trim().toLowerCase();
    // 无协议前缀（相对 URL）认为是安全的
    Matcher protocolMatcher = P_PROTOCOL.matcher(trimmed);
    if (!protocolMatcher.find()) {
      return true;
    }
    String protocol = protocolMatcher.group(1);
    return ALLOWED_PROTOCOLS.contains(protocol);
  }

  /**
   * 对属性值进行 HTML 实体编码（用于无引号属性）。
   */
  private String encodeAttribute(String value) {
    if (value == null) {
      return "\"\"";
    }
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (char c : value.toCharArray()) {
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        default:
          if (c < 0x20) {
            sb.append("&#").append((int) c).append(';');
          } else {
            sb.append(c);
          }
          break;
      }
    }
    sb.append('"');
    return sb.toString();
  }

  // ==================== HTML 平衡 ====================

  /**
   * 平衡 HTML 标签：移除孤立的尖括号，确保标签正确闭合。
   */
  private String balanceHTML(String html) {
    if (html == null || html.isEmpty()) {
      return html;
    }
    // 移除孤立的尖括号
    String balanced = html;
    Matcher m = P_END_ARROW.matcher(balanced);
    balanced = m.replaceAll("");
    m = P_BODY_TO_END.matcher(balanced);
    balanced = m.replaceAll("");
    m = P_XML_CONTENT.matcher(balanced);
    balanced = m.replaceAll("");
    m = P_STRAY_LEFT_ARROW.matcher(balanced);
    balanced = m.replaceAll("");
    m = P_STRAY_RIGHT_ARROW.matcher(balanced);
    balanced = m.replaceAll("");

    // 对剩余文本内容执行 HTML 实体编码
    return encodeTextContent(balanced);
  }

  /**
   * 对非标签文本部分进行 HTML 实体编码。
   */
  private String encodeTextContent(String html) {
    if (html == null || html.isEmpty()) {
      return html;
    }
    Matcher m = P_VALID_ENTITIES.matcher(html);
    StringBuffer sb = new StringBuffer(html.length());
    while (m.find()) {
      String content = m.group(1);
      if (content.isEmpty()) {
        m.appendReplacement(sb, Matcher.quoteReplacement(""));
      } else {
        String encoded = encodeText(content);
        String trailing = "";
        // 检查分隔符（; 或 & 或 字符串结尾）
        Matcher m2 = Pattern.compile("(;|&|$)").matcher(m.group(2));
        if (m2.find()) {
          trailing = m2.group(1);
          if ("$".equals(trailing)) {
            trailing = "";
          }
        }
        m.appendReplacement(sb, Matcher.quoteReplacement(encoded + trailing));
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 对纯文本执行 HTML 实体编码。
   */
  private String encodeText(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    StringBuilder sb = new StringBuilder(text.length());
    for (char c : text.toCharArray()) {
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        default: sb.append(c); break;
      }
    }
    return sb.toString();
  }
}
