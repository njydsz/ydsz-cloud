package com.njydsz.common.util.http;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.njydsz.common.util.ip.IpAddrUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * URL 处理工具类
 *
 * <p>提供 URL 协议检测、URL 编码解码、URL 校验等能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class UrlUtils {

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String FILE_PREFIX = "file://";
    private static final String FTP_PREFIX = "ftp://";
    /** 折叠连续斜杠的预编译正则（类加载时编译一次，避免每次调用重新编译） */
    private static final Pattern MULTI_SLASH_PATTERN = Pattern.compile("/+");
    /** 修复协议部分双斜杠的预编译正则 */
    private static final Pattern PROTOCOL_SLASH_PATTERN = Pattern.compile("^(https?):/([^/])");

    /**
     * 判断是否为 HTTP/HTTPS 协议
     */
    public static boolean isHttpUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        return lowerUrl.startsWith(HTTP_PREFIX) || lowerUrl.startsWith(HTTPS_PREFIX);
    }

    /**
     * 判断是否为 HTTPS 协议
     */
    public static boolean isHttpsUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        return url.toLowerCase().startsWith(HTTPS_PREFIX);
    }

    /**
     * 判断是否为 FILE 协议
     */
    public static boolean isFileUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        return url.toLowerCase().startsWith(FILE_PREFIX);
    }

    /**
     * 判断是否为 FTP 协议
     */
    public static boolean isFtpUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        return url.toLowerCase().startsWith(FTP_PREFIX);
    }

    /**
     * URL 编码 (UTF-8)
     */
    public static String encode(String url) {
        return url == null ? null : URLEncoder.encode(url, StandardCharsets.UTF_8);
    }

    /**
     * URL 解码 (UTF-8)
     */
    public static String decode(String url) {
        return url == null ? null : URLDecoder.decode(url, StandardCharsets.UTF_8);
    }

    /**
     * 解析 URL 参数为 Map（保持顺序）
     */
    public static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new LinkedHashMap<>();
        if (StringUtils.isEmpty(queryString)) {
            return params;
        }

        // 处理完整的 URL，提取 query 部分
        int queryIdx = queryString.indexOf('?');
        if (queryIdx != -1) {
            queryString = queryString.substring(queryIdx + 1);
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (StringUtils.isEmpty(pair)) {
                continue;
            }
            int idx = pair.indexOf("=");
            if (idx != -1) {
                params.put(decode(pair.substring(0, idx)), decode(pair.substring(idx + 1)));
            } else {
                params.put(decode(pair), "");
            }
        }
        return params;
    }

    /**
     * 从 URL 中提取主机名
     */
    public static String getHost(String url) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        try {
            URI parsedUri = URI.create(url);
            return parsedUri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 URL 中提取端口
     */
    public static int getPort(String url) {
        if (StringUtils.isEmpty(url)) {
            return -1;
        }
        try {
            URI parsedUri = URI.create(url);
            return parsedUri.getPort();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 从 URL 中提取协议
     */
    public static String getProtocol(String url) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        try {
            URI parsedUri = URI.create(url);
            return parsedUri.getScheme();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 URL 中提取路径
     */
    public static String getPath(String url) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        try {
            URI parsedUri = URI.create(url);
            return parsedUri.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 URL 中提取查询字符串（不含问号）
     */
    public static String getQuery(String url) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        try {
            URI parsedUri = URI.create(url);
            return parsedUri.getRawQuery();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建 URL，自动添加参数
     */
    public static String buildUrl(String baseUrl, Map<String, String> params) {
        if (StringUtils.isEmpty(baseUrl)) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean hasQuery = baseUrl.contains("?");
        
        if (params != null && !params.isEmpty()) {
            boolean first = !hasQuery;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                // value 为 null 时跳过该 entry，避免写入字符串 "null"
                if (entry.getValue() == null) {
                    continue;
                }
                if (first) {
                    sb.append("?");
                    first = false;
                } else {
                    sb.append("&");
                }
                sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
            }
        }
        
        return sb.toString();
    }

    /**
     * 移除 URL 中的指定参数
     */
    public static String removeParamFromUrl(String url, String paramName) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(paramName)) {
            return url;
        }
        
        Map<String, String> params = parseQueryString(url);
        params.remove(paramName);

        int qIdx = url.indexOf('?');
        String baseUrl = qIdx >= 0 ? url.substring(0, qIdx) : url;
        return buildUrl(baseUrl, params);
    }

    /**
     * 替换 URL 中的参数值
     */
    public static String replaceParamInUrl(String url, String paramName, String paramValue) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(paramName)) {
            return url;
        }

        Map<String, String> params = parseQueryString(url);
        params.put(paramName, paramValue);

        int qIdx = url.indexOf('?');
        String baseUrl = qIdx >= 0 ? url.substring(0, qIdx) : url;
        return buildUrl(baseUrl, params);
    }

    /**
     * 规范化 URL（去除多余斜杠等）
     */
    public static String normalizeUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }
        
        // 移除末尾的斜杠（如果是根路径则保留）
        while (url.length() > 1 && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        // 替换多个斜杠为一个（使用预编译 Pattern，避免每次调用重新编译正则）
        url = MULTI_SLASH_PATTERN.matcher(url).replaceAll("/");

        // 修复协议部分的双斜杠
        url = PROTOCOL_SLASH_PATTERN.matcher(url).replaceAll("$1://$2");
        
        return url;
    }

    /**
     * 判断两个 URL 是否指向同一主机
     */
    public static boolean isSameHost(String url1, String url2) {
        if (StringUtils.isEmpty(url1) || StringUtils.isEmpty(url2)) {
            return false;
        }
        
        String host1 = getHost(url1);
        String host2 = getHost(url2);
        
        return host1 != null && host1.equals(host2);
    }

    /**
     * 获取 URL 的域名（不含 www）
     */
    public static String getDomain(String url) {
        String host = getHost(url);
        if (StringUtils.isEmpty(host)) {
            return null;
        }
        
        // 移除 www 前缀
        if (host.startsWith("www.")) {
            return host.substring(4);
        }
        
        return host;
    }

    /**
     * 检查 URL 是否合法
     */
    public static boolean isValidUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        
        try {
            URI uri = URI.create(url);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 确保 URL 以 http:// 或 https:// 开头。
     *
     * <p>若 url 已以 http:// 或 https:// 开头则原样返回；否则抛出 {@link IllegalArgumentException}，
     * 不再自动拼接 https:// 以免对非 http URL（如 ftp://、file://）生成畸形 URL。
     *
     * @param url 待校验的 URL
     * @return 原 url（已具备 http/https 前缀）
     * @throws IllegalArgumentException 当 url 非空且不以 http:// 或 https:// 开头时
     */
    public static String ensureHttpPrefix(String url) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }

        if (!isHttpUrl(url)) {
            throw new IllegalArgumentException("URL 必须以 http:// 或 https:// 开头: " + url);
        }

        return url;
    }
}
