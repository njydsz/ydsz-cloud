package com.njydsz.common.safe.ssrf;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * SSRF 防护校验器 — 防止服务端请求伪造攻击。
 *
 * <p>校验所有出站 HTTP 请求的目标地址，确保不访问内网 IP、链路本地地址、
 * 元数据服务（169.254.169.254）等敏感目标。</p>
 *
 * <p><b>防护范围：</b></p>
 * <ul>
 *   <li>内网 IP 段（10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、127.0.0.0/8）</li>
 *   <li>链路本地地址（169.254.0.0/16、fe80::/10）</li>
 *   <li>元数据服务（169.254.169.254、100.100.100.200）</li>
 *   <li>IPv6 本地地址（::1、fc00::/7、fd00::/8）</li>
 *   <li>IPv4-Mapped IPv6 中的内网地址（::ffff:127.0.0.1 等）</li>
 * </ul>
 *
 * <p><b>使用方式：</b></p>
 * <pre>{@code
 * // 在 RestTemplate 拦截器或 OkHttp 拦截器中调用
 * HttpConnectionValidator.getDefault().validate("http://example.com/api");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
public final class HttpConnectionValidator {

    /** AWS/Azure/GCP 实例元数据服务地址 */
    private static final List<String> METADATA_ENDPOINTS = Arrays.asList(
            "169.254.169.254",
            "100.100.100.200",
            "169.254.169.255",
            "fd00:ec2::254"
    );

    /** 内网 IPv4 正则（CIDR 展开为简化模式） */
    private static final List<Pattern> INTERNAL_IP_PATTERNS = Arrays.asList(
            Pattern.compile("^10\\..*"),
            Pattern.compile("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"),
            Pattern.compile("^192\\.168\\..*"),
            Pattern.compile("^127\\..*"),
            Pattern.compile("^169\\.254\\..*"),
            Pattern.compile("^0\\..*"),
            Pattern.compile("^192\\.0\\.2\\..*"),
            Pattern.compile("^198\\.51\\.100\\..*"),
            Pattern.compile("^203\\.0\\.113\\..*"),
            Pattern.compile("^224\\..*|^239\\..*|^240\\..*|^255\\..*")
    );

    /** IPv6 本地地址正则 */
    private static final List<Pattern> INTERNAL_IPV6_PATTERNS = Arrays.asList(
            Pattern.compile("^::1$"),
            Pattern.compile("^fc", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^fd", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^fe80:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^::ffff:10\\."),
            Pattern.compile("^::ffff:172\\.(1[6-9]|2[0-9]|3[01])\\."),
            Pattern.compile("^::ffff:192\\.168\\."),
            Pattern.compile("^::ffff:127\\.")
    );

    private static volatile HttpConnectionValidator defaultInstance;

    private volatile SsrfProperties properties;

    private HttpConnectionValidator(SsrfProperties properties) {
        this.properties = properties != null ? properties : new SsrfProperties();
    }

    /**
     * 获取默认 SSRF 校验器实例（懒初始化，单例）。
     *
     * @return 默认 SSRF 校验器
     */
    public static HttpConnectionValidator getDefault() {
        if (defaultInstance == null) {
            synchronized (HttpConnectionValidator.class) {
                if (defaultInstance == null) {
                    defaultInstance = new HttpConnectionValidator(null);
                }
            }
        }
        return defaultInstance;
    }

    /**
     * 创建带自定义配置的校验器实例。
     *
     * @param properties SSRF 防护配置，null 时使用默认配置
     * @return 定制的 SSRF 校验器
     */
    public static HttpConnectionValidator with(SsrfProperties properties) {
        return new HttpConnectionValidator(properties);
    }

    /**
     * 更新默认实例的校验策略（热更新支持）。
     *
     * @param newProperties 新的 SSRF 配置
     */
    public static void updateProperties(SsrfProperties newProperties) {
        if (newProperties != null && defaultInstance != null) {
            defaultInstance.properties = newProperties;
        }
    }

    /**
     * 验证 URL 是否允许访问。
     *
     * @param urlString 待验证的目标 URL
     * @throws SsrfBlockedException 若 URL 被判定为风险目标
     */
    public void validate(String urlString) throws SsrfBlockedException {
        if (!properties.isEnabled()) {
            return;
        }
        if (urlString == null || urlString.isBlank()) {
            return;
        }

        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            throw new SsrfBlockedException("Invalid URL syntax: " + urlString, e);
        }

        String scheme = uri.getScheme();
        if (scheme != null) {
            String lowerScheme = scheme.toLowerCase();
            if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
                throw new SsrfBlockedException("Unsupported protocol: " + scheme);
            }
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfBlockedException("URL has no valid host: " + urlString);
        }
        String lowerHost = host.toLowerCase().trim();

        for (Pattern pattern : properties.getAllowedDomainPatterns()) {
            if (pattern.matcher(lowerHost).matches()) {
                return;
            }
        }

        for (Pattern pattern : properties.getBlockedDomainPatterns()) {
            if (pattern.matcher(lowerHost).matches()) {
                throw new SsrfBlockedException("Domain explicitly blocked: " + host);
            }
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfBlockedException("Unable to resolve host: " + host, e);
        }

        for (InetAddress addr : addresses) {
            String ip = addr.getHostAddress();
            if (isInternalIp(ip)) {
                throw new SsrfBlockedException(
                        "Internal network address blocked: " + host + " -> " + ip);
            }
        }
    }

    /**
     * 检查 IP 地址是否为内网/保留地址。
     *
     * @param ip 待检查的 IP 地址（v4 或 v6 格式）
     * @return true 若为内网/保留地址
     */
    public boolean isInternalIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }

        for (String metadata : METADATA_ENDPOINTS) {
            if (ip.equalsIgnoreCase(metadata)) {
                return true;
            }
        }

        if (ip.contains(".")) {
            for (Pattern pattern : INTERNAL_IP_PATTERNS) {
                if (pattern.matcher(ip).matches()) {
                    return true;
                }
            }
        }

        if (ip.contains(":")) {
            for (Pattern pattern : INTERNAL_IPV6_PATTERNS) {
                if (pattern.matcher(ip).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取当前生效的 SSRF 配置。
     *
     * @return 当前配置
     */
    public SsrfProperties getProperties() {
        return properties;
    }

    /**
     * SSRF 防护配置属性。
     */
    public static class SsrfProperties {

        private boolean enabled = true;

        private List<String> allowedDomains = Collections.emptyList();

        private List<String> blockedDomains = Arrays.asList(
                "localhost",
                "*.local",
                "*.internal",
                "*.corp"
        );

        private List<Pattern> allowedDomainPatterns = Collections.emptyList();

        private List<Pattern> blockedDomainPatterns;

        public SsrfProperties() {
            compilePatterns();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(List<String> allowedDomains) {
            this.allowedDomains = allowedDomains != null ? allowedDomains : Collections.emptyList();
            compilePatterns();
        }

        public List<String> getBlockedDomains() {
            return blockedDomains;
        }

        public void setBlockedDomains(List<String> blockedDomains) {
            this.blockedDomains = blockedDomains != null ? blockedDomains : Collections.emptyList();
            compilePatterns();
        }

        public List<Pattern> getAllowedDomainPatterns() {
            return allowedDomainPatterns;
        }

        public List<Pattern> getBlockedDomainPatterns() {
            return blockedDomainPatterns;
        }

        private void compilePatterns() {
            this.allowedDomainPatterns = allowedDomains.stream()
                    .map(this::domainToRegex)
                    .toList();
            this.blockedDomainPatterns = blockedDomains.stream()
                    .map(this::domainToRegex)
                    .toList();
        }

        private Pattern domainToRegex(String domain) {
            String regex = domain.replace(".", "\\.")
                    .replace("*", ".*");
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }
    }

    /**
     * SSRF 访问阻断异常。
     *
     * <p>当出站 HTTP 请求被 SSRF 防护机制拦截时抛出，携带安全异常码
     * {@link SecurityExceptionCode#SEC_ACCESS_DENIED}（C01051 / 403），
     * 由全局异常处理器统一转换为标准错误响应。</p>
     *
     * @author ydsz-team
     * @since 1.0.0
     */
    public static class SsrfBlockedException extends BusinessException {
        private static final long serialVersionUID = 1L;

        public SsrfBlockedException(String message) {
            super(SecurityExceptionCode.SEC_ACCESS_DENIED);
            setMessage(message);
        }

        public SsrfBlockedException(String message, Throwable cause) {
            super(SecurityExceptionCode.SEC_ACCESS_DENIED, cause);
            setMessage(message);
        }
    }
}
