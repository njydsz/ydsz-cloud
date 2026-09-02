package com.njydsz.common.safe.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.safe.alert.SafeAlertProperties;
import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventPublisher;
import com.njydsz.common.safe.alert.SecurityEventType;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.safe.xss.EscapeUtils;
import com.njydsz.common.util.http.UrlPathUtils;

/**
 * XSS 安全防护过滤器
 *
 * <p>全局 HTTP 请求参数与 JSON 请求体的 XSS 攻击过滤。 基于 Spring {@link OncePerRequestFilter} 实现，在请求进入 Controller
 * 之前完成参数清洗。
 *
 * <p><b>威胁模型：</b>攻击者通过查询参数、表单字段、JSON Body 注入 JavaScript / HTML 片段， 实现 cookie 窃取、钓鱼、UI 伪装、键盘记录等 XSS
 * 攻击。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li>全局过滤：一次配置，全局生效
 *   <li>智能排除：支持 Ant 风格路径匹配，排除无需过滤的端点
 *   <li>JSON 支持：可处理 JSON 请求体的 XSS 攻击
 *   <li>OncePerRequest：基于 Spring 过滤器，确保每次请求只执行一次
 *   <li>安全告警：检测到 XSS 攻击时发布安全事件
 * </ul>
 *
 * <p><b>过滤范围：</b>
 *
 * <ul>
 *   <li>排除路径列表中的端点不过滤
 *   <li>其他所有请求的参数和 JSON Body 都会经过 XSS 过滤
 * </ul>
 *
 * <p><b>性能影响：</b>每次请求都会执行参数遍历和字符串替换，对高 QPS 接口需评估 性能开销。JSON Body 在内存中缓存（10MB 上限），不应作为大文件上传接口的兜底。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see XssHttpServletRequestWrapper
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class XssFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(XssFilter.class);

  /** 默认 XSS 排除路径列表 */
  private static final List<String> DEFAULT_EXCLUDES = new ArrayList<>(4);

  static {
    DEFAULT_EXCLUDES.add("/error");
    DEFAULT_EXCLUDES.add("/favicon.ico");
    DEFAULT_EXCLUDES.add("/actuator/**");
  }

  /** 排除路径列表（Ant 风格） */
  private final List<String> excludes;

  /** 安全事件发布器（可为 null） */
  private final SecurityEventPublisher eventPublisher;

  /** 安全告警配置（可为 null） */
  private final SafeAlertProperties alertProperties;

  /** 默认构造器：使用默认排除路径，不发布安全事件 */
  public XssFilter() {
    this.excludes = new ArrayList<>(DEFAULT_EXCLUDES);
    this.eventPublisher = null;
    this.alertProperties = null;
  }

  /**
   * 自定义排除路径构造器
   *
   * @param excludes 排除路径列表（null 时使用默认）
   */
  public XssFilter(List<String> excludes) {
    this.excludes = excludes == null ? new ArrayList<>(16) : new ArrayList<>(excludes);
    if (this.excludes.isEmpty()) {
      this.excludes.addAll(DEFAULT_EXCLUDES);
    }
    this.eventPublisher = null;
    this.alertProperties = null;
  }

  /**
   * 完整构造器
   *
   * @param excludes 排除路径列表
   * @param eventPublisher 安全事件发布器（可为 null）
   * @param alertProperties 安全告警配置（可为 null）
   */
  public XssFilter(
      List<String> excludes,
      SecurityEventPublisher eventPublisher,
      SafeAlertProperties alertProperties) {
    this.excludes = excludes == null ? new ArrayList<>(16) : new ArrayList<>(excludes);
    if (this.excludes.isEmpty()) {
      this.excludes.addAll(DEFAULT_EXCLUDES);
    }
    this.eventPublisher = eventPublisher;
    this.alertProperties = alertProperties;
  }

  /**
   * 过滤器核心逻辑
   *
   * <ol>
   *   <li>排除路径直接放行
   *   <li>JSON 请求体执行 XSS 攻击检测，命中时发布安全事件
   *   <li>非 JSON 请求遍历参数做 XSS 检测
   *   <li>使用 {@link XssHttpServletRequestWrapper} 包装请求，自动清洗参数
   * </ol>
   *
   * <p><b>请求体复用：</b>若请求已被 {@link SafeRequestBodyCacheFilter} 包装为 {@link
   * CachedBodyHttpServletRequestWrapper}，则直接复用已缓存的请求体， 不再重复读取 InputStream。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param filterChain 过滤器链
   * @throws IOException IO 异常
   * @throws ServletException Servlet 异常
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws IOException, ServletException {
    if (isExcluded(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 检测 JSON 请求体中的 XSS 攻击
    CachedRequestBody cachedBody = null;
    if (isJsonRequest(request)) {
      byte[] bodyBytes = extractBodyBytes(request);
      if (bodyBytes != null) {
        cachedBody = new CachedRequestBody(bodyBytes);
        if (cachedBody.hasText() && EscapeUtils.containsXSS(cachedBody.getText())) {
          publishEvent(request, cachedBody.getText());
        }
      }
    } else {
      // 非 JSON 请求只检测参数
      detectAndPublishXssEvent(request);
    }

    XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(request, cachedBody);
    filterChain.doFilter(xssRequest, response);
  }

  /**
   * 提取请求体字节数组。
   *
   * <p>优先从 {@link CachedBodyHttpServletRequestWrapper} 获取已缓存的请求体， 避免重复读取 InputStream；若请求未被包装，则直接读取
   * InputStream。
   *
   * @param request HTTP 请求
   * @return 请求体字节数组；若无 body 或读取失败返回 null
   */
  private byte[] extractBodyBytes(HttpServletRequest request) {
    if (request instanceof CachedBodyHttpServletRequestWrapper cachedWrapper) {
      return cachedWrapper.getCachedBody();
    }
    try {
      return request.getInputStream().readAllBytes();
    } catch (IOException e) {
      LOG.warn("XSS 过滤器读取请求体失败 | URI: {} | 消息: {}", request.getRequestURI(), e.getMessage());
      return null;
    }
  }

  /**
   * 检测非 JSON 请求参数中的 XSS 攻击，并发布安全事件
   *
   * <p>仅扫描非 JSON 请求。JSON 请求体的检测在 {@link #doFilterInternal} 中完成。
   */
  private void detectAndPublishXssEvent(HttpServletRequest request) {
    if (eventPublisher == null || alertProperties == null || !alertProperties.isEnabled()) {
      return;
    }

    // 检查请求参数
    String[] paramNames = request.getParameterMap().keySet().toArray(new String[0]);
    for (String name : paramNames) {
      String[] values = request.getParameterValues(name);
      if (values != null) {
        for (String value : values) {
          if (EscapeUtils.containsXSS(value)) {
            publishEvent(request, value);
            return;
          }
        }
      }
    }
  }

  /**
   * 发布 XSS 攻击安全事件
   *
   * @param request HTTP 请求
   * @param payload 触发检测的攻击载荷
   */
  private void publishEvent(HttpServletRequest request, String payload) {
    SecurityEvent event =
        new SecurityEvent(
            SecurityEventType.XSS_ATTACK,
            request.getRequestURI(),
            ClientIpResolver.getClientIp(request),
            request.getHeader("User-Agent"),
            payload,
            SecurityEvent.Severity.HIGH);
    eventPublisher.publish(event);
  }

  /**
   * 判断请求是否为 JSON 请求
   *
   * @param request HTTP 请求
   * @return Content-Type 包含 {@code application/json} 时返回 true
   */
  private boolean isJsonRequest(HttpServletRequest request) {
    String contentType = request.getHeader("Content-Type");
    return StringUtils.hasText(contentType)
        && contentType.toLowerCase().contains("application/json");
  }

  /** 缓存的请求体，用于支持 XSS 检测和后续 Wrapper 重复读取 */
  static class CachedRequestBody {
    /** 原始字节数组 */
    private final byte[] bytes;

    /** 原始字符串（UTF-8 编码） */
    private final String text;

    CachedRequestBody(byte[] bytes) {
      this.bytes = bytes;
      this.text = new String(bytes, StandardCharsets.UTF_8);
    }

    byte[] getBytes() {
      return bytes;
    }

    String getText() {
      return text;
    }

    boolean hasText() {
      return StringUtils.hasText(text);
    }
  }

  /**
   * 判断请求路径是否需要排除 XSS 过滤
   *
   * @param request HTTP 请求
   * @return 需要排除返回 true
   */
  private boolean isExcluded(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    return UrlPathUtils.matchAny(excludes, servletPath);
  }
}
