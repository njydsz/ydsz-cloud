package com.njydsz.pmis.common.util.url;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.util.AntPathMatcher;

import com.njydsz.pmis.common.util.collection.CollectionUtils;
import com.njydsz.pmis.common.util.string.StringUtils;

/**
 * URL 路径工具类
 *
 * <p>提供 URL 路径匹配（Ant 风格）、路径规范化、路径变量提取、查询参数解析等能力。
 *
 * <p>URL 协议检测、编码解码请使用 {@link com.njydsz.pmis.common.util.http.UrlUtils}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class UrlPathUtils {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^/]+?)\\}");

    private static final char SLASH = '/';

    private UrlPathUtils() {
        throw new UnsupportedOperationException("UrlPathUtils is a utility class and cannot be instantiated");
    }

    /**
     * 检查请求路径是否匹配给定的模式
     */
    public static boolean match(String pattern, String requestPath) {
        if (StringUtils.isBlank(pattern) || StringUtils.isBlank(requestPath)) {
            return false;
        }
        return PATH_MATCHER.match(pattern, requestPath);
    }

    /**
     * 检查请求路径是否匹配给定的模式集合中的任意一个
     */
    public static boolean matchAny(Collection<String> patterns, String requestPath) {
        if (CollectionUtils.isEmpty(patterns) || StringUtils.isBlank(requestPath)) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> match(pattern, requestPath));
    }

    /**
     * 检查请求路径是否匹配给定的模式集合中的所有模式
     */
    public static boolean matchAll(Collection<String> patterns, String requestPath) {
        if (CollectionUtils.isEmpty(patterns) || StringUtils.isBlank(requestPath)) {
            return false;
        }
        return patterns.stream().allMatch(pattern -> match(pattern, requestPath));
    }

    /**
     * 判断路径是否在忽略列表中
     */
    public static boolean isIgnoreUrl(Collection<String> ignoreUrls, String requestPath) {
        return matchAny(ignoreUrls, requestPath);
    }

    public static boolean isIgnoreUrl(List<String> ignoreUrls, String requestPath) {
        return isIgnoreUrl((Collection<String>) ignoreUrls, requestPath);
    }

    public static boolean isIgnoreUrl(Set<String> ignoreUrls, String requestPath) {
        return isIgnoreUrl((Collection<String>) ignoreUrls, requestPath);
    }

    public static boolean isIgnoreUrl(String[] ignoreUrls, String requestPath) {
        if (ignoreUrls == null || ignoreUrls.length == 0) {
            return false;
        }
        return isIgnoreUrl(Arrays.asList(ignoreUrls), requestPath);
    }

    /**
     * 规范化路径：移除多余的斜杠
     */
    public static String normalize(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        String normalized = path.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 移除路径开头的斜杠
     */
    public static String removeLeadingSlash(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * 移除路径末尾的斜杠
     */
    public static String removeTrailingSlash(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * 确保路径以斜杠开头
     */
    public static String ensureLeadingSlash(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 确保路径以斜杠结尾
     */
    public static String ensureTrailingSlash(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * 拼接多个路径片段
     */
    public static String join(String... paths) {
        if (paths == null || paths.length == 0) {
            return "";
        }
        return Arrays.stream(paths)
                .filter(StringUtils::isNotBlank)
                .map(UrlPathUtils::removeLeadingSlash)
                .map(UrlPathUtils::removeTrailingSlash)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("/", "/", ""));
    }

    /**
     * 拼接两个路径
     */
    public static String combine(String path1, String path2) {
        if (StringUtils.isBlank(path1)) {
            return normalize(path2);
        }
        if (StringUtils.isBlank(path2)) {
            return normalize(path1);
        }
        return PATH_MATCHER.combine(path1, path2);
    }

    /**
     * 解析路径，提取路径变量
     */
    public static Map<String, String> extractUriTemplateVariables(String pattern, String requestPath) {
        if (StringUtils.isBlank(pattern) || StringUtils.isBlank(requestPath)) {
            return Collections.emptyMap();
        }
        if (!match(pattern, requestPath)) {
            return Collections.emptyMap();
        }
        return PATH_MATCHER.extractUriTemplateVariables(pattern, requestPath);
    }

    /**
     * 提取通配符之前的路径部分
     */
    public static String getPathBeforeWildcard(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return pattern;
        }
        int wildcardIndex = pattern.indexOf("**");
        if (wildcardIndex == -1) {
            wildcardIndex = pattern.indexOf("*");
        }
        if (wildcardIndex == -1) {
            return pattern;
        }
        String beforeWildcard = pattern.substring(0, wildcardIndex);
        return removeTrailingSlash(beforeWildcard);
    }

    /**
     * 提取通配符之后的路径部分
     */
    public static String getPathAfterWildcard(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return pattern;
        }
        int wildcardIndex = pattern.indexOf("**");
        if (wildcardIndex == -1) {
            wildcardIndex = pattern.indexOf("*");
        }
        if (wildcardIndex == -1) {
            return pattern;
        }
        int afterIndex = wildcardIndex + (pattern.startsWith("**", wildcardIndex) ? 2 : 1);
        if (afterIndex >= pattern.length()) {
            return "";
        }
        return ensureLeadingSlash(pattern.substring(afterIndex));
    }

    /**
     * 从路径中提取不包含查询参数的部分
     */
    public static String extractPath(String fullPath) {
        if (StringUtils.isBlank(fullPath)) {
            return fullPath;
        }
        int queryIndex = fullPath.indexOf("?");
        return queryIndex == -1 ? fullPath : fullPath.substring(0, queryIndex);
    }

    /**
     * 从路径中提取查询参数字符串
     */
    public static String extractQuery(String fullPath) {
        if (StringUtils.isBlank(fullPath)) {
            return fullPath;
        }
        int queryIndex = fullPath.indexOf("?");
        return queryIndex == -1 ? "" : fullPath.substring(queryIndex + 1);
    }

    /**
     * 解析查询参数字符串为 Map
     */
    public static Map<String, String> parseParams(String queryString) {
        if (StringUtils.isBlank(queryString)) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new LinkedHashMap<>();
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (StringUtils.isBlank(pair)) {
                continue;
            }
            int idx = pair.indexOf("=");
            String key = idx > 0 ?
                    URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) :
                    pair;
            String value = idx > 0 && idx < pair.length() - 1 ?
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) :
                    "";
            params.put(key, value);
        }
        return params;
    }

    /**
     * 从完整路径中解析出所有参数
     */
    public static Map<String, String> parsePath(String fullPath) {
        String query = extractQuery(fullPath);
        return parseParams(query);
    }

    /**
     * 编码 URL 参数值
     */
    public static String encodeParam(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 解码 URL 参数值
     */
    public static String decodeParam(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 比较两个路径是否相等（规范化后比较）
     */
    public static boolean equals(String path1, String path2) {
        if (path1 == path2) {
            return true;
        }
        if (path1 == null || path2 == null) {
            return false;
        }
        return normalize(path1).equals(normalize(path2));
    }

    /**
     * 比较两个路径是否相等（忽略大小写）
     */
    public static boolean equalsIgnoreCase(String path1, String path2) {
        if (path1 == path2) {
            return true;
        }
        if (path1 == null || path2 == null) {
            return false;
        }
        return normalize(path1).equalsIgnoreCase(normalize(path2));
    }

    /**
     * 比较两个路径是否相等（忽略首尾斜杠）
     */
    public static boolean equalsIgnoreSlash(String path1, String path2) {
        if (path1 == path2) {
            return true;
        }
        if (path1 == null || path2 == null) {
            return false;
        }
        return removeLeadingSlash(removeTrailingSlash(path1))
                .equals(removeLeadingSlash(removeTrailingSlash(path2)));
    }

    /**
     * 判断路径是否为根路径
     */
    public static boolean isRootPath(String path) {
        return "/".equals(normalize(path));
    }

    /**
     * 判断路径是否以指定前缀开头
     */
    public static boolean startsWith(String path, String prefix) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(prefix)) {
            return false;
        }
        return normalize(path).startsWith(normalize(prefix));
    }

    /**
     * 判断路径是否以指定后缀结尾
     */
    public static boolean endsWith(String path, String suffix) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(suffix)) {
            return false;
        }
        return normalize(path).endsWith(normalize(suffix));
    }

    /**
     * 判断路径是否包含通配符
     */
    public static boolean hasWildcard(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        return path.contains("*") || path.contains("?") || path.contains("{");
    }

    /**
     * 判断路径是否是 RESTful 风格（包含路径变量）
     */
    public static boolean isRestful(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        return PATH_VARIABLE_PATTERN.matcher(path).find();
    }

    /**
     * 将 Ant 风格路径转换为正则表达式
     */
    public static String antToRegex(String antPath) {
        if (StringUtils.isBlank(antPath)) {
            return antPath;
        }
        String regex = antPath
                .replace(".", "\\.")
                .replace("*", "[^/]*")
                .replace("?", ".")
                .replaceAll("\\{[^/]+?\\}", "([^/]+)")
                .replaceAll("\\/\\[\\^/\\]\\*", "(/[^/]*)*")
                .replaceFirst("\\^\\[/\\^/\\]\\*", "^");
        return "^" + regex + "$";
    }

    /**
     * 获取路径的层级深度
     */
    public static int getDepth(String path) {
        if (StringUtils.isBlank(path)) {
            return 0;
        }
        String normalized = normalize(path);
        if ("/".equals(normalized)) {
            return 0;
        }
        return (int) normalized.chars().filter(c -> c == SLASH).count();
    }

    /**
     * 获取路径的最后一段（文件名或资源名）
     */
    public static String getLastName(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        String normalized = removeTrailingSlash(path);
        int lastSlash = normalized.lastIndexOf("/");
        return lastSlash == -1 ? normalized : normalized.substring(lastSlash + 1);
    }

    /**
     * 获取路径的父路径
     */
    public static String getParent(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        String normalized = normalize(path);
        if ("/".equals(normalized)) {
            return null;
        }
        int lastSlash = normalized.lastIndexOf("/");
        if (lastSlash == 0) {
            return "/";
        }
        return normalized.substring(0, lastSlash);
    }

    /**
     * 构建带参数的 URL
     */
    public static String buildUrl(String path, String... parameters) {
        if (StringUtils.isBlank(path)) {
            return path;
        }
        if (parameters == null || parameters.length == 0 || parameters.length % 2 != 0) {
            return path;
        }
        StringBuilder sb = new StringBuilder(path);
        boolean hasQuery = path.contains("?");
        for (int i = 0; i < parameters.length; i += 2) {
            String key = parameters[i];
            String value = parameters[i + 1];
            if (StringUtils.isBlank(key)) {
                continue;
            }
            sb.append(hasQuery ? "&" : "?");
            sb.append(encodeParam(key));
            sb.append("=");
            if (value != null) {
                sb.append(encodeParam(value));
            }
            hasQuery = true;
        }
        return sb.toString();
    }

    /**
     * 从路径中提取所有路径变量名
     */
    public static List<String> extractVariableNames(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return Collections.emptyList();
        }
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pattern);
        List<String> variableNames = new ArrayList<>();
        while (matcher.find()) {
            variableNames.add(matcher.group(1));
        }
        return variableNames;
    }

    /**
     * 替换路径中的变量为实际值
     */
    public static String replaceVariables(String pattern, Map<String, String> variables) {
        if (StringUtils.isBlank(pattern) || CollectionUtils.isEmpty(variables)) {
            return pattern;
        }
        String result = pattern;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
