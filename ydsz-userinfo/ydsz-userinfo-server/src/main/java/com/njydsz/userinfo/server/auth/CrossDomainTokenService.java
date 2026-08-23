package com.njydsz.userinfo.server.auth;

import java.util.List;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.config.CrossDomainSsoProperties;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 跨域 Token 服务。
 *
 * <p>封装微前端架构下跨域 Token 传递的核心能力：跨域 Cookie 注入/读取、postMessage Token 提取、
 * 可信域校验、CORS 响应头添加。与 {@link SessionManager} 协同，为 {@link CrossDomainSsoFilter}
 * 和 {@code TokenExchangeController} 提供底层支撑。
 *
 * <p><b>跨域 Token 传递方案：</b>
 *
 * <ol>
 *   <li><b>Cookie 共享：</b>登录成功后设置 Domain 为父域名的 Cookie（{@code SameSite=None; Secure=true}），
 *       子域浏览器自动携带</li>
 *   <li><b>postMessage：</b>主应用通过 {@code window.postMessage()} 将 Token 传递给跨域子 iframe/弹窗</li>
 *   <li><b>令牌交换：</b>子应用用父域 Cookie 中的 Token 换取本域独立 Session</li>
 * </ol>
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>CORS 响应头必须验证 Origin 白名单，禁止直接返回 {@code "*"}</li>
 *   <li>Cookie 的 SameSite 必须为 None 且 Secure 必须为 true</li>
 *   <li>令牌交换需校验请求来源 Origin 白名单</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CrossDomainSsoProperties 跨域 SSO 配置
 * @see CrossDomainSsoFilter 跨域 SSO 过滤器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossDomainTokenService {
  /** URL scheme 分隔符长度（"://"） */
  private static final int URL_SCHEME_SEPARATOR_LENGTH = 3;


  /** postMessage 回调中 Token 参数的请求参数名 */
  private static final String POST_MESSAGE_TOKEN_PARAM = "sso_token";

  private final CrossDomainSsoProperties ssoProperties;
  private final UserInfoProperties userInfoProperties;

  /**
   * 注入跨域共享 Cookie。
   *
   * <p>在响应中设置 Domain 为父域名的 Cookie，使 Token 对父域名下所有子域可见。
   * Cookie 属性：{@code SameSite=None}、{@code Secure=true}、{@code Path=/}、HttpOnly=false
   * （允许前端 JS 读取以支持 postMessage 回传）。
   *
   * <p>当 {@link CrossDomainSsoProperties#cookieDomain} 为空时，不设置 Domain 属性（仅当前域可见），
   * 降级为同域 Cookie，不影响现有认证流程。
   *
   * @param response HTTP 响应，不可为 null
   * @param accessToken 访问令牌
   * @param props 跨域 SSO 配置属性
   */
  public void injectTokenCookie(
      HttpServletResponse response, String accessToken, CrossDomainSsoProperties props) {
    if (response == null || accessToken == null || accessToken.isBlank()) {
      return;
    }
    Cookie cookie = new Cookie(props.getCookieName(), accessToken);
    cookie.setPath("/");
    // HttpOnly=false: 允许前端 JS 读取（用于 postMessage 回传给子应用）
    cookie.setHttpOnly(false);
    cookie.setSecure(props.isCookieSecure());
    cookie.setAttribute("SameSite", props.getCookieSameSite());

    // 计算 maxAge：优先使用显式配置，否则使用 access_token TTL
    int maxAge = props.getCookieMaxAge() > 0
        ? props.getCookieMaxAge()
        : (int) userInfoProperties.getTokenTtlSeconds();
    cookie.setMaxAge(maxAge);

    // 仅当配置了父域名时才设置 Domain（否则为同域 Cookie）
    String cookieDomain = props.getCookieDomain();
    if (cookieDomain != null && !cookieDomain.isBlank()) {
      cookie.setDomain(cookieDomain);
      log.debug("Setting cross-domain SSO cookie with domain: {}", cookieDomain);
    } else {
      log.debug("Setting same-domain SSO cookie (no cookieDomain configured)");
    }

    response.addCookie(cookie);
    log.info(
        "SSO token cookie injected: name={}, domain={}, secure={}, sameSite={}, maxAge={}",
        props.getCookieName(),
        cookieDomain,
        props.isCookieSecure(),
        props.getCookieSameSite(),
        maxAge);
  }

  /**
   * 从 Cookie 中读取 Token。
   *
   * <p>遍历请求中的所有 Cookie，匹配 {@code cookieName} 对应的值。
   * 跨域场景下浏览器会自动携带父域 Cookie，无需前端显式传递。
   *
   * @param request HTTP 请求，不可为 null
   * @param cookieName 要读取的 Cookie 名称
   * @return Token 字符串，未找到时返回 null
   */
  public String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
    if (request == null || cookieName == null || cookieName.isBlank()) {
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        String value = cookie.getValue();
        if (value != null && !value.isBlank()) {
          log.debug("Token extracted from cross-domain cookie: {}", cookieName);
          return value;
        }
      }
    }
    return null;
  }

  /**
   * 从 postMessage 回调请求中提取 Token。
   *
   * <p>前端主应用通过 {@code window.postMessage({token: "xxx"}, targetOrigin)} 将 Token 传递给
   * 跨域子 iframe/弹窗，子应用收到后通过 POST 请求将 Token 回传至服务端进行验证/交换。
   *
   * <p>请求参数名为 {@code sso_token}，由前端约定格式传递。
   *
   * @param request HTTP 请求
   * @return Token 字符串，未找到时返回 null
   */
  public String extractTokenFromPostMessage(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String token = request.getParameter(POST_MESSAGE_TOKEN_PARAM);
    if (token != null && !token.isBlank()) {
      log.debug("Token extracted from postMessage callback parameter");
      return token;
    }
    return null;
  }

  /**
   * 校验请求来源 Origin 是否在可信域列表中。
   *
   * <p>严格字符串匹配：Origin 格式为 {@code scheme://host[:port]}（如 {@code https://app1.example.com}），
   * 必须与 {@code trustedDomains} 中的某一项完全匹配。列表为空时返回 false（安全兜底）。
   *
   * @param origin 请求来源 Origin（HTTP Origin 头），可为 null
   * @param trustedDomains 可信域白名单
   * @return true 表示该 Origin 已加入白名单
   */
  public boolean isTrustedDomain(String origin, List<String> trustedDomains) {
    if (origin == null || origin.isBlank()) {
      return false;
    }
    if (trustedDomains == null || trustedDomains.isEmpty()) {
      return false;
    }
    boolean trusted = trustedDomains.contains(origin);
    if (!trusted) {
      log.warn("Untrusted SSO domain rejected: {}", origin);
    } else {
      log.debug("Trusted SSO domain verified: {}", origin);
    }
    return trusted;
  }

  /**
   * 添加 CORS 响应头。
   *
   * <p>仅在 Origin 命中白名单时添加 CORS 头，拒绝直接返回 {@code "*"}（防止 CSRF 攻击）。
   * 同时添加 {@code Vary: Origin} 响应头，告知缓存服务器响应内容随 Origin 变化，
   * 避免 CDN/代理缓存了带有 CORS 头的响应后回显给未授权的 Origin。
   *
   * <p>响应头清单：
   *
   * <ul>
   *   <li>{@code Access-Control-Allow-Origin}: 具体 Origin 值（非 "*"）</li>
   *   <li>{@code Access-Control-Allow-Credentials}: true（允许跨域携带 Cookie）</li>
   *   <li>{@code Access-Control-Allow-Methods}: 允许的 HTTP 方法</li>
   *   <li>{@code Access-Control-Allow-Headers}: 允许的请求头</li>
   *   <li>{@code Access-Control-Max-Age}: 预检结果缓存时间（秒）</li>
   *   <li>{@code Vary: Origin}: 防止 CDN 缓存污染</li>
   * </ul>
   *
   * @param response HTTP 响应，不可为 null
   * @param origin 已通过白名单校验的 Origin 值
   * @param trustedDomains 可信域白名单
   */
  public void addCorsHeaders(
      HttpServletResponse response, String origin, List<String> trustedDomains) {
    if (response == null || origin == null || origin.isBlank()) {
      return;
    }
    // 校验 Origin 白名单，拒绝直接返回 "*"
    if (!isTrustedDomain(origin, trustedDomains)) {
      log.warn("CORS headers not added for untrusted origin: {}", origin);
      return;
    }
    response.setHeader("Access-Control-Allow-Origin", origin);
    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
    response.setHeader(
        "Access-Control-Allow-Headers",
        "Content-Type, Authorization, X-Requested-With, X-Platform, X-Trace-Id");
    response.setHeader("Access-Control-Max-Age", "3600");
    // Vary: Origin — 防止 CDN/代理缓存 CORS 响应头后回显给其他 Origin
    response.addHeader("Vary", "Origin");
    log.debug("CORS headers added for trusted origin: {}", origin);
  }

  /**
   * 判断当前请求是否为跨域请求。
   *
   * <p>通过检查 Origin 头是否存在且与当前请求 Host 是否一致来判断。
   * 有 Origin 头且与当前域不同则为跨域请求。
   *
   * @param request HTTP 请求
   * @return true 表示跨域请求
   */
  public boolean isCrossDomainRequest(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String origin = request.getHeader("Origin");
    if (origin == null || origin.isBlank()) {
      return false;
    }
    // 从 Origin 中提取 host:port 部分
    String originHost = extractHost(origin);
    String serverName = request.getServerName();
    boolean crossDomain = !originHost.equalsIgnoreCase(serverName);
    if (crossDomain) {
      log.debug("Cross-domain request detected: origin={}, server={}", originHost, serverName);
    }
    return crossDomain;
  }

  /**
   * 从 URL 中提取 host 部分（去除 scheme 和 path）。
   *
   * @param url 完整 URL 或 scheme://host 格式字符串
   * @return host 部分（含端口，如有）
   */
  private String extractHost(String url) {
    if (url == null) {
      return "";
    }
    // 去除 scheme
    int schemeIdx = url.indexOf("://");
    String hostPart = schemeIdx >= 0 ? url.substring(schemeIdx + URL_SCHEME_SEPARATOR_LENGTH) : url;
    // 去除 path
    int pathIdx = hostPart.indexOf('/');
    if (pathIdx >= 0) {
      hostPart = hostPart.substring(0, pathIdx);
    }
    return hostPart;
  }
}
