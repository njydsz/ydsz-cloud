package com.njydsz.userinfo.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.safe.config.ApiSignatureProperties;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;

/**
 * API 参数签名校验过滤器（P0-7）。
 *
 * <p>对 {@code /api/internal/**} 路径的请求进行 HMAC-SHA256 参数签名校验，
 * 实现零信任内部调用。校验流程：
 *
 * <ol>
 *   <li>检查是否在排除路径列表中（跳过签名校验）
 *   <li>读取 X-Timestamp、X-Nonce、X-Signature 请求头
 *   <li>检查时间戳是否在有效期内（防过期请求重放）
 *   <li>使用 {@link NonceCache#verifyAndConsume} 检查 nonce 是否已使用（防请求重放）
 *   <li>拼接签名字符串并计算签名，与请求头中的签名比对
 * </ol>
 *
 * <p>本类继承 common-safe 的 {@link com.njydsz.common.safe.filter.ApiSignatureFilter}，
 * 复用其基础设施（{@link ApiSignatureProperties} 配置绑定 + {@link NonceCache} 防重放），
 * 同时保持 userinfo 模块特有的行为：仅拦截 {@code /api/internal/**} 路径、
 * 返回 {@link UserInfoExceptionCode} 错误码格式、执行顺序 {@code HIGHEST_PRECEDENCE + 30}。
 *
 * <p><b>优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 30，在 TraceIdFilter 之后、
 * MetricsFilter 之前执行。确保日志可以记录签名校验失败的事件，同时不影响 traceId 的传递。
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>签名比较使用 {@link DigestUtils#constantTimeEquals} 防时序攻击
 *   <li>nonce 缓存 TTL 由 {@link ApiSignatureProperties#getNonceExpireSeconds()} 控制
 *   <li>校验失败返回 401 不暴露具体原因细节（由日志记录详细信息）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ApiSignatureProperties 签名配置（common-safe）
 * @see NonceCache 防重放 Nonce 缓存（common-safe）
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ApiSignatureFilter extends com.njydsz.common.safe.filter.ApiSignatureFilter {

  /** 内部 API 路径前缀 */
  private static final String INTERNAL_PATH_PREFIX = "/api/internal";

  /** 请求体缓存大小（8KB），用于签名校验时缓存请求体 */
  private static final int REQUEST_BODY_CACHE_SIZE = 8192;

  /** 签名字段分隔符 */
  private static final char FIELD_SEPARATOR = '\n';

  /**
   * 签名校验通过的请求属性名。
   *
   * <p>签名过滤器校验通过后设置此属性，{@link
   * com.njydsz.userinfo.web.aspect.RequireInternalAspect} 检测到后跳过 IP 标记头校验。
   */
  public static final String SIGNATURE_VERIFIED_ATTR =
      ApiSignatureFilter.class.getName() + ".SIGNATURE_VERIFIED";

  private final ApiSignatureProperties properties;
  private final NonceCache nonceCache;

  /**
   * 构造 API 参数签名校验过滤器。
   *
   * @param properties 签名配置属性（来自 common-safe）
   * @param nonceCache 防重放 Nonce 缓存（来自 common-safe）
   */
  public ApiSignatureFilter(ApiSignatureProperties properties, NonceCache nonceCache) {
    super(properties, nonceCache, null);
    this.properties = properties;
    this.nonceCache = nonceCache;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 仅对内部 API 路径进行签名校验
    if (!isInternalPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 当签名功能整体关闭时，放行所有请求
    if (!properties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    // 排除路径直接放行
    if (isExcludedPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 包装请求以支持多次读取 body
    ContentCachingRequestWrapper wrappedRequest =
        new ContentCachingRequestWrapper(request, REQUEST_BODY_CACHE_SIZE);

    try {
      if (verifySignature(wrappedRequest, response)) {
        // 校验通过，设置属性通知 RequireInternalAspect 放行 IP 标记头校验，再放行请求
        wrappedRequest.setAttribute(SIGNATURE_VERIFIED_ATTR, Boolean.TRUE);
        filterChain.doFilter(wrappedRequest, response);
      }
    } catch (Exception e) {
      log.error("API signature: unexpected error during verification, uri={}",
          wrappedRequest.getRequestURI(), e);
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
    }
  }

  /**
   * 执行签名校验（参数检查 / 时间戳 / nonce / 签名比对）。
   *
   * @param wrappedRequest 已包装的请求（可重复读 body）
   * @param response HTTP 响应
   * @return true 表示校验通过
   */
  private boolean verifySignature(
      ContentCachingRequestWrapper wrappedRequest, HttpServletResponse response) {
    // 1. 读取签名相关请求头
    String timestampStr = wrappedRequest.getHeader(properties.getHeaderTimestamp());
    String nonce = wrappedRequest.getHeader(properties.getHeaderNonce());
    String signature = wrappedRequest.getHeader(properties.getHeaderSignature());

    // 2. 校验必要参数是否存在
    if (!StringUtils.hasText(timestampStr) || !StringUtils.hasText(nonce)
        || !StringUtils.hasText(signature)) {
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_REQUIRED);
      return false;
    }

    // 3. 解析时间戳并校验有效期
    long timestamp = parseTimestamp(timestampStr, wrappedRequest, response);
    if (timestamp < 0) {
      return false;
    }
    if (isExpired(timestamp)) {
      log.warn("API signature: expired signature, uri={}, timestamp={}, tolerance={}",
          wrappedRequest.getRequestURI(), timestamp,
          properties.getTimestampToleranceSeconds());
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_EXPIRED);
      return false;
    }

    // 4. nonce 防重放校验
    if (!nonceCache.verifyAndConsume(nonce)) {
      log.warn("API signature: nonce reused (possible replay attack), uri={}, nonce={}",
          wrappedRequest.getRequestURI(), nonce);
      writeUnauthorized(response, UserInfoExceptionCode.NONCE_REUSED);
      return false;
    }

    // 5. 计算并比对签名
    String method = wrappedRequest.getMethod();
    String path = wrappedRequest.getRequestURI();
    String query = wrappedRequest.getQueryString();
    String body = getRequestBody(wrappedRequest);

    String signContent = buildSignContent(method, path, query, body, timestamp, nonce);
    if (signContent == null) {
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
      return false;
    }

    String expected = DigestUtils.hmacSha256Base64(signContent, properties.getAppSecret());
    if (!DigestUtils.constantTimeEquals(expected, signature)) {
      log.warn("API signature: invalid signature, uri={}, method={}, nonce={}",
          wrappedRequest.getRequestURI(), method, nonce);
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
      return false;
    }
    return true;
  }

  /**
   * 判断签名时间戳是否已过期。
   *
   * @param timestamp 签名时间戳（毫秒 Unix epoch）
   * @return true 表示已过期
   */
  private boolean isExpired(long timestamp) {
    long now = System.currentTimeMillis();
    long toleranceMillis = properties.getTimestampToleranceSeconds() * 1000L;
    return Math.abs(now - timestamp) > toleranceMillis;
  }

  /**
   * 构建签名字符串。
   *
   * @param method HTTP 方法
   * @param path 请求路径
   * @param query 查询字符串
   * @param body 请求体
   * @param timestamp 签名时间戳
   * @param nonce 一次性随机串
   * @return 签名字符串；method/path/nonce 任一为空时返回 null
   */
  private String buildSignContent(
      String method, String path, String query, String body, long timestamp, String nonce) {
    if (method == null || method.isBlank()) {
      return null;
    }
    if (path == null || path.isBlank()) {
      return null;
    }
    if (nonce == null || nonce.isBlank()) {
      return null;
    }
    String safeQuery = query != null ? query : "";
    String safeBody = body != null ? body : "";
    return method + FIELD_SEPARATOR
        + path + FIELD_SEPARATOR
        + safeQuery + FIELD_SEPARATOR
        + safeBody + FIELD_SEPARATOR
        + timestamp + FIELD_SEPARATOR
        + nonce;
  }

  /**
   * 解析时间戳字符串，非法格式直接拒绝。
   *
   * @param timestampStr 时间戳字符串
   * @param wrappedRequest 请求对象（日志用）
   * @param response HTTP 响应
   * @return 解析后的时间戳；非法格式返回 -1
   */
  private long parseTimestamp(
      String timestampStr,
      ContentCachingRequestWrapper wrappedRequest,
      HttpServletResponse response) {
    try {
      return Long.parseLong(timestampStr);
    } catch (NumberFormatException e) {
      log.warn("API signature: invalid timestamp format, uri={}, timestamp={}",
          wrappedRequest.getRequestURI(), timestampStr);
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
      return -1;
    }
  }

  /**
   * 判断是否为内部 API 路径。
   *
   * @param request HTTP 请求
   * @return true 表示 {@code /api/internal/**} 路径
   */
  private boolean isInternalPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith(INTERNAL_PATH_PREFIX);
  }

  /**
   * 判断当前请求路径是否在排除列表中。
   *
   * <p>使用 Ant 风格路径匹配（common-safe 的 {@code UrlPathUtils}），
   * 行为与原 userinfo 实现（startsWith）兼容。
   *
   * @param request HTTP 请求
   * @return true 表示应跳过签名校验
   */
  private boolean isExcludedPath(HttpServletRequest request) {
    List<String> excludes = properties.getExcludes();
    if (excludes == null || excludes.isEmpty()) {
      return false;
    }
    String uri = request.getRequestURI();
    for (String pattern : excludes) {
      if (uri.startsWith(pattern)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 获取请求体字符串。
   *
   * <p>从 {@link ContentCachingRequestWrapper} 中读取缓存的请求体内容。
   * 若 body 为空或读取失败，返回空字符串（非 null）。
   *
   * @param request 包装后的 HTTP 请求
   * @return 请求体字符串；无 body 或读取失败时返回空字符串
   */
  private String getRequestBody(ContentCachingRequestWrapper request) {
    try {
      byte[] content = request.getContentAsByteArray();
      if (content == null || content.length == 0) {
        return "";
      }
      return new String(content, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("API signature: failed to read request body, error={}", e.getMessage());
      return "";
    }
  }

  /**
   * 写入 401 未授权响应。
   *
   * <p>返回 JSON 格式的 {@link YdszResponse} 错误响应，不暴露具体校验失败原因。
   *
   * @param response HTTP 响应
   * @param exceptionCode 错误码枚举
   */
  private void writeUnauthorized(HttpServletResponse response,
      UserInfoExceptionCode exceptionCode) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    YdszResponse<Void> body =
        YdszResponse.error(exceptionCode.getCode(), exceptionCode.getMsg());
    try {
      response.getWriter().write(YdszJson.toJson(body));
    } catch (IOException e) {
      log.error("API signature: failed to write error response", e);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isInternalPath(request);
  }
}
