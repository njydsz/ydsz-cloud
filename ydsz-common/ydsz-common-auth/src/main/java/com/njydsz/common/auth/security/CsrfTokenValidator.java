package com.njydsz.common.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.safe.csrf.CsrfDoubleSubmitUtility;

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
 * <p><b>架构说明：</b>本验证器委托 ydsz-common-safe 的 {@link CsrfDoubleSubmitUtility} 执行核心逻辑，
 * 确保 Token 生成（密码学安全随机数）、比较（恒定时间）、Cookie 构建（安全属性）统一遵循安全模块规范。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @since 26.10.01 重构为 CsrfDoubleSubmitUtility 委托适配器
 * @see CsrfDoubleSubmitUtility
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
   * <p>委托 {@link CsrfDoubleSubmitUtility#generateToken()} 生成密码学安全的随机 Token
   * （{@link java.security.SecureRandom} 32 字节 + Base64URL 编码，43 字符）。
   *
   * @return 密码学安全的 CSRF Token
   */
  public String generateToken() {
    return CsrfDoubleSubmitUtility.generateToken();
  }

  /**
   * 设置 CSRF Cookie 到响应中。
   *
   * <p><b>双重提交 Cookie 模式的约束：</b>CSRF Token 需要前端 JS 读取后放入 {@code X-CSRF-Token} 请求头，因此该 Cookie
   * <b>不能设置 HttpOnly</b>。 设置 HttpOnly 会导致 JS 无法读取 Token，双重提交校验永远失败，防护形同虚设。
   *
   * <p>安全取舍说明：该 Cookie 仅承载 CSRF 防护 Token，与认证凭证（JWT/Session） 相互独立。即使攻击者通过 XSS 窃取 CSRF
   * Token，也无法直接用于认证。
   *
   * @param response HTTP 响应
   * @param token CSRF Token
   */
  public void setCsrfCookie(HttpServletResponse response, String token) {
    setCsrfCookie(response, token, null);
  }

  /**
   * 设置 CSRF Cookie 到响应中（带请求上下文）。
   *
   * <p>当调用方持有 {@link HttpServletRequest} 时，建议使用此重载以获得更精确的 Secure 标志判定。
   *
   * @param response HTTP 响应
   * @param token    CSRF Token
   * @param request  HTTP 请求（用于判断 Secure 标志）
   */
  public void setCsrfCookie(HttpServletResponse response, String token, HttpServletRequest request) {
    if (!enabled || token == null) {
      return;
    }
    // 委托 safe 模块工具类设置标准化 CSRF Cookie（HttpOnly=false + SameSite=Strict + 动态 Secure）
    CsrfDoubleSubmitUtility.addCsrfCookie(response, CSRF_COOKIE_NAME, token, request);
  }

  /**
   * 校验 CSRF Token。
   *
   * <p>委托 {@link CsrfDoubleSubmitUtility#validateDoubleSubmit} 执行恒定时间比较。
   *
   * @param request HTTP 请求
   * @return 校验通过返回 true，未启用或校验失败返回 false
   */
  public boolean validate(HttpServletRequest request) {
    if (!enabled) {
      return true;
    }

    boolean valid = CsrfDoubleSubmitUtility.validateDoubleSubmit(request, CSRF_HEADER_NAME,
        CSRF_COOKIE_NAME);

    if (!valid) {
      String headerToken = request.getHeader(CSRF_HEADER_NAME);
      String cookieToken = CsrfDoubleSubmitUtility.getCookieValue(request, CSRF_COOKIE_NAME);
      LOG.debug("CSRF Token 校验失败 | header={} | cookie={}",
          headerToken != null ? "present" : "absent",
          cookieToken != null ? "present" : "absent");
    }

    return valid;
  }

  /**
   * 判断 CSRF 防护是否启用。
   *
   * @return 启用返回 true
   */
  public boolean isEnabled() {
    return enabled;
  }
}
