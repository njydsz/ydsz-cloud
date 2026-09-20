package com.njydsz.common.locales.config;

import java.util.Locale;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.WebUtils;

import com.njydsz.common.locales.util.KnownLocaleTags;

/**
 * 用户偏好 LocaleResolver（L2 基础设施）
 *
 * <p>解析顺序：请求参数 {@code ?lang=xxx} > Cookie {@code ydsz_locale} > 登录用户偏好（预留 SPI） >
 * Accept-Language Header > 默认 Locale。
 *
 * <p>参数/Cookie 指定的 Locale 会被写入当前请求浏览器 Cookie（有效期 365 天），下次打开仍生效 — 解决此前 {@link
 * org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver} 无状态导致的"每次打开都回到系统默认语言"的痛点。
 *
 * <p><b>注册方式：</b>在 application.yml 中设置 {@code ydsz.i18n.locale-resolver-type=user-priority} 即可启用（默认
 * 保留 AcceptHeader 行为）。需要 Spring Web 环境。
 *
 * <p><b>预留 SPI：</b>若需要联动 ydsz-userinfo 用户 profile 偏好（{@code user.getPreferredLocale()}），
 * 可重写 {@link #resolveUserPreferredLocale(HttpServletRequest)} 方法注入 Feign 调用，无需改动主流程。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see org.springframework.web.servlet.i18n.CookieLocaleResolver
 */
public class UserPriorityLocaleResolver implements LocaleResolver {

  /** 默认 Cookie 名称 */
  public static final String DEFAULT_LOCALE_COOKIE_NAME = "ydsz_locale";

  /** 默认 Cookie 有效期（秒）：365 天 */
  public static final int DEFAULT_COOKIE_MAX_AGE = 365 * 24 * 60 * 60;

  private final Locale defaultLocale;
  private final String localeCookieName;
  private final String langParamName;

  /**
   * 构造用户偏好 LocaleResolver
   *
   * @param defaultLocale 默认 Locale（不为 null）
   * @param localeCookieName 存储 Locale 的 Cookie 名称
   * @param langParamName 语言切换请求参数名
   */
  public UserPriorityLocaleResolver(
      Locale defaultLocale, String localeCookieName, String langParamName) {
    this.defaultLocale = defaultLocale != null ? defaultLocale : Locale.ROOT;
    this.localeCookieName =
        StringUtils.hasText(localeCookieName) ? localeCookieName : DEFAULT_LOCALE_COOKIE_NAME;
    this.langParamName = StringUtils.hasText(langParamName) ? langParamName : "lang";
  }

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    // 1. 请求参数最高优先级
    String langParam = request.getParameter(langParamName);
    if (StringUtils.hasText(langParam)) {
      Locale paramLocale = KnownLocaleTags.toKnownLocale(langParam.trim());
      if (!Locale.ROOT.equals(paramLocale)) {
        writeLocaleCookie(request, paramLocale);
        return paramLocale;
      }
    }

    // 2. Cookie 次之
    Cookie localeCookie = WebUtils.getCookie(request, localeCookieName);
    if (localeCookie != null && StringUtils.hasText(localeCookie.getValue())) {
      Locale cookieLocale = KnownLocaleTags.toKnownLocale(localeCookie.getValue().trim());
      if (!Locale.ROOT.equals(cookieLocale)) {
        return cookieLocale;
      }
    }

    // 3. 登录用户偏好（预留 SPI，子类或 AOP 注入实现）
    Locale userPreferred = resolveUserPreferredLocale(request);
    if (userPreferred != null) {
      return userPreferred;
    }

    // 4. Accept-Language Header
    Locale headerLocale = request.getLocale();
    if (headerLocale != null && KnownLocaleTags.isKnown(headerLocale.toString())) {
      return headerLocale;
    }

    // 5. 兜底默认 Locale
    return defaultLocale;
  }

  /**
   * 预留 SPI：解析登录用户的语言偏好。
   *
   * <p>默认实现返回 null（由前面的 Cookie / 参数 / Header 兜底）。子类可覆盖此方法，通过 Feign 调用 ydzs-userinfo
   * 查询当前登录用户的 preferredLocale。
   *
   * @param request ServletRequest
   * @return 用户偏好 Locale；未登录或无偏好时返回 null
   */
  protected Locale resolveUserPreferredLocale(HttpServletRequest request) {
    return null;
  }

  @Override
  public void setLocale(
      HttpServletRequest request, HttpServletResponse response, Locale locale) {
    // 参数/Cookie 驱动的解析流程无需在 setLocale 时额外处理（resolveLocale 已负责写入 Cookie）；
    // 仅当外部主动调用 setLocale 时才写入 Cookie
    if (locale != null && response != null) {
      writeLocaleCookie(request, locale);
    }
  }

  /**
   * 在用户浏览器写入（或刷新）语言偏好 Cookie
   *
   * @param request ServletRequest
   * @param locale 要持久化的 Locale
   */
  protected void writeLocaleCookie(HttpServletRequest request, Locale locale) {
    // 通过 Request Attribute 标记当前请求已写入 Cookie，由 Interceptor 在响应提交前写入
    // （UserPriorityLocaleWritingInterceptor 负责）
    request.setAttribute("ydsz.locale.to.persist", locale);
  }

  /**
   * 获取语言切换请求参数名称（默认 {@code lang}）。
   *
   * @return 请求参数名称
   */
  public String getLangParamName() {
    return langParamName;
  }

  /**
   * 获取存储 Locale 的 Cookie 名称（默认 {@link #DEFAULT_LOCALE_COOKIE_NAME}）。
   *
   * @return Cookie 名称
   */
  public String getLocaleCookieName() {
    return localeCookieName;
  }

  /**
   * 获取兜底默认 Locale（当请求参数 / Cookie / Header 均未提供时生效）。
   *
   * @return 默认 Locale
   */
  public Locale getDefaultLocale() {
    return defaultLocale;
  }
}
