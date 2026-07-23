package com.njydsz.common.app.filter;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.app.config.AppSignatureProperties;
import com.njydsz.common.app.metrics.AppMetrics;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.safe.filter.CachedBodyHttpServletRequestWrapper;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.common.util.url.UrlPathUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * App 端请求签名验证过滤器
 *
 * <p>基于 HMAC-SHA256 算法验证请求签名的完整性、来源合法性和防重放。
 *
 * <p><b>签名算法：</b>
 * <pre>
 * bodyHash = SHA-256(requestBody)              // 请求体的 SHA-256 哈希（Hex），GET 请求为空字符串
 * signData = method + "|" + uri + "|" + timestamp + "|" + nonce + "|" + bodyHash
 * signature = HMAC-SHA256(key = appSecret, data = signData)
 * </pre>
 *
 * <p><b>必需请求头：</b>
 * <ul>
 *   <li>{@code X-App-Sign} - 客户端计算的签名值（Hex 格式）</li>
 *   <li>{@code X-App-Timestamp} - 请求时间戳（毫秒）</li>
 *   <li>{@code X-App-Nonce} - 随机字符串（用于防重放）</li>
 *   <li>{@code X-App-Id} - App 标识（多 App 密钥场景，可选）</li>
 * </ul>
 *
 * <p><b>防重放机制：</b>
 * <ul>
 *   <li>时间戳容差：请求时间戳与服务端时间差超过 {@code timestampTolerance} 则拒绝</li>
 *   <li>Nonce 唯一性：通过 Redis SETNX 原子操作确保同一 Nonce 在 TTL 内不被重复使用</li>
 * </ul>
 *
 * <p><b>路径白名单：</b>
 * 配置 {@code ydsz.app.signature.ignore-urls} 可跳过指定路径的签名验证，
 * 适用于公开接口（登录、注册等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AppSignatureProperties
 * @see CachedBodyHttpServletRequestWrapper
 */
@Slf4j
public class AppSignatureFilter extends OncePerRequestFilter {

    /** 签名头名称，客户端将计算后的 Hex 字符串写入此头 */
    private static final String HEADER_SIGNATURE = "X-App-Sign";
    /** 时间戳头名称（毫秒） */
    private static final String HEADER_TIMESTAMP = "X-App-Timestamp";
    /** 随机字符串头名称，用于防重放 */
    private static final String HEADER_NONCE = "X-App-Nonce";
    /** Redis Nonce 缓存 Key 前缀 */
    private static final String NONCE_CACHE_PREFIX = "app:signature:nonce:";
    /** SHA-256 算法名称 */
    private static final String SHA_256 = "SHA-256";

    /** 签名配置属性 */
    private final AppSignatureProperties properties;
    /** Redis 模板，用于 Nonce 防重放（可为 null，降级为仅时间戳校验） */
    private final StringRedisTemplate redisTemplate;
    /** App 指标采集器（可为 null） */
    private final AppMetrics appMetrics;

