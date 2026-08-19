package com.njydsz.userinfo.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.ApiSignatureUtil;
import com.njydsz.userinfo.server.config.ApiSignatureProperties;

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
 *   <li>使用 SETNX 检查 nonce 是否已使用（防请求重放）
 *   <li>拼接签名字符串并计算签名，与请求头中的签名比对
 * </ol>
 *
 * <p><b>优先级：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 30，在 TraceIdFilter 之后、
 * MetricsFilter 之前执行。确保日志可以记录签名校验失败的事件，同时不影响 traceId 的传递。
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>签名比较使用 {@link java.security.MessageDigest#isEqual} 防时序攻击
 *   <li>nonce 缓存 TTL 为签名 TTL 的 2 倍，确保窗口期内有效请求的 nonce 不被清除
 *   <li>校验失败返回 401 不暴露具体原因细节（由日志记录详细信息）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see ApiSignatureProperties 签名配置
 * @see ApiSignatureUtil 签名工具类
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class ApiSignatureFilter extends OncePerRequestFilter {

  /** 内部 API 路径前缀 */
  private static final String INTERNAL_PATH_PREFIX = "/api/internal";

  /**
   * 签名校验通过的请求属性名。
   *
   * <p>签名过滤器校验通过后设置此属性，{@link
   * com.njydsz.userinfo.web.aspect.RequireInternalAspect} 检测到后跳过 IP 标记头校验。
   */
  public static final String SIGNATURE_VERIFIED_ATTR =
      ApiSignatureFilter.class.getName() + ".SIGNATURE_VERIFIED";

  /** Nonce Redis Key 前缀 */
  private static final String NONCE_KEY_PREFIX = "userinfo:api-signature:nonce:";

  /** nonce 缓存 TTL 倍率（相对于签名 TTL） */
  private static final int NONCE_TTL_MULTIPLIER = 2;

  private final ApiSignatureProperties properties;
  private final RedisStringOps redisStringOps;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 仅对内部 API 路径进行签名校验
    if (!isInternalPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 排除路径直接放行
    if (isExcludedPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 包装请求以支持多次读取 body
    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024);

    try {
      // 1. 读取签名相关请求头
      String timestampStr = wrappedRequest.getHeader(properties.getHeaderTimestamp());
      String nonce = wrappedRequest.getHeader(properties.getHeaderNonce());
      String signature = wrappedRequest.getHeader(properties.getHeaderSignature());

      // 2. 校验必要参数是否存在
      if (!StringUtils.hasText(timestampStr) || !StringUtils.hasText(nonce)
          || !StringUtils.hasText(signature)) {
        writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_REQUIRED);
        return;
      }

      // 3. 解析时间戳并校验有效期
      long timestamp;
      try {
        timestamp = Long.parseLong(timestampStr);
      } catch (NumberFormatException e) {
        log.warn("API signature: invalid timestamp format, uri={}, timestamp={}",
            wrappedRequest.getRequestURI(), timestampStr);
        writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
        return;
      }

      if (ApiSignatureUtil.isExpired(timestamp, properties.getTtlMillis())) {
        log.warn("API signature: expired signature, uri={}, timestamp={}, ttl={}",
            wrappedRequest.getRequestURI(), timestamp, properties.getTtlMillis());
        writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_EXPIRED);
        return;
      }

      // 4. nonce 防重放校验（SETNX）
      if (!tryAcquireNonce(nonce, timestamp)) {
        log.warn("API signature: nonce reused (possible replay attack), uri={}, nonce={}",
            wrappedRequest.getRequestURI(), nonce);
        writeUnauthorized(response, UserInfoExceptionCode.NONCE_REUSED);
        return;
      }

      // 5. 计算并比对签名
      String method = wrappedRequest.getMethod();
      String path = wrappedRequest.getRequestURI();
      String query = wrappedRequest.getQueryString();
      String body = getRequestBody(wrappedRequest);

      boolean valid =
          ApiSignatureUtil.verify(
              signature, method, path, query, body, timestamp, nonce, properties.getSecret());

      if (!valid) {
        log.warn("API signature: invalid signature, uri={}, method={}, nonce={}",
            wrappedRequest.getRequestURI(), method, nonce);
        writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
        return;
      }

      // 校验通过，设置属性通知 RequireInternalAspect 放行 IP 标记头校验，再放行请求
      wrappedRequest.setAttribute(SIGNATURE_VERIFIED_ATTR, Boolean.TRUE);
      filterChain.doFilter(wrappedRequest, response);
    } catch (Exception e) {
      log.error("API signature: unexpected error during verification, uri={}",
          wrappedRequest.getRequestURI(), e);
      writeUnauthorized(response, UserInfoExceptionCode.SIGNATURE_INVALID);
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
   * @param request HTTP 请求
   * @return true 表示应跳过签名校验
   */
  private boolean isExcludedPath(HttpServletRequest request) {
    List<String> excludePaths = properties.getExcludePaths();
    if (excludePaths == null || excludePaths.isEmpty()) {
      return false;
    }
    String uri = request.getRequestURI();
    for (String pattern : excludePaths) {
      if (uri.startsWith(pattern)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 尝试占用 nonce（SETNX + TTL）。
   *
   * <p>使用 Redis SETNX 实现 nonce 的一次性语义：若 key 已存在则说明 nonce 已被使用，
   * 防止请求重放。TTL 设为签名有效期的 2 倍，确保窗口期内的请求 nonce 唯一性。
   *
   * @param nonce     一次性随机字符串
   * @param timestamp 签名时间戳（用于计算 Redis TTL）
   * @return true 表示 nonce 占用成功（首次使用），false 表示已被使用
   */
  private boolean tryAcquireNonce(String nonce, long timestamp) {
    try {
      String key = NONCE_KEY_PREFIX + nonce;
      // TTL = 签名 TTL * 2，转换为秒并向上取整
      long ttlSeconds = (properties.getTtlMillis() * NONCE_TTL_MULTIPLIER) / 1000L;
      if (ttlSeconds < 1L) {
        ttlSeconds = 1L;
      }
      Boolean acquired = redisStringOps.setIfAbsent(key, timestamp, ttlSeconds);
      return Boolean.TRUE.equals(acquired);
    } catch (Exception e) {
      log.warn("API signature: Redis error during nonce check, nonce={}, error={}",
          nonce, e.getMessage());
      // Redis 异常时拒绝请求（fail-closed 策略）
      return false;
    }
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
   * @param response     HTTP 响应
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
    // 当签名功能整体关闭时，放行所有请求
    return !properties.isEnabled();
  }
}
