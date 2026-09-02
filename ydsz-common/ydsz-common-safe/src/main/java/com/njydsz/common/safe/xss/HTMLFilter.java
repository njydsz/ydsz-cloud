package com.njydsz.common.safe.xss;.xss
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

  static {
    Map<String, String> entities = new HashMap<>(16);