package com.njydsz.common.safe.xss;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.njydsz.common.util.string.StringUtils;

/**
 * EscapeUtils - HTML 转义工具类 (基于标准化实现)
 *
 * <p>参考 OWASP、阿里巴巴、Hutool 等互联网大厂最佳实践设计，提供全面的 HTML 转义和 XSS 防护能力。
 *
 * <p>核心特性： 1. 零第三方依赖 - 纯 JDK 实现，不依赖 Apache Commons Text 等外部库 2. 高性能 - 使用预编译正则和 StringBuilder 优化 3.
 * 全面覆盖 - 支持 HTML4/HTML5 实体、JavaScript、CSS、URL 等多场景转义 4. 灵活配置 - 支持自定义白名单、黑名单、转义级别 5. 双向操作 -
 * 同时支持转义和反转义 6. 危险协议过滤 - 禁止 javascript:、data:、vbscript: 等危险 URL 协议
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class EscapeUtils {

  private EscapeUtils() {}

  /** 危险的 URL 协议列表，大小写不敏感匹配 */
  private static final String[] DANGEROUS_PROTOCOLS = {"javascript:", "data:", "vbscript:"};

  private static final Pattern[] HTML_PATTERNS = {
    Pattern.compile("&"),
    Pattern.compile("<"),
    Pattern.compile(">"),
    Pattern.compile("\""),
    Pattern.compile("'")
  };

  private static final String[] HTML_ENTITIES = {"&amp;", "&lt;", "&gt;", "&quot;", "&#39;"};

  private static final Pattern P_ENTITY_DECIMAL = Pattern.compile("&#(\\d+);?");
  private static final Pattern P_ENTITY_HEX = Pattern.compile("&#x([0-9a-fA-F]+);?");
  private static final Pattern URL_PATTERN = Pattern.compile("[^a-zA-Z0-9\\-_.~!$'()*+,;=:@/?]");

  /**
   * 转义 HTML 特殊字符（HTML4 标准）
   *
   * <p>将以下字符转换为对应的 HTML 实体：
   *
   * <ul>
   *   <li>{@code &} → {@code &amp;}
   *   <li>{@code <} → {@code &lt;}
   *   <li>{@code >} → {@code &gt;}
   *   <li>{@code "} → {@code &quot;}
   *   <li>{@code '} → {@code &#39;}
   * </ul>
   *
   * @param text 待转义的文本
   * @return 转义后的文本，如果输入为空则返回原值
   */
  public static String escape(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    String result = text;
    for (int i = 0; i < HTML_PATTERNS.length; i++) {
      result = HTML_PATTERNS[i].matcher(result).replaceAll(HTML_ENTITIES[i]);
    }
    return result;
  }

  /**
   * 转义 HTML 特殊字符（HTML4 标准），{@link #escape(String)} 的别名
   *
   * @param text 待转义的文本
   * @return 转义后的文本
   */
  public static String escapeHtml4(String text) {
    return escape(text);
  }

  /**
   * 转义 HTML 特殊字符（HTML5 标准）
   *
   * <p>在 HTML4 基础上，额外转义以下字符： 空格、版权符号、注册商标、商标、省略号、破折号等。
   *
   * @param text 待转义的文本
   * @return 转义后的文本
   */
  public static String escapeHtml5(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    String result = escape(text);
    result = replaceNamedEntity(result, " ", "&nbsp;");
    result = replaceNamedEntity(result, "\u00A9", "&copy;");
    result = replaceNamedEntity(result, "\u00AE", "&reg;");
    result = replaceNamedEntity(result, "\u2122", "&trade;");
    result = replaceNamedEntity(result, "\u2026", "&hellip;");
    result = replaceNamedEntity(result, "\u2014", "&mdash;");
    result = replaceNamedEntity(result, "\u2013", "&ndash;");
    result = replaceNamedEntity(result, "\u201C", "&ldquo;");
    result = replaceNamedEntity(result, "\u201D", "&rdquo;");
    result = replaceNamedEntity(result, "\u2018", "&lsquo;");
    result = replaceNamedEntity(result, "\u2019", "&rsquo;");

    return result;
  }

  private static String replaceNamedEntity(String text, String ch, String entity) {
    return text.replace(ch, entity);
  }

  /**
   * 反转义 HTML 实体字符
   *
   * <p>将 HTML 实体还原为对应字符，支持十进制和十六进制数字实体。
   *
   * @param text 包含 HTML 实体的文本
   * @return 反转义后的文本
   */
  public static String unescape(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    String result = text;
    result =
        P_ENTITY_DECIMAL
            .matcher(result)
            .replaceAll(
                m -> {
                  try {
                    int code = Integer.parseInt(m.group(1));
                    return String.valueOf((char) code);
                  } catch (NumberFormatException e) {
                    return m.group(0);
                  }
                });

    result =
        P_ENTITY_HEX
            .matcher(result)
            .replaceAll(
                m -> {
                  try {
                    int code = Integer.parseInt(m.group(1), 16);
                    return String.valueOf((char) code);
                  } catch (NumberFormatException e) {
                    return m.group(0);
                  }
                });

    result = result.replace("&amp;", "&");
    result = result.replace("&lt;", "<");
    result = result.replace("&gt;", ">");
    result = result.replace("&quot;", "\"");
    result = result.replace("&#39;", "'");
    result = result.replace("&apos;", "'");
    result = result.replace("&nbsp;", " ");
    result = result.replace("&copy;", "\u00A9");
    result = result.replace("&reg;", "\u00AE");
    result = result.replace("&trade;", "\u2122");
    result = result.replace("&hellip;", "\u2026");
    result = result.replace("&mdash;", "\u2014");
    result = result.replace("&ndash;", "\u2013");
    result = result.replace("&ldquo;", "\u201C");
    result = result.replace("&rdquo;", "\u201D");
    result = result.replace("&lsquo;", "\u2018");
    result = result.replace("&rsquo;", "\u2019");

    return result;
  }

  /**
   * 反转义 HTML4 实体字符，{@link #unescape(String)} 的别名
   *
   * @param text 包含 HTML 实体的文本
   * @return 反转义后的文本
   */
  public static String unescapeHtml4(String text) {
    return unescape(text);
  }

  /**
   * 反转义 HTML5 实体字符，{@link #unescape(String)} 的别名
   *
   * @param text 包含 HTML 实体的文本
   * @return 反转义后的文本
   */
  public static String unescapeHtml5(String text) {
    return unescape(text);
  }

  /**
   * 清理内容中的 XSS 攻击代码（默认策略）
   *
   * <p>先过滤危险协议（javascript:、data:、vbscript:），再委托 OWASP Java HTML Sanitizer
   * 的 STANDARD 策略清洗，替代早期基于自定义正则的 {@link HTMLFilter} 实现，提供业界标准级防护。
   *
   * @param content 待清理的内容
   * @return 清理后的内容
   */
  public static String clean(String content) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }
    String filtered = filterDangerousProtocols(content);
    return OwaspXssCleaner.clean(filtered);
  }

  /**
   * 过滤危险的 URL 协议。
   *
   * <p>禁止 javascript:、data:、vbscript: 等危险协议，大小写不敏感匹配。
   *
   * @param content 待过滤的内容
   * @return 过滤后的内容
   */
  private static String filterDangerousProtocols(String content) {
    String result = content;
    for (String protocol : DANGEROUS_PROTOCOLS) {
      result = result.replaceAll("(?i)" + Pattern.quote(protocol), "blocked:");
    }
    return result;
  }

  /**
   * 使用宽松策略清理内容中的 XSS 攻击代码
   *
   * <p>保留格式化+图片+链接+样式+表格，适用于富文本编辑器场景（OWASP RELAXED 策略）。
   *
   * @param content 待清理的内容
   * @return 清理后的内容
   */
  public static String cleanRelaxed(String content) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }
    return OwaspXssCleaner.clean(content, XssPolicyFactory.Policy.RELAXED);
  }

  /**
   * 使用标准策略清理内容中的 XSS 攻击代码
   *
   * <p>保留基本格式化标签（b/i/em/strong/a 等），适用于普通表单场景（OWASP STANDARD 策略）。
   *
   * @param content 待清理的内容
   * @return 清理后的内容
   */
  public static String cleanStandard(String content) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }
    return OwaspXssCleaner.clean(content, XssPolicyFactory.Policy.STANDARD);
  }

  /**
   * 使用严格策略清理内容中的 XSS 攻击代码
   *
   * <p>仅保留纯文本，移除所有 HTML 标签，适用于 API 接口场景（OWASP STRICT 策略）。
   *
   * @param content 待清理的内容
   * @return 清理后的内容
   */
  public static String cleanStrict(String content) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }
    return OwaspXssCleaner.clean(content, XssPolicyFactory.Policy.STRICT);
  }

  /**
   * 使用自定义 HTMLFilter 清理内容中的 XSS 攻击代码
   *
   * @param content 待清理的内容
   * @param filter 自定义 HTMLFilter 实例
   * @return 清理后的内容
   */
  public static String cleanCustom(String content, HTMLFilter filter) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }
    return filter.filter(content);
  }

  /**
   * 转义 JavaScript 字符串中的特殊字符
   *
   * <p>将以下字符转换为转义序列： 双引号、单引号、反斜杠、斜杠、尖括号、控制字符等。 非 ASCII 字符使用 {@code &#92;uXXXX} 格式转义。
   *
   * @param text 待转义的文本
   * @return 转义后的 JavaScript 安全字符串
   */
  public static String escapeJavaScript(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\'':
          sb.append("\\'");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '/':
          sb.append("\\/");
          break;
        case '<':
          sb.append("\\x3C");
          break;
        case '>':
          sb.append("\\x3E");
          break;
        case '&':
          sb.append("\\x26");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        default:
          if (c < 32 || c > 126) {
            sb.append("\\u").append(String.format("%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  /**
   * 反转义 JavaScript 转义序列
   *
   * @param text 包含 JavaScript 转义序列的文本
   * @return 反转义后的文本
   */
  public static String unescapeJavaScript(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length()) {
        char next = text.charAt(i + 1);
        switch (next) {
          case '"':
            sb.append('"');
            i += 2;
            continue;
          case '\'':
            sb.append('\'');
            i += 2;
            continue;
          case '\\':
            sb.append('\\');
            i += 2;
            continue;
          case '/':
            sb.append('/');
            i += 2;
            continue;
          case 'r':
            sb.append('\r');
            i += 2;
            continue;
          case 'n':
            sb.append('\n');
            i += 2;
            continue;
          case 't':
            sb.append('\t');
            i += 2;
            continue;
          case 'b':
            sb.append('\b');
            i += 2;
            continue;
          case 'f':
            sb.append('\f');
            i += 2;
            continue;
          case 'x':
            if (i + 3 < text.length()) {
              try {
                int code = Integer.parseInt(text.substring(i + 2, i + 4), 16);
                sb.append((char) code);
                i += 4;
                continue;
              } catch (NumberFormatException e) {
                // 非法十六进制实体，保留原字符继续处理
              }
            }
            break;
          case 'u':
            if (i + 5 < text.length()) {
              try {
                int code = Integer.parseInt(text.substring(i + 2, i + 6), 16);
                sb.append((char) code);
                i += 6;
                continue;
              } catch (NumberFormatException e) {
                // 非法 Unicode 实体，保留原字符继续处理
              }
            }
            break;
          default:
            // 未知转义序列：保留反斜杠原样输出，由后续 sb.append(c) 处理
            break;
        }
      }
      sb.append(c);
      i++;
    }
    return sb.toString();
  }

  /**
   * 转义 CSS 字符串中的特殊字符
   *
   * <p>将双引号、单引号、反斜杠、尖括号、花括号等转换为 CSS 十六进制转义序列。
   *
   * @param text 待转义的 CSS 文本
   * @return 转义后的 CSS 安全字符串
   */
  public static String escapeCSS(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\22 ");
          break;
        case '\'':
          sb.append("\\27 ");
          break;
        case '\\':
          sb.append("\\5C ");
          break;
        case '<':
          sb.append("\\3C ");
          break;
        case '>':
          sb.append("\\3E ");
          break;
        case '{':
          sb.append("\\7B ");
          break;
        case '}':
          sb.append("\\7D ");
          break;
        case '(':
          sb.append("\\28 ");
          break;
        case ')':
          sb.append("\\29 ");
          break;
        case ';':
          sb.append("\\3B ");
          break;
        case '&':
          sb.append("\\26 ");
          break;
        default:
          if (c < 32 || c > 126 || (c >= '0' && c <= '9')) {
            sb.append("\\").append(String.format("%X ", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  /**
   * 转义 URL 中的特殊字符
   *
   * <p>将非安全字符转换为百分号编码（{@code %XX}）。
   *
   * @param text 待转义的 URL 文本
   * @return 转义后的 URL 安全字符串
   */
  public static String escapeURL(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (URL_PATTERN.matcher(String.valueOf(c)).find()) {
        sb.append(String.format("%%%02X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * 转义 XML 特殊字符
   *
   * @param text 待转义的 XML 文本
   * @return 转义后的 XML 安全字符串
   */
  public static String escapeXML(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    String result = text;
    result = result.replace("&", "&amp;");
    result = result.replace("<", "&lt;");
    result = result.replace(">", "&gt;");
    result = result.replace("\"", "&quot;");
    result = result.replace("'", "&apos;");
    return result;
  }

  /**
   * 反转义 XML 实体字符
   *
   * @param text 包含 XML 实体的文本
   * @return 反转义后的文本
   */
  public static String unescapeXML(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }

    String result = text;
    result = result.replace("&apos;", "'");
    result = result.replace("&quot;", "\"");
    result = result.replace("&gt;", ">");
    result = result.replace("&lt;", "<");
    result = result.replace("&amp;", "&");
    return result;
  }

  /**
   * 移除内容中的所有 HTML 标签
   *
   * @param content 包含 HTML 标签的内容
   * @return 移除标签后的纯文本
   */
  public static String stripTags(String content) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }
    return content.replaceAll("<[^>]*>", "");
  }

  /**
   * 移除内容中的 HTML 标签（保留指定标签）
   *
   * @param content 包含 HTML 标签的内容
   * @param allowedTags 允许保留的标签名列表
   * @return 移除标签后的文本
   */
  public static String stripTags(String content, String... allowedTags) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }

    if (allowedTags == null || allowedTags.length == 0) {
      return stripTags(content);
    }

    StringBuilder patternBuilder = new StringBuilder("<(?!(?i)");
    for (int i = 0; i < allowedTags.length; i++) {
      if (i > 0) {
        patternBuilder.append("|");
      }
      patternBuilder.append(allowedTags[i]);
      patternBuilder.append("\\b");
    }
    patternBuilder.append(")[^>]*>");

    Pattern pattern = Pattern.compile(patternBuilder.toString());
    Matcher matcher = pattern.matcher(content);
    return matcher.replaceAll("");
  }

  /**
   * 移除 HTML 标签中的属性（保留指定属性）
   *
   * @param content 包含 HTML 标签的内容
   * @param allowedAttributes 允许保留的属性名列表
   * @return 移除属性后的文本
   */
  public static String stripAttributes(String content, String... allowedAttributes) {
    if (StringUtils.isEmpty(content)) {
      return content;
    }

    if (allowedAttributes == null || allowedAttributes.length == 0) {
      return content.replaceAll(
          "\\s+[a-zA-Z][a-zA-Z0-9_-]*\\s*=\\s*([\"'][^\"']*?[\"']|[^\\s>]+)", "");
    }

    Pattern tagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
    Matcher matcher = tagPattern.matcher(content);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String tagName = matcher.group(1);
      String attributes = matcher.group(2);

      StringBuilder filteredAttrs = new StringBuilder();
      Pattern attrPattern =
          Pattern.compile("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*([\"'][^\"']*?[\"']|[^\\s>]+)");
      Matcher attrMatcher = attrPattern.matcher(attributes);

      while (attrMatcher.find()) {
        String attrName = attrMatcher.group(1);
        boolean allowed = false;
        for (String allowedAttr : allowedAttributes) {
          if (allowedAttr.equalsIgnoreCase(attrName)) {
            allowed = true;
            break;
          }
        }
        if (allowed) {
          filteredAttrs.append(" ").append(attrMatcher.group(0));
        }
      }

      matcher.appendReplacement(sb, "<" + tagName + filteredAttrs + ">");
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  /**
   * 检查内容是否包含潜在的 XSS 攻击
   *
   * <p>委托 {@link OwaspXssCleaner#containsXSS}，以 OWASP sanitizer 的清洗结果为准：
   * 若清洗前后内容不一致，判定存在潜在 XSS。与 {@link #clean(String)} 的清洗口径保持一致。
   *
   * @param content 待检查的内容
   * @return 检测到 XSS 返回 true，否则返回 false
   */
  public static boolean containsXSS(String content) {
    return OwaspXssCleaner.containsXSS(content);
  }

  /**
   * 清理内容中的 XSS 攻击代码（宽松策略别名）
   *
   * @param content 待清理的内容
   * @return 清理后的内容
   */
  public static String sanitize(String content) {
    return cleanRelaxed(content);
  }

  /**
   * Base64 编码
   *
   * @param text 待编码的文本
   * @return Base64 编码后的字符串
   */
  public static String encodeBase64(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Base64 解码
   *
   * @param text 待解码的 Base64 字符串
   * @return 解码后的文本，解码失败时返回原值
   */
  public static String decodeBase64(String text) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }
    try {
      return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return text;
    }
  }

  /**
   * 清理 JSON 值中的 XSS 攻击内容。
   *
   * <p>使用流式解析方式递归清理 JSON 字符串值，不破坏 JSON 结构。 仅处理 JSON 字符串值，其他类型保持不变。
   *
   * @param json JSON 字符串
   * @return 清理后的 JSON 字符串
   */
  public static String cleanJsonValue(String json) {
    if (StringUtils.isEmpty(json)) {
      return json;
    }
    StringBuilder result = new StringBuilder();
    int length = json.length();
    int i = 0;

    while (i < length) {
      char c = json.charAt(i);

      if (c == '"') {
        StringBuilder value = new StringBuilder();
        i++;
        while (i < length) {
          char ch = json.charAt(i);
          if (ch == '\\' && i + 1 < length) {
            value.append(ch).append(json.charAt(i + 1));
            i += 2;
            continue;
          }
          if (ch == '"') {
            break;
          }
          value.append(ch);
          i++;
        }
        String cleaned = clean(value.toString());
        result.append('"').append(escapeJsonString(cleaned)).append('"');
        if (i < length) {
          i++;
        }
      } else {
        result.append(c);
        i++;
      }
    }

    return result.toString();
  }

  /**
   * 转义 JSON 字符串中的特殊字符。
   *
   * @param text 原始文本
   * @return 转义后的 JSON 字符串值
   */
  private static String escapeJsonString(String text) {
    StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }
}
