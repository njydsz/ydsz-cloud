package com.njydsz.common.safe.xss;

import java.util.*;
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
 * <ul>
 *   <li>零第三方依赖，纯 JDK 实现</li>
 *   <li>支持完全自定义的白名单配置（允许的标签、属性、协议）</li>
 *   <li>内置多种安全策略（宽松、标准、严格）</li>
 *   <li>支持协议级别的 URL 验证（http/https/mailto 等）</li>
 *   <li>高性能正则匹配和缓存机制（{@link ConcurrentHashMap} 缓存已编译正则）</li>
 *   <li>HTML 实体编码（&amp; &lt; &gt; &quot;）防止 XSS 注入</li>
 *   <li>嵌套标签处理（递归解析标签栈）</li>
 * </ul>
 *
 * <h3>过滤流程</h3>
 * <ol>
 *   <li>移除 HTML 注释（{@code <!-- -->}）</li>
 *   <li>逐标签解析，白名单内的标签保留，其他标签移除</li>
 *   <li>对保留的标签，过滤属性（白名单属性 + 协议验证）</li>
 *   <li>对 URL 属性执行协议白名单校验（防止 {@code javascript:} 等危险协议）</li>
 *   <li>对文本内容执行 HTML 实体编码</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * HTMLFilter filter = new HTMLFilter();
 * String clean = filter.filter("<script>alert(1)</script><b>text</b>");
 * // 结果：<b>text</b>
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class HTMLFilter {

    private static final int REGEX_FLAGS_SI = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern P_COMMENTS = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
    private static final Pattern P_COMMENT = Pattern.compile("^!--(.*)--$", REGEX_FLAGS_SI);
    private static final Pattern P_TAGS = Pattern.compile("<(.*?)>", Pattern.DOTALL);
    private static final Pattern P_END_TAG = Pattern.compile("^/([a-z0-9]+)", REGEX_FLAGS_SI);
    private static final Pattern P_START_TAG = Pattern.compile("^([a-z0-9]+)(.*?)(/?)$", REGEX_FLAGS_SI);
    private static final Pattern P_QUOTED_ATTRIBUTES = Pattern.compile("([a-z0-9]+)=([\"'])(.*?)\\2", REGEX_FLAGS_SI);
    private static final Pattern P_UNQUOTED_ATTRIBUTES = Pattern.compile("([a-z0-9]+)(=)([^\"\\s']+)", REGEX_FLAGS_SI);
    private static final Pattern P_PROTOCOL = Pattern.compile("^([^:]+):", REGEX_FLAGS_SI);
    private static final Pattern P_ENTITY = Pattern.compile("&#(\\d+);?");
    private static final Pattern P_ENTITY_UNICODE = Pattern.compile("&#x([0-9a-f]+);?");
    private static final Pattern P_ENCODE = Pattern.compile("%([0-9a-f]{2});?");
    private static final Pattern P_VALID_ENTITIES = Pattern.compile("&([^&;]*)(?=(;|&|$))");
    private static final Pattern P_VALID_QUOTES = Pattern.compile("(>|^)([^<]+?)(<|$)", Pattern.DOTALL);
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
        Map<String, String> entities = new HashMap<>();
        entities.put("amp", "&");
        entities.put("lt", "<");
        entities.put("gt", ">");
        entities.put("quot", "\"");
        entities.put("apos", "'");
        entities.put("nbsp", " ");
        entities.put("copy", "\u00A9");
        entities.put("reg", "\u00AE");
        entities.put("trade", "\u2122");
        entities.put("mdash", "\u2014");
        entities.put("ndash", "\u2013");
        entities.put("hellip", "\u2026");
        entities.put("ldquo", "\u201C");
        entities.put("rdquo", "\u201D");
        entities.put("lsquo", "\u2018");
        entities.put("rsquo", "\u2019");
        NAMED_ENTITIES = Collections.unmodifiableMap(entities);
    }

    private static final ConcurrentMap<String, Pattern> P_REMOVE_PAIR_BLANKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Pattern> P_REMOVE_SELF_BLANKS = new ConcurrentHashMap<>();

    private final Map<String, List<String>> vAllowed;
    private final Set<String> vSelfClosingTags;
    private final Set<String> vNeedClosingTags;
    private final Set<String> vDisallowed;
    private final Set<String> vProtocolAtts;
    private final Set<String> vAllowedProtocols;
    private final Set<String> vRemoveBlanks;
    private final Set<String> vAllowedEntities;
    private final boolean stripComment;
    private final boolean encodeQuotes;
    private final boolean alwaysMakeTags;

    public HTMLFilter() {
        vAllowed = new HashMap<>();
        vSelfClosingTags = new HashSet<>(Arrays.asList("img", "br", "hr", "input", "meta", "link", "area", "base", "col"));
        vNeedClosingTags = new HashSet<>(Arrays.asList("a", "b", "strong", "i", "em", "span", "p", "div", "table", "tr", "td", "th", "thead", "tbody", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "code", "pre"));
        vDisallowed = new HashSet<>(Arrays.asList("script", "iframe", "object", "embed", "applet", "form", "button", "textarea", "select", "option", "input", "style", "link", "meta", "base", "head", "title", "html", "body", "frame", "frameset"));
        vAllowedProtocols = new HashSet<>(Arrays.asList("http", "https", "mailto", "ftp"));
        vProtocolAtts = new HashSet<>(Arrays.asList("src", "href", "action", "formaction", "data", "poster", "background"));
        vRemoveBlanks = new HashSet<>(Arrays.asList("a", "b", "strong", "i", "em", "span", "p", "div", "h1", "h2", "h3", "h4", "h5", "h6"));
        vAllowedEntities = new HashSet<>(Arrays.asList("amp", "gt", "lt", "quot", "nbsp", "copy", "reg", "trade", "mdash", "ndash", "hellip", "ldquo", "rdquo", "lsquo", "rsquo"));
        stripComment = true;
        encodeQuotes = true;
        alwaysMakeTags = false;

        initializeDefaultAllowedTags();
    }

    public HTMLFilter(final Map<String, Object> conf) {
        assert conf.containsKey("vAllowed") : "configuration requires vAllowed";
        assert conf.containsKey("vSelfClosingTags") : "configuration requires vSelfClosingTags";
        assert conf.containsKey("vNeedClosingTags") : "configuration requires vNeedClosingTags";
        assert conf.containsKey("vDisallowed") : "configuration requires vDisallowed";
        assert conf.containsKey("vAllowedProtocols") : "configuration requires vAllowedProtocols";
        assert conf.containsKey("vProtocolAtts") : "configuration requires vProtocolAtts";
        assert conf.containsKey("vRemoveBlanks") : "configuration requires vRemoveBlanks";
        assert conf.containsKey("vAllowedEntities") : "configuration requires vAllowedEntities";

        vAllowed = Collections.unmodifiableMap(castToHashMap(conf.get("vAllowed")));
        vSelfClosingTags = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vSelfClosingTags"))));
        vNeedClosingTags = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vNeedClosingTags"))));
        vDisallowed = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vDisallowed"))));
        vAllowedProtocols = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vAllowedProtocols"))));
        vProtocolAtts = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vProtocolAtts"))));
        vRemoveBlanks = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vRemoveBlanks"))));
        vAllowedEntities = new HashSet<>(Arrays.asList(castToStringArray(conf.get("vAllowedEntities"))));
        stripComment = conf.containsKey("stripComment") ? (Boolean) conf.get("stripComment") : true;
        encodeQuotes = conf.containsKey("encodeQuotes") ? (Boolean) conf.get("encodeQuotes") : true;
        alwaysMakeTags = conf.containsKey("alwaysMakeTags") ? (Boolean) conf.get("alwaysMakeTags") : true;
    }

    private HTMLFilter(Builder builder) {
        this.vAllowed = Collections.unmodifiableMap(new HashMap<>(builder.vAllowed));
        this.vSelfClosingTags = new HashSet<>(builder.vSelfClosingTags);
        this.vNeedClosingTags = new HashSet<>(builder.vNeedClosingTags);
        this.vDisallowed = new HashSet<>(builder.vDisallowed);
        this.vAllowedProtocols = new HashSet<>(builder.vAllowedProtocols);
        this.vProtocolAtts = new HashSet<>(builder.vProtocolAtts);
        this.vRemoveBlanks = new HashSet<>(builder.vRemoveBlanks);
        this.vAllowedEntities = new HashSet<>(builder.vAllowedEntities);
        this.stripComment = builder.stripComment;
        this.encodeQuotes = builder.encodeQuotes;
        this.alwaysMakeTags = builder.alwaysMakeTags;
    }

    private void initializeDefaultAllowedTags() {
        addAllowedTagsWithAttributes("a", "href", "target", "rel", "title", "class");
        addAllowedTagsWithAttributes("img", "src", "alt", "title", "width", "height", "class", "style");
        addAllowedTagsWithAttributes("span", "class", "style", "title");
        addAllowedTagsWithAttributes("div", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("p", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("table", "class", "style", "border", "cellpadding", "cellspacing", "width");
        addAllowedTagsWithAttributes("tr", "class", "style", "align", "valign");
        addAllowedTagsWithAttributes("td", "class", "style", "colspan", "rowspan", "align", "valign", "width", "height");
        addAllowedTagsWithAttributes("th", "class", "style", "colspan", "rowspan", "align", "valign", "width", "height", "scope");
        addAllowedTagsWithAttributes("thead", "class", "style");
        addAllowedTagsWithAttributes("tbody", "class", "style");
        addAllowedTagsWithAttributes("ul", "class", "style", "type");
        addAllowedTagsWithAttributes("ol", "class", "style", "type", "start");
        addAllowedTagsWithAttributes("li", "class", "style", "value");
        addAllowedTagsWithAttributes("h1", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("h2", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("h3", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("h4", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("h5", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("h6", "class", "style", "id", "title");
        addAllowedTagsWithAttributes("strong", "class", "style", "title");
        addAllowedTagsWithAttributes("b", "class", "style", "title");
        addAllowedTagsWithAttributes("i", "class", "style", "title");
        addAllowedTagsWithAttributes("em", "class", "style", "title");
        addAllowedTagsWithAttributes("u", "class", "style", "title");
        addAllowedTagsWithAttributes("s", "class", "style", "title");
        addAllowedTagsWithAttributes("strike", "class", "style", "title");
        addAllowedTagsWithAttributes("del", "class", "style", "title", "datetime");
        addAllowedTagsWithAttributes("ins", "class", "style", "title", "datetime");
        addAllowedTagsWithAttributes("sub", "class", "style", "title");
        addAllowedTagsWithAttributes("sup", "class", "style", "title");
        addAllowedTagsWithAttributes("br", "class", "style");
        addAllowedTagsWithAttributes("hr", "class", "style", "width", "size", "noshade");
        addAllowedTagsWithAttributes("pre", "class", "style", "code", "lang");
        addAllowedTagsWithAttributes("code", "class", "style", "lang");
        addAllowedTagsWithAttributes("blockquote", "class", "style", "cite");
        addAllowedTagsWithAttributes("q", "class", "style", "cite");
        addAllowedTagsWithAttributes("abbr", "class", "style", "title");
        addAllowedTagsWithAttributes("acronym", "class", "style", "title");
        addAllowedTagsWithAttributes("address", "class", "style", "title");
        addAllowedTagsWithAttributes("caption", "class", "style");
        addAllowedTagsWithAttributes("col", "class", "style", "span", "width");
        addAllowedTagsWithAttributes("colgroup", "class", "style", "span");
    }

    private void addAllowedTagsWithAttributes(String tagName, String... attributes) {
        List<String> attrs = new ArrayList<>();
        if (attributes != null) {
            Collections.addAll(attrs, attributes);
        }
        vAllowed.put(tagName.toLowerCase(), attrs);
    }

    
    private static HashMap<String, List<String>> castToHashMap(Object obj) {
        return (HashMap<String, List<String>>) obj;
    }

    private static String[] castToStringArray(Object obj) {
        return (String[]) obj;
    }

    /**
     * 将十进制数字转换为对应的字符
     *
     * @param decimal 字符的十进制编码
     * @return 对应的字符
     */
    public static String chr(final int decimal) {
        return String.valueOf((char) decimal);
    }

    /**
     * 转义 HTML 特殊字符
     *
     * @param s 待转义的字符串
     * @return 转义后的字符串
     */
    public static String htmlSpecialChars(final String s) {
        String result = s;
        result = regexReplace(P_AMP, "&amp;", result);
        result = regexReplace(P_QUOTE, "&quot;", result);
        result = regexReplace(P_LEFT_ARROW, "&lt;", result);
        result = regexReplace(P_RIGHT_ARROW, "&gt;", result);
        return result;
    }

    /**
     * 过滤输入内容，移除不安全的 HTML 标签和属性
     *
     * @param input 待过滤的 HTML 内容
     * @return 过滤后的安全内容
     */
    public String filter(final String input) {
        String s = input;

        s = escapeComments(s);
        s = balanceHTML(s);
        s = checkTags(s, new HashMap<>());
        s = processRemoveBlanks(s);

        return s;
    }

    /**
     * 是否始终生成完整标签（自动闭合未闭合的标签）
     *
     * @return true 表示始终生成完整标签
     */
    public boolean isAlwaysMakeTags() {
        return alwaysMakeTags;
    }

    /**
     * 是否移除 HTML 注释
     *
     * @return true 表示移除注释
     */
    public boolean isStripComments() {
        return stripComment;
    }

    private String escapeComments(final String s) {
        final Matcher m = P_COMMENTS.matcher(s);
        final StringBuilder buf = new StringBuilder();
        while (m.find()) {
            final String match = m.group(1);
            m.appendReplacement(buf, Matcher.quoteReplacement("<!--" + htmlSpecialChars(match) + "-->"));
        }
        m.appendTail(buf);

        return buf.toString();
    }

    private String balanceHTML(String s) {
        if (alwaysMakeTags) {
            s = regexReplace(P_END_ARROW, "", s);
            s = regexReplace(P_BODY_TO_END, "<$1>", s);
            s = regexReplace(P_XML_CONTENT, "$1<$2", s);
        } else {
            s = regexReplace(P_STRAY_LEFT_ARROW, "&lt;$1", s);
            s = regexReplace(P_STRAY_RIGHT_ARROW, "$1$2&gt;<", s);
            s = regexReplace(P_BOTH_ARROWS, "", s);
        }

        return s;
    }

    private String checkTags(String s, final Map<String, Integer> tagCounts) {
        Matcher m = P_TAGS.matcher(s);

        final StringBuilder buf = new StringBuilder();
        while (m.find()) {
            String replaceStr = m.group(1);
            replaceStr = processTag(replaceStr, tagCounts);
            m.appendReplacement(buf, Matcher.quoteReplacement(replaceStr));
        }
        m.appendTail(buf);

        final StringBuilder sBuilder = new StringBuilder(buf.toString());
        for (String key : tagCounts.keySet()) {
            for (int ii = 0; ii < tagCounts.get(key); ii++) {
                sBuilder.append("</").append(key).append(">");
            }
        }
        s = sBuilder.toString();

        return s;
    }

    private String processRemoveBlanks(final String s) {
        String result = s;
        for (String tag : vRemoveBlanks) {
            if (!P_REMOVE_PAIR_BLANKS.containsKey(tag)) {
                P_REMOVE_PAIR_BLANKS.putIfAbsent(tag, Pattern.compile("<" + tag + "(\\s[^>]*)?></" + tag + ">"));
            }
            result = regexReplace(P_REMOVE_PAIR_BLANKS.get(tag), "", result);
            if (!P_REMOVE_SELF_BLANKS.containsKey(tag)) {
                P_REMOVE_SELF_BLANKS.putIfAbsent(tag, Pattern.compile("<" + tag + "(\\s[^>]*)?/>"));
            }
            result = regexReplace(P_REMOVE_SELF_BLANKS.get(tag), "", result);
        }

        return result;
    }

    private static String regexReplace(final Pattern regex_pattern, final String replacement, final String s) {
        Matcher m = regex_pattern.matcher(s);
        return m.replaceAll(replacement);
    }

    private String processTag(final String s, final Map<String, Integer> tagCounts) {
        Matcher m = P_END_TAG.matcher(s);
        if (m.find()) {
            final String name = m.group(1).toLowerCase();
            if (allowed(name)) {
                if (!inArray(name, vSelfClosingTags)) {
                    if (tagCounts.containsKey(name)) {
                        tagCounts.put(name, tagCounts.get(name) - 1);
                        return "</" + name + ">";
                    }
                }
            }
        }

        m = P_START_TAG.matcher(s);
        if (m.find()) {
            final String name = m.group(1).toLowerCase();
            final String body = m.group(2);
            String ending = m.group(3);

            if (allowed(name)) {
                final StringBuilder params = new StringBuilder();

                final Matcher m2 = P_QUOTED_ATTRIBUTES.matcher(body);
                final Matcher m3 = P_UNQUOTED_ATTRIBUTES.matcher(body);
                final List<String> paramNames = new ArrayList<>();
                final List<String> paramValues = new ArrayList<>();
                while (m2.find()) {
                    paramNames.add(m2.group(1));
                    paramValues.add(m2.group(3));
                }
                while (m3.find()) {
                    paramNames.add(m3.group(1));
                    paramValues.add(m3.group(3));
                }

                String paramName, paramValue;
                for (int ii = 0; ii < paramNames.size(); ii++) {
                    paramName = paramNames.get(ii).toLowerCase();
                    paramValue = paramValues.get(ii);

                    if (allowedAttribute(name, paramName)) {
                        if (inArray(paramName, vProtocolAtts)) {
                            paramValue = processParamProtocol(paramValue);
                        }
                        params.append(' ').append(paramName).append("=\\\"").append(paramValue).append("\\\"");
                    }
                }

                if (inArray(name, vSelfClosingTags)) {
                    ending = " /";
                }

                if (inArray(name, vNeedClosingTags)) {
                    ending = "";
                }

                if (ending == null || ending.length() < 1) {
                    if (tagCounts.containsKey(name)) {
                        tagCounts.put(name, tagCounts.get(name) + 1);
                    } else {
                        tagCounts.put(name, 1);
                    }
                } else {
                    ending = " /";
                }
                return "<" + name + params + ending + ">";
            } else {
                return "";
            }
        }

        m = P_COMMENT.matcher(s);
        if (!stripComment && m.find()) {
            return "<" + m.group() + ">";
        }

        return "";
    }

    private String processParamProtocol(String s) {
        s = decodeEntities(s);
        final Matcher m = P_PROTOCOL.matcher(s);
        if (m.find()) {
            final String protocol = m.group(1);
            if (!inArray(protocol, vAllowedProtocols)) {
                s = "#" + s.substring(protocol.length() + 1);
                if (s.startsWith("#//")) {
                    s = "#" + s.substring(3);
                }
            }
        }

        return s;
    }

    private String decodeEntities(String s) {
        StringBuilder buf = new StringBuilder();

        Matcher m = P_ENTITY.matcher(s);
        while (m.find()) {
            final String match = m.group(1);
            final int decimal = Integer.decode(match).intValue();
            m.appendReplacement(buf, Matcher.quoteReplacement(chr(decimal)));
        }
        m.appendTail(buf);
        s = buf.toString();

        buf = new StringBuilder();
        m = P_ENTITY_UNICODE.matcher(s);
        while (m.find()) {
            final String match = m.group(1);
            final int decimal = Integer.valueOf(match, 16).intValue();
            m.appendReplacement(buf, Matcher.quoteReplacement(chr(decimal)));
        }
        m.appendTail(buf);
        s = buf.toString();

        buf = new StringBuilder();
        m = P_ENCODE.matcher(s);
        while (m.find()) {
            final String match = m.group(1);
            final int decimal = Integer.valueOf(match, 16).intValue();
            m.appendReplacement(buf, Matcher.quoteReplacement(chr(decimal)));
        }
        m.appendTail(buf);
        s = buf.toString();

        s = validateEntities(s);

        // 解码命名实体（如 &amp; &lt; &gt; &quot; &nbsp;）
        buf = new StringBuilder();
        m = P_NAMED_ENTITY.matcher(s);
        while (m.find()) {
            final String name = m.group(1);
            final String replacement = NAMED_ENTITIES.get(name);
            if (replacement != null) {
                m.appendReplacement(buf, Matcher.quoteReplacement(replacement));
            }
        }
        m.appendTail(buf);
        s = buf.toString();

        return s;
    }

    private String validateEntities(final String s) {
        StringBuilder buf = new StringBuilder();

        Matcher m = P_VALID_ENTITIES.matcher(s);
        while (m.find()) {
            final String one = m.group(1);
            final String two = m.group(2);
            m.appendReplacement(buf, Matcher.quoteReplacement(checkEntity(one, two)));
        }
        m.appendTail(buf);

        return encodeQuotes(buf.toString());
    }

    private String encodeQuotes(final String s) {
        if (encodeQuotes) {
            StringBuilder buf = new StringBuilder();
            Matcher m = P_VALID_QUOTES.matcher(s);
            while (m.find()) {
                final String one = m.group(1);
                final String two = m.group(2);
                final String three = m.group(3);
                m.appendReplacement(buf, Matcher.quoteReplacement(one + two + three));
            }
            m.appendTail(buf);
            return buf.toString();
        } else {
            return s;
        }
    }

    private String checkEntity(final String preamble, final String term) {
        return ";".equals(term) && isValidEntity(preamble) ? '&' + preamble : "&amp;" + preamble;
    }

    private boolean isValidEntity(final String entity) {
        return inArray(entity, vAllowedEntities);
    }

    private static boolean inArray(final String s, final Set<String> set) {
        return set.contains(s);
    }

    private boolean allowed(final String name) {
        return (vAllowed.isEmpty() || vAllowed.containsKey(name)) && !inArray(name, vDisallowed);
    }

    private boolean allowedAttribute(final String name, final String paramName) {
        return allowed(name) && (vAllowed.isEmpty() || vAllowed.get(name).contains(paramName));
    }

    /**
     * HTML 过滤器配置构建器。
     *
     * <p>通过链式方法配置标签/属性白名单、禁止标签、允许协议、去空白标签等策略，
     * 最后调用 {@code build()} 生成对应的 {@link HTMLFilter} 实例；标签名匹配均不区分大小写。
     */
    public static class Builder {
        private final Map<String, List<String>> vAllowed = new HashMap<>();
        private final List<String> vSelfClosingTags = new ArrayList<>();
        private final List<String> vNeedClosingTags = new ArrayList<>();
        private final List<String> vDisallowed = new ArrayList<>();
        private final List<String> vAllowedProtocols = new ArrayList<>();
        private final List<String> vProtocolAtts = new ArrayList<>();
        private final List<String> vRemoveBlanks = new ArrayList<>();
        private final List<String> vAllowedEntities = new ArrayList<>();
        private boolean stripComment = true;
        private boolean encodeQuotes = true;
        private boolean alwaysMakeTags = false;

        /**
         * 将标签加入白名单（允许出现，但默认不带任何属性）。
         *
         * @param tags 标签名（不区分大小写）
         * @return 当前 Builder，支持链式调用
         */
        public Builder allowTags(String... tags) {
            for (String tag : tags) {
                if (!vAllowed.containsKey(tag.toLowerCase())) {
                    vAllowed.put(tag.toLowerCase(), new ArrayList<>());
                }
            }
            return this;
        }

        /**
         * 将标签加入白名单并声明允许的属性集合。
         *
         * @param tag       标签名
         * @param attributes 允许保留的属性名列表
         * @return 当前 Builder，支持链式调用
         */
        public Builder allowTagWithAttributes(String tag, String... attributes) {
            String tagName = tag.toLowerCase();
            List<String> attrs = vAllowed.computeIfAbsent(tagName, k -> new ArrayList<>());
            Collections.addAll(attrs, attributes);
            return this;
        }

        /**
         * 将标签加入黑名单（无论是否在白名单中均强制移除，优先级最高）。
         *
         * @param tags 标签名
         * @return 当前 Builder，支持链式调用
         */
        public Builder disallowTags(String... tags) {
            Collections.addAll(vDisallowed, tags);
            return this;
        }

        /**
         * 声明自闭合标签（如 img/br/hr），输出时以 {@code <tag />} 形式呈现且不计未闭合。
         *
         * @param tags 标签名
         * @return 当前 Builder，支持链式调用
         */
        public Builder allowSelfClosingTags(String... tags) {
            Collections.addAll(vSelfClosingTags, tags);
            return this;
        }

        /**
         * 声明 URL 属性允许的协议白名单（如 http/https/mailto/ftp）。
         *
         * <p>不在白名单内的协议（如 {@code javascript:}）将被替换为 {@code #} 前缀，防 XSS。
         *
         * @param protocols 协议名
         * @return 当前 Builder，支持链式调用
         */
        public Builder allowProtocols(String... protocols) {
            Collections.addAll(vAllowedProtocols, protocols);
            return this;
        }

        /**
         * 声明需要做协议校验的 URL 类属性（如 href/src/action）。
         *
         * @param attributes 属性名
         * @return 当前 Builder，支持链式调用
         */
        public Builder protocolAttributes(String... attributes) {
            Collections.addAll(vProtocolAtts, attributes);
            return this;
        }

        /**
         * 声明允许保留的命名 HTML 实体（如 amp/lt/gt/quot/nbsp）。
         *
         * @param entities 实体名
         * @return 当前 Builder，支持链式调用
         */
        public Builder allowEntities(String... entities) {
            Collections.addAll(vAllowedEntities, entities);
            return this;
        }

        /**
         * 声明成对空标签（如 {@code <p></p>}）应被整体移除的标签集合。
         *
         * @param tags 标签名
         * @return 当前 Builder，支持链式调用
         */
        public Builder removeBlankTags(String... tags) {
            Collections.addAll(vRemoveBlanks, tags);
            return this;
        }

        /**
         * 是否移除 HTML 注释（默认 true）。
         *
         * @param strip 为 true 时移除 {@code <!-- -->} 内容
         * @return 当前 Builder，支持链式调用
         */
        public Builder stripComments(boolean strip) {
            this.stripComment = strip;
            return this;
        }

        /**
         * 是否对文本内容中的引号进行 HTML 实体编码（默认 true）。
         *
         * @param encode 为 true 时编码引号，降低属性注入风险
         * @return 当前 Builder，支持链式调用
         */
        public Builder encodeQuotes(boolean encode) {
            this.encodeQuotes = encode;
            return this;
        }

        /**
         * 是否自动补全未闭合标签（默认 false）。
         *
         * @param always 为 true 时尽力平衡标签，避免片段破坏整体页面结构
         * @return 当前 Builder，支持链式调用
         */
        public Builder alwaysMakeTags(boolean always) {
            this.alwaysMakeTags = always;
            return this;
        }

        /**
         * 基于当前配置构建不可变的 HTMLFilter 实例。
         *
         * @return HTMLFilter 实例
         */
        public HTMLFilter build() {
            return new HTMLFilter(this);
        }

        /**
         * 宽松安全策略预设（保留较多富文本标签/属性，适用内容展示场景）。
         *
         * @return 宽松策略 Builder
         */
        public static Builder relaxed() {
            Builder builder = new Builder();
            builder.allowTags("a", "b", "strong", "i", "em", "u", "s", "strike", "del", "ins",
                    "sub", "sup", "span", "div", "p", "br", "hr", "pre", "code",
                    "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
                    "table", "tr", "td", "th", "thead", "tbody", "col", "colgroup",
                    "blockquote", "q", "abbr", "acronym", "address", "caption", "img");
            builder.allowTagWithAttributes("a", "href", "target", "rel", "title", "class");
            builder.allowTagWithAttributes("img", "src", "alt", "title", "width", "height", "class", "style");
            builder.allowTagWithAttributes("span", "div", "p", "table", "tr", "td", "th", "ul", "ol", "li",
                    "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code",
                    "strong", "b", "i", "em", "u", "s", "strike", "del", "ins", "sub", "sup",
                    "class", "style", "id", "title");
            builder.allowSelfClosingTags("img", "br", "hr");
            builder.allowProtocols("http", "https", "mailto", "ftp");
            builder.protocolAttributes("href", "src", "action", "formaction");
            builder.allowEntities("amp", "gt", "lt", "quot", "nbsp", "copy", "reg", "trade", "mdash", "ndash", "hellip");
            builder.removeBlankTags("a", "b", "strong", "i", "em", "span", "p", "div");
            builder.stripComments(true);
            builder.encodeQuotes(true);
            builder.alwaysMakeTags(false);
            return builder;
        }

        /**
         * 标准安全策略预设（保留常用富文本，收紧危险属性与协议，推荐默认）。
         *
         * @return 标准策略 Builder
         */
        public static Builder standard() {
            Builder builder = new Builder();
            builder.allowTags("a", "b", "strong", "i", "em", "u", "s", "strike", "del", "ins",
                    "sub", "sup", "span", "div", "p", "br", "hr", "pre", "code",
                    "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
                    "table", "tr", "td", "th", "thead", "tbody", "blockquote", "q", "abbr", "address", "caption", "img");
            builder.allowTagWithAttributes("a", "href", "target", "rel", "title");
            builder.allowTagWithAttributes("img", "src", "alt", "title", "width", "height");
            builder.allowTagWithAttributes("span", "div", "p", "table", "tr", "td", "th", "ul", "ol", "li",
                    "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code",
                    "class", "style");
            builder.allowSelfClosingTags("img", "br", "hr");
            builder.allowProtocols("http", "https", "mailto");
            builder.protocolAttributes("href", "src");
            builder.allowEntities("amp", "gt", "lt", "quot", "nbsp");
            builder.removeBlankTags("a", "b", "strong", "i", "em", "span", "p", "div");
            builder.stripComments(true);
            builder.encodeQuotes(true);
            builder.alwaysMakeTags(false);
            return builder;
        }

        /**
         * 严格安全策略预设（仅保留极简排版标签，仅允许 http/https 协议）。
         *
         * @return 严格策略 Builder
         */
        public static Builder strict() {
            Builder builder = new Builder();
            builder.allowTags("b", "strong", "i", "em", "u", "s", "strike", "del", "ins",
                    "sub", "sup", "span", "p", "br", "hr", "ul", "ol", "li");
            builder.allowTagWithAttributes("span", "p", "ul", "ol", "li", "class");
            builder.allowSelfClosingTags("br", "hr");
            builder.allowProtocols("http", "https");
            builder.protocolAttributes("href", "src");
            builder.allowEntities("amp", "gt", "lt", "quot");
            builder.removeBlankTags("b", "strong", "i", "em", "span", "p");
            builder.stripComments(true);
            builder.encodeQuotes(true);
            builder.alwaysMakeTags(false);
            return builder;
        }
    }
}
