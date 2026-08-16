package com.njydsz.common.safe.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import com.njydsz.common.util.http.UrlPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.common.safe.config.ApiSignatureProperties;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.common.safe.util.ClientIpResolver;

/**
 * API 签名验证过滤器
 *
 * <p>基于 {@code timestamp + nonce + signature} 三要素实现 API 请求防篡改和防重放。
 * 使用 HMAC-SHA256 算法计算签名，确保请求在传输过程中未被篡改。
 *
 * <p><b>验证流程：</b>
 * <ol>
 *   <li>提取请求头中的 {@code X-Timestamp}、{@code X-Nonce}、{@code X-Signature}、{@code X-App-Id}</li>
 *   <li>校验时间戳偏移量（±{@link ApiSignatureProperties#getTimestampToleranceSeconds()} 秒），防止重放</li>
 *   <li>重组签名串（含规范化 Query String）：{@code method + "\n" + path + "\n" + normalizedQuery + "\n" + timestamp + "\n" + nonce + "\n" + bodySha256}</li>
 *   <li>使用 HMAC-SHA256 + appSecret 计算签名，与请求头签名比对（常量时间比较）</li>
 *   <li><b>先验签、后消费 nonce</b>：签名校验通过后才写入 nonce 缓存，
 *       防止攻击者用伪造签名 + 随机 nonce 打满缓存导致合法请求被误判重放（DoS）</li>
 * </ol>
 *
 * <p><b>签名计算示例：</b>
 * <pre>{@code
 * // GET 请求（无 query）：query 行固定为 `\n` 后的空串
 * String raw = "GET\n/api/v1/order/detail\n\ne3b0c44298fc1c149afbf4c8996fb924..."
 * // GET 请求（带 query，按 key 字典序排序后规范化）：
 * String raw = "GET\n/api/v1/order/list\nid=1&page=2\ne3b0c44298fc1c149afbf4c8996fb924..."
 * // POST 请求：
 * String raw = "POST\n/api/v1/order/create\n\ne3b0c44298fc1c149afbf4c8996fb924..."
 * }</pre>
 *
 * <p><b>客户端使用：</b>
 * <pre>{@code
 * // 1. 获取时间戳和 nonce
 * long timestamp = System.currentTimeMillis();
 * String nonce = UUID.randomUUID().toString();
 *
 * // 2. 计算请求体 SHA-256
 * String bodySha256 = Hex(SHA256(requestBody));
 *
 * // 3. 规范化 Query String：按 key 字典序排序，key=value 用 & 连接；无 query 为空串
 * String normalizedQuery = normalizeQuery(request.getQueryString());
 *
 * // 4. 重组签名串
 * String raw = method + "\n" + path + "\n" + normalizedQuery + "\n" + timestamp + "\n" + nonce + "\n" + bodySha256;
 *
 * // 5. HMAC-SHA256 签名
 * String signature = Base64(HMAC-SHA256(raw, appSecret));
 *
 * // 6. 设置请求头
 * request.setHeader("X-App-Id", appId);
 * request.setHeader("X-Timestamp", String.valueOf(timestamp));
 * request.setHeader("X-Nonce", nonce);
 * request.setHeader("X-Signature", signature);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ApiSignatureProperties
 * @see NonceCache
 */
