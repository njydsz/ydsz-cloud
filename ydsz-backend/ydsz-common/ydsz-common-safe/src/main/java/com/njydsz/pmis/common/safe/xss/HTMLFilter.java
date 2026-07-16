package com.njydsz.common.safe.xss;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 过滤器，用于去除 XSS 漏洞隐患。
 * <p>
 * 基于 OWASP 最佳实践设计，支持白名单机制、协议验证、属性过滤等功能。
 * 相比互联网大厂工具类的优势：
 * 1. 零第三方依赖，纯 JDK 实现
 * 2. 支持完全自定义的白名单配置
 * 3. 内置多种安全策略（宽松、标准、严格）
 * 4. 支持协议级别的URL 验证
 * 5. 高性能正则匹配和缓存机制
 * </p>
 *
 * @since 1.0.0
 * 
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

    public static String chr(final int decimal) {
        return String.valueOf((char) decimal);
    }

    public static String htmlSpecialChars(final String s) {
        String result = s;
        result = regexReplace(P_AMP, "&amp;", result);
        result = regexReplace(P_QUOTE, "&quot;", result);
        result = regexReplace(P_LEFT_ARROW, "&lt;", result);
        result = regexReplace(P_RIGHT_ARROW, "&gt;", result);
        return result;
    }

    public String filter(final String input) {
        String s = input;

        s = escapeComments(s);
        s = balanceHTML(s);
        s = checkTags(s, new HashMap<>());
        s = processRemoveBlanks(s);

        return s;
    }

    public boolean isAlwaysMakeTags() {
        return alwaysMakeTags;
    }

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

        public Builder allowTags(String... tags) {
            for (String tag : tags) {
                if (!vAllowed.containsKey(tag.toLowerCase())) {
                    vAllowed.put(tag.toLowerCase(), new ArrayList<>());
                }
            }
            return this;
        }

        public Builder allowTagWithAttributes(String tag, String... attributes) {
            String tagName = tag.toLowerCase();
            List<String> attrs = vAllowed.computeIfAbsent(tagName, k -> new ArrayList<>());
            Collections.addAll(attrs, attributes);
            return this;
        }

        public Builder disallowTags(String... tags) {
            Collections.addAll(vDisallowed, tags);
            return this;
        }

        public Builder allowSelfClosingTags(String... tags) {
            Collections.addAll(vSelfClosingTags, tags);
            return this;
        }

        public Builder allowProtocols(String... protocols) {
            Collections.addAll(vAllowedProtocols, protocols);
            return this;
        }

        public Builder protocolAttributes(String... attributes) {
            Collections.addAll(vProtocolAtts, attributes);
            return this;
        }

        public Builder allowEntities(String... entities) {
            Collections.addAll(vAllowedEntities, entities);
            return this;
        }

        public Builder removeBlankTags(String... tags) {
            Collections.addAll(vRemoveBlanks, tags);
            return this;
        }

        public Builder stripComments(boolean strip) {
            this.stripComment = strip;
            return this;
        }

        public Builder encodeQuotes(boolean encode) {
            this.encodeQuotes = encode;
            return this;
        }

        public Builder alwaysMakeTags(boolean always) {
            this.alwaysMakeTags = always;
            return this;
        }

        public HTMLFilter build() {
            return new HTMLFilter(this);
        }

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
