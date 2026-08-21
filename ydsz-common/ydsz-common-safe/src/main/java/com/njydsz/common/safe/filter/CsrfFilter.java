package com.njydsz.common.safe.filter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.common.safe.config.CsrfProperties.CsrfMode;
import com.njydsz.common.safe.config.CsrfProperties;
import com.njydsz.common.safe.csrf.CsrfToken;
import com.njydsz.common.safe.csrf.CsrfTokenRepository;
import com.njydsz.common.util.http.UrlPathUtils;

/**
 * CSRF 防护过滤器
 *
 * <p>防止跨站请求伪造（CSRF）攻击，基于 Token 机制。Synchronizer Token Pattern 是当前最成熟的 CSRF 防御方案（OWASP 推荐）。
 *
 * <p><b>威胁模型：</b>用户已登录目标站点，攻击者通过第三方站点诱导用户浏览器 发送跨域请求（携带目标站点的 Cookie），完成未授权的写操作。
 *
 * <p><b>防护原理：</b>
 *
 * <ul>
 *   <li>服务端生成唯一的 CSRF 令牌并写入 Cookie/Response Header
 *   <li>客户端在请求中携带令牌（Header 或 Parameter）
 *   <li>服务端验证令牌有效性（攻击者无法通过跨域脚本获取 Token）
 *   <li>Token 一次性使用，验证后立即生成新 Token 防重放
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * 1. 前端在页面加载时从 Cookie/Header 获取 CSRF 令牌
 * 2. 发起请求时在 Header 或 Parameter 中携带令牌
 * 3. 服务端自动验证令牌有效性
 * }</pre>
 *
 * <p><b>性能影响：</b>每次非 GET 请求都会调用一次 Redis 校验。建议生产环境 启用 Redis 存储而非内存存储，否则多实例下 Token 不一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CsrfProperties
 * @see CsrfTokenRepository
 */
