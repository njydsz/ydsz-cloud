package com.remisoft.common.util.http;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.remisoft.common.util.ip.IpAddrUtils;
import com.remisoft.common.util.string.StringUtils;

/**
 * URL 处理工具类
 *
 * <p>提供 URL 协议检测、URL 编码解码、URL 校验等能力。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class UrlUtils {

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String FILE_PREFIX = "file://";
    private static final String FTP_PREFIX = "ftp://";
    /** 折叠连续斜杠的预编译正则（类加载时编译一次，避免每次调用重新编译） */
    private static final Pattern MULTI_SLASH_PATTERN = Pattern.compile("/+");

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
     * URL 编码（application/x-www-form-urlencoded 格式）。
     *
     * <p>基于 {@link URLEncoder#encode(String, java.nio.charset.Charset)}，
     * 空格编码为 {@code +}，适用于 HTML 表单参数。
     *
     * <p>若需要 RFC 3986 URI 编码（空格编码为 {@code %20}），
     * 请使用 {@link #encodeUri(String)}。
     *
     * @param value 待编码字符串
     * @return 编码后字符串，输入为 null 时返回 null
     */
    public static String encode(String value) {
        return value == null ? null : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * URI 编码（RFC 3986 标准）。
     *
     * <p>与 {@link #encode(String)} 的区别：
     * <ul>
     *   <li>空格编码为 {@code %20}（而非 {@code +}），符合 RFC 3986</li>
     *   <li>{@code *} 编码为 {@code %2A}（保留字符规范化）</li>
     *   <li>{@code ~} 不编码（RFC 3986 允许的未保留字符）</li>
     * </ul>
     *
     * <p>适用于编码 URI 路径段或查询参数值，不适用于 HTML 表单。
     *
     * @param value 待编码字符串
     * @return 编码后字符串，输入为 null 时返回 null
     */
    public static String encodeUri(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    /**
     * URL 解码 (UTF-8)
     *
     * @param value 待解码字符串
     * @return 解码后字符串，输入为 null 时返回 null
     */
    public static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
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
     * 规范化 URL（安全版本）。
     *
     * <p>执行以下规范化操作：
     * <ul>
     *   <li>将 Windows 反斜杠 {@code \} 替换为正斜杠 {@code /}</li>
     *   <li>折叠路径中的连续斜杠（协议部分 {@code ://} 除外）</li>
     *   <li>移除路径末尾的斜杠（根路径 {@code /} 除外）</li>
     *   <li>处理协议相对 URL（{@code //example.com}）</li>
     * </ul>
     *
     * <p><b>安全注意：</b>本方法不执行路径遍历解析（如 {@code ../}），
     * 也不做协议/主机名大小写规范化。如需更完整的 RFC 3986 规范化，
     * 请使用 {@link URI#normalize()}。
     *
     * @param url 待规范化 URL
     * @return 规范化后的 URL，输入为空/空白时返回原值
     */
    public static String normalizeUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }

        // 将 Windows 反斜杠替换为正斜杠
        url = url.replace('\\', '/');

        // 分割协议分隔符与路径部分，避免协议中的 // 被错误折叠
        String prefix;
        String path;
        int protocolIdx = url.indexOf("://");
        if (protocolIdx != -1) {
            prefix = url.substring(0, protocolIdx + 3);
            path = url.substring(protocolIdx + 3);
        } else if (url.startsWith("//")) {
            // 协议相对 URL：//example.com/path
            prefix = "//";
            path = url.substring(2);
        } else {
            prefix = "";
            path = url;
        }

        // 折叠路径中的连续斜杠
        path = MULTI_SLASH_PATTERN.matcher(path).replaceAll("/");

        // 移除路径末尾的斜杠（根路径 "/" 除外）
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return prefix + path;
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

    /**
     * 判断 URL 的 host 是否为内网地址（SSRF 防护）。
     *
     * <p>用于服务端发起外部请求前判断目标地址是否指向内网，避免 SSRF。
     * 复用 {@link IpAddrUtils#isInternalIp(String)} 判断 host 是否为内网/回环 IP。
     * 注意：仅对 host 为字面量 IP 的场景生效，域名需调用方自行解析后再判断（防 DNS rebinding）。
     *
     * @param url 待判断的 URL
     * @return true 表示 URL host 为内网/回环地址；URL 为空或 host 无法解析时返回 false
     */
    public static boolean isInternalUrl(String url) {
        String host = getHost(url);
        if (StringUtils.isEmpty(host)) {
            return false;
        }
        return IpAddrUtils.isInternalIp(host);
    }

    /**
     * 断言 URL 为安全的外部地址（SSRF 防护）。
     *
     * <p>用于服务端发起请求前拦截内网地址，防止 SSRF 攻击。当 URL 非 http/https 协议，
     * 或 host 为内网/回环地址时抛出 {@link IllegalArgumentException}。
     *
     * @param url 待校验的 URL
     * @throws IllegalArgumentException 当 URL 协议非法或指向内网地址时
     */
    public static void assertSafeExternalUrl(String url) {
        if (!isHttpUrl(url)) {
            throw new IllegalArgumentException("URL 必须为 http/https 协议: " + url);
        }
        if (isInternalUrl(url)) {
            throw new IllegalArgumentException("URL 指向内网地址，疑似 SSRF 攻击: " + url);
        }
    }
}
