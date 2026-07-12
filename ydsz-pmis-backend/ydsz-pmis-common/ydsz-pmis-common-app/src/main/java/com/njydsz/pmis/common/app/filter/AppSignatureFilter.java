package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.util.security.DigestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * App 端请求签名验证过滤器
 *
 * <p>基于 HMAC-SHA256 算法验证请求签名的完整性和来源合法性。
 *
 * <p><b>签名算法：</b>
 * <pre>
 * signature = HMAC-SHA256(
 *     key = appSecret,
 *     data = method + "|" + uri + "|" + timestamp + "|" + nonce
 * )
 * </pre>
 *
 * <p><b>必需请求头：</b>
 * <ul>
 *   <li>{@code X-App-Sign} - 客户端计算的签名值（Hex 格式）</li>
 *   <li>{@code X-App-Timestamp} - 请求时间戳（毫秒）</li>
 *   <li>{@code X-App-Nonce} - 随机字符串（用于防重放）</li>
 * </ul>
 *
 * <p><b>配置开关：</b>
 * <ul>
 *   <li>{@code ydsz.app.signature.enabled=false} 可禁用签名验证</li>
 *   <li>{@code ydsz.app.signature.app-secret} 配置签名密钥（必填）</li>
 *   <li>{@code ydsz.app.signature.timestamp-tolerance} 时间戳容差（毫秒，默认 5 分钟）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class AppSignatureFilter extends OncePerRequestFilter {

    /** 签名头名称，客户端将计算后的 Hex 字符串写入此头 */
    private static final String HEADER_SIGNATURE = "X-App-Sign";
    /** 时间戳头名称（毫秒） */
    private static final String HEADER_TIMESTAMP = "X-App-Timestamp";
    /** 随机字符串头名称，用于防重放 */
    private static final String HEADER_NONCE = "X-App-Nonce";

    /** HMAC-SHA256 签名密钥，由 {@link AppSignatureProperties#getAppSecret()} 注入 */
    private final String appSecret;
    /** 时间戳容差（毫秒），由 {@link AppSignatureProperties#getTimestampTolerance()} 注入 */
    private final long timestampTolerance;

    /**
     * 构造方法
     *
     * @param appSecret          HMAC-SHA256 签名密钥
     * @param timestampTolerance 时间戳容差（毫秒）
     */
    public AppSignatureFilter(String appSecret, long timestampTolerance) {
        this.appSecret = appSecret;
        this.timestampTolerance = timestampTolerance;
    }

    /**
     * 执行签名校验的核心逻辑
     *
     * <p>校验流程：
     * <ol>
     *   <li>提取签名相关请求头，缺失则返回 400</li>
     *   <li>解析时间戳并校验是否在容差范围内</li>
     *   <li>按 {@code method|uri|timestamp|nonce} 计算期望签名</li>
     *   <li>使用恒定时间比较客户端签名与服务端计算签名</li>
     * </ol>
     *
     * <p>任意一步校验失败均直接响应错误，不再放行至后续过滤器。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 透传 Servlet 异常
     * @throws IOException      透传 IO 异常
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 提取签名相关请求头
        String signature = request.getHeader(HEADER_SIGNATURE);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);

        // 验证必需头
        if (signature == null || timestampStr == null || nonce == null) {
            log.warn("【App签名验证】缺少必需请求头 | uri={} | sign={} | timestamp={} | nonce={}",
                    request.getRequestURI(), signature, timestampStr, nonce);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing signature headers");
            return;
        }

        // 验证时间戳
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("【App签名验证】时间戳格式错误 | uri={} | timestamp={}", request.getRequestURI(), timestampStr);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp format");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - timestamp) > timestampTolerance) {
            log.warn("【App签名验证】时间戳过期 | uri={} | timestamp={} | currentTime={} | tolerance={}",
                    request.getRequestURI(), timestamp, currentTime, timestampTolerance);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timestamp expired");
            return;
        }

        // 构建签名字符串
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String signData = method + "|" + uri + "|" + timestamp + "|" + nonce;

        // 计算期望签名
        String expectedSignature = DigestUtils.hmacSha256Hex(signData, appSecret);

        // 验证签名（时序恒定比较）
        if (!DigestUtils.verifyDigestHex(expectedSignature, signature)) {
            log.warn("【App签名验证】签名不匹配 | uri={} | expected={} | actual={}",
                    request.getRequestURI(), expectedSignature, signature);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return;
        }

        log.debug("【App签名验证】通过 | uri={}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