public class CsrfFilter extends OncePerRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CsrfFilter.class);

  /** 安全随机数生成器（Double Submit 模式使用） */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** CSRF Cookie 名称 */
  private static final String CSRF_TOKEN_COOKIE = "CSRF-TOKEN";

  /** 通配符 Origin 模式编译缓存（allowedOrigin -> 编译后的 Pattern） */
  private final ConcurrentHashMap<String, Pattern> originPatternCache = new ConcurrentHashMap<>();

  /** CSRF 配置属性 */
  private final CsrfProperties properties;

  /** CSRF 令牌存储库（Redis / 内存） */
  private final CsrfTokenRepository tokenRepository;

  /**
   * 构造 CSRF 防护过滤器
   *
   * @param properties CSRF 配置属性
   * @param tokenRepository CSRF 令牌存储库
   */
  public CsrfFilter(CsrfProperties properties, CsrfTokenRepository tokenRepository) {
    this.properties = properties;
    this.tokenRepository = tokenRepository;
  }

  /**
   * 过滤器核心逻辑
   *
   * <ol>
   *   <li>禁用 / 排除路径：直接放行
   *   <li>GET 请求：生成新 Token，写入 Cookie 和 Response Header
   *   <li>HEAD / OPTIONS 请求：放行（不携带业务语义）
   *   <li>其他请求：验证 Token，验证通过后刷新 Token 防重放
   * </ol>
   *
   * @param httpRequest HTTP 请求
   * @param httpResponse HTTP 响应
   * @param chain 过滤器链
   * @throws IOException IO 异常
   * @throws ServletException Servlet 异常
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest httpRequest,
      @NonNull HttpServletResponse httpResponse,
      @NonNull FilterChain chain)
      throws IOException, ServletException {

    if (!properties.isEnabled()) {
      chain.doFilter(httpRequest, httpResponse);
      return;
    }

    if (isExcluded(httpRequest)) {
      chain.doFilter(httpRequest, httpResponse);
      return;
    }

    if (HttpMethod.GET.matches(httpRequest.getMethod())) {
      handleGetRequest(httpRequest, httpResponse, chain);
      return;
    }

    if (HttpMethod.HEAD.matches(httpRequest.getMethod())
        || HttpMethod.OPTIONS.matches(httpRequest.getMethod())) {
      chain.doFilter(httpRequest, httpResponse);
      return;
    }

    // Origin/Referer 校验（第二道防线，在 Token 校验之前执行）
    if (properties.isCheckOrigin() && !validateOrigin(httpRequest)) {
      LOGGER.warn(
          "CSRF Origin 校验失败 | URI: {} | Origin: {} | Referer: {}",
          httpRequest.getRequestURI(),
          httpRequest.getHeader("Origin"),
          httpRequest.getHeader("Referer"));
      httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
      httpResponse.setHeader("X-Content-Type-Options", "nosniff");
      httpResponse.setContentType("application/json;charset=UTF-8");
      httpResponse
          .getWriter()
          .write("{\"code\":403,\"message\":\"Cross-origin request not allowed\"}");
      return;
    }

    if (!validateCsrfToken(httpRequest)) {
      LOGGER.warn("CSRF 验证失败: {}", httpRequest.getRequestURI());
      httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
      httpResponse.setHeader("X-Content-Type-Options", "nosniff");
      httpResponse.setContentType("application/json;charset=UTF-8");
      httpResponse.getWriter().write("{\"code\":403,\"message\":\"CSRF token validation failed\"}");
      return;
    }

    // 验证通过后刷新 CSRF token，防止同一 token 被重放攻击
    String newToken = (String) httpRequest.getAttribute("NEW_CSRF_TOKEN");
    if (newToken != null) {
      Cookie cookie = buildCsrfCookie(newToken, httpRequest);
      httpResponse.addCookie(cookie);
      httpResponse.setHeader(properties.getTokenHeader(), newToken);
    }

    chain.doFilter(httpRequest, httpResponse);
  }

  private void handleGetRequest(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (properties.getMode() == CsrfMode.DOUBLE_SUBMIT) {
      String token = generateRandomToken();
      Cookie cookie = buildCsrfCookie(token, request);
      response.addCookie(cookie);
      response.setHeader(properties.getTokenHeader(), token);
    } else {
      String sessionId = getSessionId(request);
      CsrfToken token = tokenRepository.createToken(sessionId);
      Cookie cookie = buildCsrfCookie(token.getToken(), request);
      response.addCookie(cookie);
      response.setHeader(properties.getTokenHeader(), token.getToken());
    }

    chain.doFilter(request, response);
  }

  /**
   * 构建 CSRF Cookie，统一 Cookie 安全属性配置
   *
   * @param token CSRF 令牌值
   * @param request HTTP 请求（用于动态判断 Secure 标志）
   * @return 配置好安全属性的 Cookie
   */
  private Cookie buildCsrfCookie(String token, HttpServletRequest request) {
    Cookie cookie = new Cookie(CSRF_TOKEN_COOKIE, token);
    cookie.setPath("/");
    // 前端需要读取 CSRF Token，不能设置 HttpOnly
    cookie.setHttpOnly(false);
    // Secure 标志：配置优先，未配置时根据请求协议动态决定
    Boolean cookieSecure = properties.getCookieSecure();
    if (cookieSecure != null) {
      cookie.setSecure(cookieSecure);
    } else {
      cookie.setSecure(request.isSecure());
    }
    cookie.setMaxAge((int) properties.getExpirationSeconds());
    setSameSiteAttribute(cookie, properties.getSameSite());
    return cookie;
  }

  /**
   * 校验请求来源（Origin/Referer），拒绝跨站请求
   *
   * <p>校验逻辑：
   *
   * <ol>
   *   <li>优先校验 Origin 头，为空时回退到 Referer 头
   *   <li>如果配置了 allowedOrigins，校验来源是否在白名单中
   *   <li>如果未配置 allowedOrigins，校验来源是否与请求 Host 同源
   * </ol>
   *
   * @param request HTTP 请求
   * @return 同源或在白名单中返回 true，跨站返回 false
   */
  private boolean validateOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    // Origin 为空时回退到 Referer
    if (origin == null || origin.isEmpty()) {
      String referer = request.getHeader("Referer");
      if (referer == null || referer.isEmpty()) {
        // 无 Origin 和 Referer，可能是同源请求或非浏览器客户端，放行
        return true;
      }
      origin = extractOrigin(referer);
    }

    List<String> allowedOrigins = properties.getAllowedOrigins();
    if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
      // 配置了白名单，校验是否匹配
      for (String allowed : allowedOrigins) {
        if (matchesOrigin(origin, allowed)) {
          return true;
        }
      }
      return false;
    }

    // 未配置白名单，校验是否同源（Origin 的 host:port 与请求 Host 一致）
    String requestHost = request.getServerName() + ":" + request.getServerPort();
    return isSameOrigin(
        origin, requestHost, request.getScheme(), request.getServerName(), request.getServerPort());
  }

   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
   * @param referer referer 参数
  /** 从 Referer URL 中提取 Origin（scheme://host:port） */
  private String extractOrigin(String referer) {
    int pathIdx = referer.indexOf('/', referer.indexOf("://") + 3);
    return pathIdx > 0 ? referer.substring(0, pathIdx) : referer;
  }

  /**
   * 校验 Origin 是否匹配允许的模式（支持通配符）
   *
   * <p><b>安全约束：</b>
   *
   * <ul>
   *   <li>正则使用 {@code ^...$} 锚点，避免部分匹配导致绕过 （如旧实现 {@code *.example.com} 可被 {@code
   *       evil.example.com.attacker.com} 绕过）；
   *   <li>通配符 {@code *} 仅匹配单个域名标签（{@code [^.]+}），不跨越点号， 防止 {@code *} 匹配整段子域名路径；
   *   <li>正则编译结果缓存到 {@link #originPatternCache}，避免每次请求重复编译。
   * </ul>
   *
   * <p><b>合法通配符示例：</b>
   *
   * <ul>
   *   <li>{@code https://*.example.com} → 匹配 {@code https://sub.example.com}， 不匹配 {@code
   *       https://a.b.example.com}（{@code *} 不跨越点号）；
   *   <li>{@code https://app-*.example.com} → 匹配 {@code https://app-v2.example.com}。
   * </ul>
   *
   * @param origin 请求 Origin（如 {@code https://sub.example.com}）
   * @param allowed 允许的 Origin 模式（如 {@code https://*.example.com}）
   * @return true 表示匹配
   */
  private boolean matchesOrigin(String origin, String allowed) {
    if (!allowed.contains("*")) {
      return origin.equals(allowed);
    }
    Pattern pattern = originPatternCache.computeIfAbsent(allowed, this::compileOriginPattern);
    return pattern.matcher(origin).matches();
  }

  /**
   * 将通配符 Origin 模式编译为安全的正则 Pattern
   *
   * <p>编译规则：
   *
   * <ul>
   *   <li>{@code *} → {@code [^.]+}（匹配单个域名标签，不跨越点号）；
   *   <li>其他正则元字符（{@code . ? + $ 等}）→ 反斜杠转义；
   *   <li>最终包裹 {@code ^...$} 锚点，确保全字符串匹配。
   * </ul>
   *
   * @param allowed 允许的 Origin 模式
   * @return 编译后的 Pattern（已加锚点，{@code *} 转为 {@code [^.]+}）
   */
  private Pattern compileOriginPattern(String allowed) {
    StringBuilder regex = new StringBuilder(allowed.length() + 16);
    for (int i = 0; i < allowed.length(); i++) {
      char ch = allowed.charAt(i);
      if (ch == '*') {
        regex.append("[^.]+");
      } else if ("\\.[]{}()+-?^$|".indexOf(ch) >= 0) {
        regex.append('\\').append(ch);
      } else {
        regex.append(ch);
      }
    }
    return Pattern.compile("^" + regex + "$");
  }

  /** 校验是否同源 */
  private boolean isSameOrigin(
      String origin, String requestHost, String scheme, String serverName, int serverPort) {
    // 解析 Origin：scheme://host:port
    try {
      URI originUri = new URI(origin);
      String originHost = originUri.getHost();
      int originPort = originUri.getPort();
      if (originPort == -1) {
        // 默认端口处理
        if ("https".equals(originUri.getScheme())) {
          originPort = 443;
        } else if ("http".equals(originUri.getScheme())) {
          originPort = 80;
        }
      }
      return originUri.getScheme().equalsIgnoreCase(scheme)
          && originHost != null
          && originHost.equalsIgnoreCase(serverName)
          && originPort == serverPort;
    } catch (URISyntaxException e) {
      LOGGER.debug("Origin 解析失败: {}", origin);
      return false;
    }
  }

  private boolean validateCsrfToken(HttpServletRequest request) {
    if (properties.getMode() == CsrfMode.DOUBLE_SUBMIT) {
      return validateDoubleSubmitToken(request);
    }
    return validateSynchronizerToken(request);
  }

  /** Double Submit Cookie 模式验证：比对 Cookie 中的 Token 与 Header/Parameter 中的 Token */
  private boolean validateDoubleSubmitToken(HttpServletRequest request) {
    String cookieToken = null;
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (CSRF_TOKEN_COOKIE.equals(cookie.getName())) {
          cookieToken = cookie.getValue();
          break;
        }
      }
    }

    String headerToken = request.getHeader(properties.getTokenHeader());
    String paramToken = request.getParameter(properties.getTokenParameter());
    String submittedToken = headerToken != null ? headerToken : paramToken;

    if (cookieToken == null
        || cookieToken.isEmpty()
        || submittedToken == null
        || submittedToken.isEmpty()) {
      LOGGER.debug(
          "CSRF Double Submit: token missing | cookie={}, header={}",
          cookieToken != null ? "present" : "absent",
          headerToken != null ? "present" : "absent");
      return false;
    }

    return constantTimeEquals(cookieToken, submittedToken);
  }

  /** Synchronizer Token Pattern 验证：服务端存储校验 */
  private boolean validateSynchronizerToken(HttpServletRequest request) {
    String sessionId = getSessionId(request);
    if (sessionId == null) {
      return false;
    }

    String tokenFromHeader = request.getHeader(properties.getTokenHeader());
    String tokenFromParameter = request.getParameter(properties.getTokenParameter());

    String token = tokenFromHeader != null ? tokenFromHeader : tokenFromParameter;

    if (token == null || token.isEmpty()) {
      LOGGER.debug("CSRF token not found in request");
      return false;
    }

    boolean valid = tokenRepository.validateToken(token, sessionId);
    if (valid) {
      CsrfToken newToken = tokenRepository.createToken(sessionId);
      request.setAttribute("NEW_CSRF_TOKEN", newToken.getToken());
    }
    return valid;
  }

  /** 生成随机 CSRF Token（Double Submit 模式使用） */
  private String generateRandomToken() {
    byte[] randomBytes = new byte[32];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /** 常量时间比较，防止时序攻击 */
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

  private String getSessionId(HttpServletRequest request) {
    String sessionIdHeader = properties.getSessionIdHeader();
    if (sessionIdHeader != null && !sessionIdHeader.isEmpty()) {
      String sessionId = request.getHeader(sessionIdHeader);
      if (sessionId != null && !sessionId.isEmpty()) {
        return sessionId;
      }
    }

    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if ("JSESSIONID".equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }

    return null;
  }

  private boolean isExcluded(HttpServletRequest request) {
    List<String> excludes = properties.getExcludes();
    if (excludes == null || excludes.isEmpty()) {
      return false;
    }
    String servletPath = request.getServletPath();
    return UrlPathUtils.matchAny(excludes, servletPath);
  }

  private void setSameSiteAttribute(Cookie cookie, String value) {
    try {
      cookie.setAttribute("SameSite", value);
    } catch (NoSuchMethodError e) {
      // Servlet 5.0 以下版本不支持 setAttribute，忽略
    }
  }
}