public class ApiSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiSignatureFilter.class);

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    private static final String QUERY_SEPARATOR = "&";
    private static final String QUERY_KV_SEPARATOR = "=";

    private final ApiSignatureProperties properties;
    private final NonceCache nonceCache;
    private final SecurityEventPublisher eventPublisher;

    /**
     * @param properties     签名配置属性
     * @param nonceCache     防重放 Nonce 缓存
     * @param eventPublisher 安全事件发布器
     */
    public ApiSignatureFilter(ApiSignatureProperties properties,
                              NonceCache nonceCache,
                              SecurityEventPublisher eventPublisher) {
        this.properties = properties;
        this.nonceCache = nonceCache;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled() || isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String timestamp = request.getHeader(properties.getHeaderTimestamp());
        String nonce = request.getHeader(properties.getHeaderNonce());
        String signature = request.getHeader(properties.getHeaderSignature());

        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            log.warn("【API签名验证】缺少签名参数 | uri={}", request.getRequestURI());
            reject(response, "Missing signature parameters");
            return;
        }

        long requestTimestamp;
        try {
            requestTimestamp = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, "Invalid timestamp format");
            return;
        }

        long now = System.currentTimeMillis();
        long toleranceMillis = properties.getTimestampToleranceSeconds() * 1000;
        if (Math.abs(now - requestTimestamp) > toleranceMillis) {
            log.warn("【API签名验证】时间戳超出容差 | uri={}, ts={}, now={}", request.getRequestURI(), requestTimestamp, now);
            publishEvent(request, "Timestamp out of tolerance");
            reject(response, "Request expired");
            return;
        }

        byte[] bodyBytes = new byte[0];
        CachedBodyHttpServletRequestWrapper wrappedRequest = null;
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            bodyBytes = extractBodyBytes(request);
            // 仅在请求未被包装时才创建新包装器
            if (!(request instanceof CachedBodyHttpServletRequestWrapper)) {
                wrappedRequest = new CachedBodyHttpServletRequestWrapper(request, bodyBytes);
            }
        }

        // 签名串包含规范化 Query String，防止 GET 参数被篡改
        String normalizedQuery = normalizeQuery(request.getQueryString());
        String bodySha256 = sha256Hex(bodyBytes);
        String raw = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + normalizedQuery + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + bodySha256;

        String expectedSignature = hmacSha256Base64(raw, properties.getAppSecret());
        if (!constantTimeEquals(expectedSignature, signature)) {
            log.warn("【API签名验证】签名校验失败 | uri={}", request.getRequestURI());
            publishEvent(request, "Signature mismatch");
            reject(response, "Invalid signature");
            return;
        }

        // 先验签后消费 nonce：签名合法才写入缓存，防止伪造签名 + 随机 nonce 打满缓存（DoS）
        if (!nonceCache.verifyAndConsume(nonce)) {
            log.warn("【API签名验证】Nonce 重复 | uri={}, nonce={}", request.getRequestURI(), nonce);
            publishEvent(request, "Nonce replay detected: " + nonce);
            reject(response, "Duplicate request");
            return;
        }

        filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, response);
    }

    /**
     * 规范化 Query String：按 key 字典序排序，{@code key=value} 用 {@code &} 连接。
     *
     * <p>服务端与客户端必须采用相同规则，否则同一请求两侧算出的签名不一致。
     * 无 query 时返回空串（签名串中对应空行），保证 GET 参数被篡改时签名校验失败。
     *
     * @param queryString 原始 Query String，可为 null
     * @return 规范化后的 Query String（无 query 时为空串）
     */
    private static String normalizeQuery(String queryString) {
        if (!StringUtils.hasText(queryString)) {
            return "";
        }
        List<String> pairs = new java.util.ArrayList<>();
        for (String pair : queryString.split(QUERY_SEPARATOR)) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            pairs.add(pair);
        }
        pairs.sort(String::compareTo);
        return String.join(QUERY_SEPARATOR, pairs);
    }

    /**
     * 拒绝请求并返回 401 响应
     */
    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"A04010\",\"msg\":\"" + message + "\"}");
    }

    /**
     * 发布安全事件
     */
    private void publishEvent(HttpServletRequest request, String payload) {
        if (eventPublisher != null) {
            SecurityEvent event = new SecurityEvent(
                    SecurityEventType.ILLEGAL_ACCESS,
                    request.getRequestURI(),
                    ClientIpResolver.getClientIp(request),
                    request.getHeader("User-Agent"),
                    payload,
                    SecurityEvent.Severity.HIGH
            );
            eventPublisher.publish(event);
        }
    }

    /**
     * 提取请求体字节数组（优先复用 SafeRequestBodyCacheFilter 已缓存的请求体）
     *
     * @param request HTTP 请求
     * @return 请求体字节数组；若无 body 或读取失败返回空数组
     */
    private static byte[] extractBodyBytes(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequestWrapper cachedWrapper) {
            return cachedWrapper.getCachedBody();
        }
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("【API签名验证】读取请求体失败 | uri={}", request.getRequestURI());
            return new byte[0];
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        List<String> excludes = properties.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        return UrlPathUtils.matchAny(excludes, request.getServletPath());
    }


    /**
     * 计算 SHA-256 哈希（十六进制输出）
     */
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 计算 HMAC-SHA256 签名（Base64 输出）
     */
    private static String hmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * 常量时间比较，防止时序攻击
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

}
