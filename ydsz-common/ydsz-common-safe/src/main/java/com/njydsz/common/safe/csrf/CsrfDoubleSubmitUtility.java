package com.njydsz.common.safe.csrf;

import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.util.security.DigestUtils;

/**
 * CSRF 双重提交 Cookie 模式工具类。
 *
 * <p>提供无状态的 CSRF 防护能力，适用于需要在已有过滤器链中嵌入 CSRF 校验的场景（如认证过滤器基类）。
 * 与 {@link com.njydsz.common.safe.filter.CsrfFilter} 功能互补：
 * <ul>
 *   <li>{@code CsrfFilter} — 独立过滤器，完整生命周期管理（Token 生成 + 校验 + 刷新 + Origin 校验）</li>
 *   <li>本工具类 — 轻量委托 API，供程序化校验使用（BaseAuthFilter 等）</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>Token 使用 {@link SecureRandom} 生成 32 字节密码学随机数 + Base64URL 编码（非 UUID，不可预测）</li>
 *   <li>Cookie 不设 HttpOnly（双重提交模式要求前端 JS 可读）</li>
 *   <li>比较使用 {@link DigestUtils#constantTimeEquals} 防止时序攻击</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see com.njydsz.common.safe.filter.CsrfFilter
 */
public final class CsrfDoubleSubmitUtility {

  /** 密码学安全随机数生成器 */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** Token 字节长度（32 字节 = 256 bit 熵） */
  private static final int TOKEN_BYTE_LENGTH = 32;

  private CsrfDoubleSubmitUtility() {
    // 工具类禁止实例化
  }

  /**
   * 生成密码学安全的 CSRF Token。
   *
   * <p>使用 {@link SecureRandom} 生成 32 字节随机数，经 Base62URL 无填充编码后得到 43 字符 URL 安全字符串。
   * 相比 UUID（仅 122 bit 随机位），本方法提供更高的不可预测性。
   *
   * @return Base64URL 编码的 CSRF Token（43 字符）
   */
  public static String generateToken() {
    byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * 校验双重提交 Cookie 模式的 CSRF Token。
   *
   * <p>比较请求头中的 Token 与 Cookie 中的 Token 是否恒定时间相等。
   *
   * @param request     HTTP 请求
   * @param headerName  Token 请求头名称（如 {@code X-CSRF-Token}）
   * @param cookieName  Token Cookie 名称（如 {@code ydsz-csrf-token}）
   * @return 校验通过返回 true，Token 缺失或不匹配返回 false
   */
  public static boolean validateDoubleSubmit(HttpServletRequest request, String headerName, String cookieName) {
    String headerToken = request.getHeader(headerName);
    String cookieToken = getCookieValue(request, cookieName);

    if (headerToken == null || headerToken.isEmpty() || cookieToken == null || cookieToken.isEmpty()) {
      return false;
    }

    return DigestUtils.constantTimeEquals(headerToken, cookieToken);
  }

  /**
   * 构建 CSRF 双重提交 Cookie 并添加到响应。
   *
   * <p>Cookie 安全属性：
   * <ul>
   *   <li>HttpOnly = false（双重提交模式要求前端 JS 可读）</li>
   *   <li>Secure = 根据请求协议动态决定（HTTPS 请求自动启用）；request 为 null 时默认 false</li>
   *   <li>SameSite = Strict</li>
   *   <li>Path = /</li>
   * </ul>
   *
   * @param response   HTTP 响应
   * @param cookieName Cookie 名称
   * @param token      Token 值
   * @param request    HTTP 请求（用于判断 Secure 标志，可为 null）
   */
  public static void addCsrfCookie(HttpServletResponse response, String cookieName, String token,
      HttpServletRequest request) {
    Cookie cookie = new Cookie(cookieName, token);
    cookie.setPath("/");
    // 双重提交模式需要前端 JS 读取 Token，不能设置 HttpOnly
    cookie.setHttpOnly(false);
    // Secure 标志：request 非 null 时根据协议动态决定；null 时默认 false
    cookie.setSecure(request != null && request.isSecure());
    cookie.setAttribute("SameSite", "Strict");
    response.addCookie(cookie);
  }

  /**
   * 从请求中获取指定名称的 Cookie 值。
   *
   * @param request    HTTP 请求
   * @param cookieName Cookie 名称
   * @return Cookie 值，不存在返回 null
   */
  public static String getCookieValue(HttpServletRequest request, String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
