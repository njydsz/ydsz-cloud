package com.njydsz.pmis.common.app.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * App 端请求签名校验过滤器
 *
 * <p>使用 HMAC-SHA256 对请求进行签名验证，防止请求伪造。
 * <ul>
 *   <li>客户端在请求头中携带 {@code X-App-Timestamp} 和 {@code X-App-Signature}</li>
 *   <li>签名计算方式：HMAC-SHA256(secret, method + uri + timestamp + body)</li>
 *   <li>时间戳容差由 {@code pmis.app.signature.timestamp-tolerance} 控制</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class AppSignatureFilter extends OncePerRequestFilter {

    private static final String HEADER_TIMESTAMP = "X-App-Timestamp";
    private static final String HEADER_SIGNATURE = "X-App-Signature";

    private final String appSecret;
    private final long timestampTolerance;

    /**
     * 构造签名校验过滤器
     *
     * @param appSecret         签名密钥
     * @param timestampTolerance 时间戳容差（毫秒）
     */
    public AppSignatureFilter(String appSecret, long timestampTolerance) {
        this.appSecret = appSecret;
        this.timestampTolerance = timestampTolerance;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (timestamp == null || signature == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "缺少签名参数");
            return;
        }

        // 验证时间戳
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > timestampTolerance) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "请求已过期");
                return;
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无效的时间戳");
            return;
        }

        // 计算签名
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String body = "";
        if (request instanceof ContentCachingRequestWrapper wrapped) {
            byte[] buf = wrapped.getContentAsByteArray();
            if (buf.length > 0) {
                body = new String(buf, StandardCharsets.UTF_8);
            }
        }

        String expectedSignature = computeSignature(appSecret, method, uri, timestamp, body);
        if (!expectedSignature.equals(signature)) {
            log.warn("[App签名校验] 签名不匹配 | uri={} | expected={} | actual={}", uri, expectedSignature, signature);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "签名校验失败");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String computeSignature(String secret, String method, String uri, String timestamp, String body) {
        try {
            String data = method + uri + timestamp + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException("签名计算失败", e);
        }
    }
}
