package com.njydsz.pmis.common.safe.xss;

import com.njydsz.pmis.common.util.string.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EscapeUtils - HTML 转义工具类 (基于标准化实现)
 * <p>
 * 参考 OWASP、阿里巴巴、Hutool 等互联网大厂最佳实践设计，提供全面的 HTML 转义和 XSS 防护能力。
 * </p>
 * <p>
 * 核心特性：
 * 1. 零第三方依赖 - 纯 JDK 实现，不依赖 Apache Commons Text 等外部库
 * 2. 高性能 - 使用预编译正则和 StringBuilder 优化
 * 3. 全面覆盖 - 支持 HTML4/HTML5 实体、JavaScript、CSS、URL 等多场景转义
 * 4. 灵活配置 - 支持自定义白名单、黑名单、转义级别
 * 5. 双向操作 - 同时支持转义和反转义
 * 6. 危险协议过滤 - 禁止 javascript:、data:、vbscript: 等危险 URL 协议
 * </p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class EscapeUtils {

    /**
     * 危险的 URL 协议列表，大小写不敏感匹配
     */
    private static final String[] DANGEROUS_PROTOCOLS = {
            "javascript:", "data:", "vbscript:"
    };

    private static final Pattern[] HTML_PATTERNS = {
            Pattern.compile("&"),
            Pattern.compile("<"),
            Pattern.compile(">"),
            Pattern.compile("\""),
            Pattern.compile("'")
    };

    private static final String[] HTML_ENTITIES = {
            "&amp;",
            "&lt;",
            "&gt;",
            "&quot;",
            "&#39;"
    };

    private static final Pattern P_ENTITY_DECIMAL = Pattern.compile("&#(\\d+);?");
    private static final Pattern P_ENTITY_HEX = Pattern.compile("&#x([0-9a-fA-F]+);?");
    private static final Pattern URL_PATTERN = Pattern.compile("[^a-zA-Z0-9\\-_.~!$'()*+,;=:@/?]");

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

    public static String escapeHtml4(String text) {
        return escape(text);
    }

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

    public static String unescape(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }

        String result = text;
        result = P_ENTITY_DECIMAL.matcher(result).replaceAll(m -> {
            try {
                int code = Integer.parseInt(m.group(1));
                return String.valueOf((char) code);
            } catch (NumberFormatException e) {
                return m.group(0);
            }
        });

        result = P_ENTITY_HEX.matcher(result).replaceAll(m -> {
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

    public static String unescapeHtml4(String text) {
        return unescape(text);
    }

    public static String unescapeHtml5(String text) {
        return unescape(text);
    }

    public static String clean(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        String filtered = filterDangerousProtocols(content);
        return new HTMLFilter().filter(filtered);
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

    public static String clean(String content, HTMLFilter.Builder builder) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return builder.build().filter(content);
    }

    public static String cleanRelaxed(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return HTMLFilter.Builder.relaxed().build().filter(content);
    }

    public static String cleanStandard(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return HTMLFilter.Builder.standard().build().filter(content);
    }

    public static String cleanStrict(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return HTMLFilter.Builder.strict().build().filter(content);
    }

    public static String cleanCustom(String content, HTMLFilter filter) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return filter.filter(content);
    }

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
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

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

    public static String stripTags(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        return content.replaceAll("<[^>]*>", "");
    }

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

    public static String stripAttributes(String content, String... allowedAttributes) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }

        if (allowedAttributes == null || allowedAttributes.length == 0) {
            return content.replaceAll("\\s+[a-zA-Z][a-zA-Z0-9_-]*\\s*=\\s*([\"'][^\"']*?[\"']|[^\\s>]+)", "");
        }

        Pattern tagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
        Matcher matcher = tagPattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String tagName = matcher.group(1);
            String attributes = matcher.group(2);

            StringBuilder filteredAttrs = new StringBuilder();
            Pattern attrPattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*([\"'][^\"']*?[\"']|[^\\s>]+)");
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

    public static boolean containsXSS(String content) {
        if (StringUtils.isEmpty(content)) {
            return false;
        }

        String lowerCase = content.toLowerCase();

        String[] xssPatterns = {
                "<script", "</script>",
                "javascript:", "vbscript:",
                "onload=", "onerror=", "onclick=", "onmouseover=", "onmouseout=",
                "onfocus=", "onblur=", "onchange=", "onsubmit=", "onreset=",
                "onmouseenter=", "onmouseleave=", "onkeydown=", "onkeyup=", "onkeypress=",
                "expression(", "url(",
                "<iframe", "</iframe>",
                "<object", "</object>",
                "<embed", "</embed>",
                "<applet", "</applet>",
                "<meta", "<link",
                "eval\\(", "alert\\(", "prompt\\(", "confirm\\(",
                "document\\.", "window\\.", "navigator\\.",
                "cookie", "localstorage", "sessionstorage"
        };

        for (String pattern : xssPatterns) {
            if (lowerCase.contains(pattern)) {
                return true;
            }
        }

        Pattern eventPattern = Pattern.compile("on\\w+\\s*=");
        if (eventPattern.matcher(lowerCase).find()) {
            return true;
        }

        return false;
    }

    public static String sanitize(String content) {
        return cleanRelaxed(content);
    }

    public static String encodeBase64(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

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
     * <p>使用流式解析方式递归清理 JSON 字符串值，不破坏 JSON 结构。
     * 仅处理 JSON 字符串值，其他类型保持不变。
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
