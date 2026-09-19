package com.njydsz.common.locales.config;

import java.util.Locale;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户偏好 Locale 的 Cookie 写入拦截器
 *
 * <p>配合 {@link UserPriorityLocaleResolver} 使用：当本次请求触发语言切换（通过参数或 setLocale）， resolver 会将待持久化的 Locale 写入 Request Attribute，本拦截器在
 * {@code afterCompletion} 阶段一次性写入 Cookie，避免在 resolver 内直接操作响应。
 *
 * <p><b>注册方式：</b>由 {@link LocalesAutoConfiguration} 在 {@link
 * UserPriorityLocaleResolver} 启用时自动注册。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class UserPriorityLocaleWritingInterceptor implements HandlerInterceptor {

  public static final String PERSIST_LOCALE_ATTRIBUTE = "ydsz.locale.to.persist";

  private final String localeCookieName;
  private final int cookieMaxAge;

  public UserPriorityLocaleWritingInterceptor(String localeCookieName, int cookieMaxAge) {
    this.localeCookieName = localeCookieName;
    this.cookieMaxAge = cookieMaxAge;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    Object attr = request.getAttribute(PERSIST_LOCALE_ATTRIBUTE);
    if (attr instanceof Locale locale) {
      Cookie cookie = new Cookie(localeCookieName, locale.toString());
      cookie.setMaxAge(cookieMaxAge);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      // 不设置 SameSite，由 Spring Session / Servlet 容器默认策略决定；生产建议通过 CookieCustomizer 加固
      response.addCookie(cookie);
    }
  }
}