    /**
     * 构造方法
     *
     * @param properties     签名配置属性
     * @param redisTemplate  Redis 模板（可为 null，降级为仅时间戳校验）
     * @param appMetrics     App 指标采集器（可为 null）
     */
    public AppSignatureFilter(AppSignatureProperties properties,
                               StringRedisTemplate redisTemplate,
                               AppMetrics appMetrics) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.appMetrics = appMetrics;
    }

    /**
     * 执行签名校验的核心逻辑
     *
     * <p>校验流程：
     * <ol>
     *   <li>检查路径白名单，命中则跳过签名验证</li>
     *   <li>提取签名相关请求头，缺失则返回 400</li>
     *   <li>解析时间戳并校验是否在容差范围内</li>
     *   <li>通过 Redis SETNX 校验 Nonce 唯一性（防重放）</li>
     *   <li>读取请求体并计算 SHA-256 哈希</li>
     *   <li>按 {@code method|uri|timestamp|nonce|bodyHash} 计算期望签名</li>
     *   <li>使用恒定时间比较客户端签名与服务端计算签名</li>
     * </ol>
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
        long startTime = System.nanoTime();

        // 路径白名单检查
        if (isIgnored(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取签名相关请求头
        String signature = request.getHeader(HEADER_SIGNATURE);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String appId = request.getHeader(properties.getAppIdHeader());

        // 验证必需头
        if (signature == null || timestampStr == null || nonce == null) {
            log.warn("【App签名验证】缺少必需请求头 | uri={}", request.getRequestURI());
            recordMetrics("missing_headers", startTime);
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "缺少签名请求头");
            return;
        }

        // 验证时间戳
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("【App签名验证】时间戳格式错误 | uri={}", request.getRequestURI());
            recordMetrics("invalid_timestamp", startTime);
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "时间戳格式错误");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - timestamp) > properties.getTimestampTolerance()) {
            log.warn("【App签名验证】时间戳过期 | uri={} | tolerance={}ms", request.getRequestURI(), properties.getTimestampTolerance());
            recordMetrics("timestamp_expired", startTime);
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "请求时间戳已过期");
            return;
        }

        // Nonce 防重放（Redis SETNX）
        if (redisTemplate != null) {
            String nonceKey = NONCE_CACHE_PREFIX + nonce;
            Boolean setSuccess = redisTemplate.opsForValue().setIfAbsent(
                    nonceKey, "1", Duration.ofSeconds(properties.getNonceCacheTtl()));
            if (setSuccess == null || !setSuccess) {
                log.warn("【App签名验证】Nonce 重复（疑似重放攻击） | uri={} | nonce={}", request.getRequestURI(), nonce);
                recordMetrics("nonce_replay", startTime);
                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "请求已过期或重复提交");
                return;
            }
        } else {
            log.debug("【App签名验证】RedisTemplate 未注入，Nonce 防重放降级为仅时间戳校验");
        }

        // 读取请求体并计算 SHA-256 哈希
        HttpServletRequest requestToForward = request;
        String bodySha256 = "";
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json") && !"GET".equalsIgnoreCase(request.getMethod())) {
            try {
                byte[] bodyBytes = request.getInputStream().readAllBytes();
                bodySha256 = sha256Hex(bodyBytes);
                requestToForward = new CachedBodyHttpServletRequestWrapper(request, bodyBytes);
            } catch (IOException e) {
                log.warn("【App签名验证】读取请求体失败 | uri={}", request.getRequestURI(), e);
            }
        }

        // 构建签名字符串
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String signData = method + "|" + uri + "|" + timestamp + "|" + nonce + "|" + bodySha256;

        // 查找签名密钥
        String secret = properties.resolveSecret(appId);
        if (secret == null || secret.isBlank()) {
            log.warn("【App签名验证】未找到有效密钥 | uri={} | appId={}", request.getRequestURI(), appId);
            recordMetrics("no_secret", startTime);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "无效的 App 身份");
            return;
        }

        // 计算期望签名
        String expectedSignature = DigestUtils.hmacSha256Hex(signData, secret);

        // 验证签名（时序恒定比较）
        if (!DigestUtils.verifyDigestHex(expectedSignature, signature)) {
            log.warn("【App签名验证】签名不匹配 | uri={}", request.getRequestURI());
            recordMetrics("signature_mismatch", startTime);
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "签名验证失败");
            return;
        }

        log.debug("【App签名验证】通过 | uri={}", request.getRequestURI());
        recordMetrics("success", startTime);
        filterChain.doFilter(requestToForward, response);
    }

    /**
     * 检查请求路径是否在白名单中
     *
     * @param request HTTP 请求
     * @return true 表示跳过签名验证
     */
    private boolean isIgnored(HttpServletRequest request) {
        List<String> ignoreUrls = properties.getIgnoreUrls();
        if (ignoreUrls == null || ignoreUrls.isEmpty()) {
            return false;
        }
        return UrlPathUtils.isIgnoreUrl(ignoreUrls, request.getServletPath());
    }

    /**
     * 写入统一的 JSON 错误响应
     *
     * @param response    HTTP 响应
     * @param statusCode  HTTP 状态码
     * @param message     错误消息
     * @throws IOException 写入响应时发生 IO 异常
     */
    private void writeErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<Void> errorResponse = BaseResponse.error("A04010", message);
        response.getWriter().write(YdszJson.toJson(errorResponse));
        response.getWriter().flush();
    }

    /**
     * 计算字节数组的 SHA-256 哈希（十六进制输出）
     *
     * @param data 原始字节数组
     * @return 十六进制哈希字符串，空数组返回空字符串
     */
    private static String sha256Hex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 记录签名验证指标
     *
     * @param result    验证结果标签
     * @param startTime 开始时间（纳秒）
     */
    private void recordMetrics(String result, long startTime) {
        if (appMetrics != null) {
            long durationNanos = System.nanoTime() - startTime;
            appMetrics.recordSignatureVerify(result, durationNanos);
        }
    }
}
