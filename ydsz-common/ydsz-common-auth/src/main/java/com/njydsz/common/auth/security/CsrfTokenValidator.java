package com.njydsz.common.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.util.string.StringUtils;

/**
 * CSRF Token 验证器（双重提交 Cookie 模式）。
 *
 * <p>采用双重提交 Cookie（Double Submit Cookie）模式防御 CSRF 攻击：
 *
 * <ul>
 *   <li>客户端在 Cookie 中存储 CSRF Token
 *   <li>客户端在请求头 X-CSRF-Token 中携带相同的 Token
 *   <li>服务端比较两者是否一致
 * </ul>
 *
 * <p>对于 Token-based 认证（JWT in Authorization Header），CSRF 风险较低， 因为攻击者无法跨域读取 JWT
 * Token。但作为纵深防御措施仍建议启用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CsrfTokenValidator {

  private static final Logger LOG = LoggerFactory.getLogger(CsrfTokenValidator.class);

  /** CSRF Token 请求头名称 */
  public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

  /** CSRF Token Cookie 名称 */
  public static final String CSRF_COOKIE_NAME = "ydsz-csrf-token";

  private final boolean enabled;

  public CsrfTokenValidator(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * 生成新的 CSRF Token。
   *
   * @return UUID 格式的 CSRF Token
   */
  public String generateToken() {
    return IdGenerator.nextIdStr();
  }

  /**
   * 设置 CSRF Cookie 到响应中。
   *
   * <p><b>双重提交 Cookie 模式的约束：</b>CSRF Token 需要前端 JS 读取后放入 {@code X-CSRF-Token} 请求头，因此该 Cookie
   * <b>不能设置 HttpOnly</b>。 设置 HttpOnly 会导致 JS 无法读取 Token，双重提交校验永远失败，防护形同虚设。
   *
   * <p>安全取舍说明：该 Cookie 仅承载 CSRF 防护 Token，与认证凭证（JWT/Session） 相互独立。即使攻击者通过 XSS 窃取 CSRF
   * Token，也无法直接用于认证。 若需 HttpOnly（如纯 Cookie 会话模式且由服务端自动注入 Token），请改为 同步 Token 模式并在校验时从服务端存储（如
   * Session/Redis）比对。
   *
   * @param response HTTP 响应
   * @param token CSRF Token
   */
  public void setCsrfCookie(HttpServletResponse response, String token) {
    if (!enabled || token == null) {
      return;
    }
    // SameSite=Strict + Secure 保持；不设 HttpOnly（双重提交模式需 JS 可读）
    String cookie =
        String.format("%s=%s; Path=/; SameSite=Strict; Secure", CSRF_COOKIE_NAME, token);
    response.setHeader("Set-Cookie", cookie);
  }

  /**
   * 校验 CSRF Token。
   *
   * <p>比较请求头中的 Token 与 Cookie 中的 Token 是否一致。
   *
   * @param request HTTP 请求
   * @return 校验通过返回 true，未启用或校验失败返回 false
   */
  public boolean validate(HttpServletRequest request) {
    if (!enabled) {
      return true;
    }

    String headerToken = request.getHeader(CSRF_HEADER_NAME);
    String cookieToken = getCookieValue(request, CSRF_COOKIE_NAME);

    if (StringUtils.isBlank(headerToken) || StringUtils.isBlank(cookieToken)) {
      LOG.debug(
          "CSRF Token 缺失: header={}, cookie={}",
          headerToken != null ? "present" : "missing",
          cookieToken != null ? "present" : "missing");
      return false;
    }

    if (!constantTimeEquals(headerToken, cookieToken)) {
      LOG.warn("CSRF Token 不匹配");
      return false;
    }

    return true;
  }

  /**
   * 判断 CSRF 防护是否启用。
   *
   * @return 启用返回 true
   */
  public boolean isEnabled() {
    return enabled;
  }

  private String getCookieValue(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return null;
    }
    for (var cookie : request.getCookies()) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  /** 恒定时间比较，防止时序攻击。 */
  private boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }
}
