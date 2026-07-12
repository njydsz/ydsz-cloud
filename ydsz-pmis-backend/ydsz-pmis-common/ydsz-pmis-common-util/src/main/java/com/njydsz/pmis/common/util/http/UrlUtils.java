package com.njydsz.pmis.common.util.http;

import com.njydsz.pmis.common.util.string.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UrlUtils - URL 处理工具类 (增强版)
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class UrlUtils {

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String FILE_PREFIX = "file://";
    private static final String FTP_PREFIX = "ftp://";

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
        
        String baseUrl = url.split("\\?")[0];
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
        
        String baseUrl = url.split("\\?")[0];
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
        
        // 替换多个斜杠为一个
        url = url.replaceAll("/+", "/");
        
        // 修复协议部分的双斜杠
        url = url.replaceAll("^(https?):/([^/])", "$1://$2");
        
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
     * 确保 URL 以 http:// 或 https:// 开头
     */
    public static String ensureHttpPrefix(String url) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }
        
        if (!isHttpUrl(url)) {
            return HTTPS_PREFIX + url;
        }
        
        return url;
    }
}
