package com.njydsz.pmis.common.safe.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.safe.config.ApiSignatureProperties;
import com.njydsz.pmis.common.safe.crypto.NonceCache;
import com.njydsz.pmis.common.safe.util.ClientIpResolver;
import com.njydsz.pmis.common.util.url.UrlPathUtils;

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
 *   <li>通过 {@link NonceCache} 校验 nonce 唯一性（原子操作），防止重复提交</li>
 *   <li>重组签名串：{@code method + "\n" + uri + "\n" + timestamp + "\n" + nonce + "\n" + bodySha256}</li>
 *   <li>使用 HMAC-SHA256 + appSecret 计算签名，与请求头签名比对</li>
 * </ol>
 *
 * <p><b>签名计算示例：</b>
 * <pre>{@code
 * String raw = "POST\n/api/v1/order/create\n1700000000000\nabc123\ne3b0c44298fc1c149afbf4c8996fb924..."
 * String signature = Base64(HMAC-SHA256(raw, appSecret))
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
 * // 3. 重组签名串
 * String raw = method + "\n" + uri + "\n" + timestamp + "\n" + nonce + "\n" + bodySha256;
 *
 * // 4. HMAC-SHA256 签名
 * String signature = Base64(HMAC-SHA256(raw, appSecret));
 *
 * // 5. 设置请求头
 * request.setHeader("X-App-Id", appId);
 * request.setHeader("X-Timestamp", String.valueOf(timestamp));
 * request.setHeader("X-Nonce", nonce);
 * request.setHeader("X-Signature", signature);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 * @see ApiSignatureProperties
 * @see NonceCache
 */
public class ApiSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiSignatureFilter.class);

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";

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

        if (!nonceCache.verifyAndConsume(nonce)) {
            log.warn("【API签名验证】Nonce 重复 | uri={}, nonce={}", request.getRequestURI(), nonce);
            publishEvent(request, "Nonce replay detected: " + nonce);
            reject(response, "Duplicate request");
            return;
        }

        byte[] bodyBytes = new byte[0];
        CachedBodyHttpServletRequest wrappedRequest = null;
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            try {
                bodyBytes = request.getInputStream().readAllBytes();
                wrappedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);
            } catch (IOException e) {
                log.warn("【API签名验证】读取请求体失败 | uri={}", request.getRequestURI());
            }
        }

        String bodySha256 = sha256Hex(bodyBytes);
        String raw = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
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

        filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, response);
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

    /**
     * 缓存请求体的 HTTP 请求包装器
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ByteArrayInputStream bis = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bis.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }

                @Override
                public int read() throws IOException {
                    return bis.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
