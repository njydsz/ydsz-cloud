package com.njydsz.common.locales.util;

import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化静态工具类
 *
 * <p>提供基于 MessageSource 的静态 i18n 消息解析方法，适用于静态场景（如异常构造器、常量类、工具方法等）。
 *
 * <p><b>使用前提：</b>需要在 Spring 容器启动后调用。首次使用前必须确保 {@link
 * com.njydsz.common.exception.custom.MessageSourceHolder} 已注入 Spring MessageSource（由 {@link
 * com.njydsz.common.locales.config.LocalesAutoConfiguration} 自动完成）。
 *
 * <p><b>与 {@link I18nMessages} 的关系：</b>
 *
 * <ul>
 *   <li>{@code I18n.message(key)}：静态方法调用，适合异常/工具/DTO 等无 Spring 注入场景
 *   <li>{@code I18nMessages.resolve(key)}：实例方法（可注入 Bean），适合 Service 等 Spring 托管场景
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 异常构造器（无 Spring 注入能力）
 * throw new BusinessException(ErrorCode.USER_NOT_FOUND)
 *     .msg(I18n.message("user.not.found", new Object[]{userId}));
 *
 * // Service / Controller（推荐注入）
 * &#64;Service
 * public class UserService {
 *     private final I18nMessages i18n;
 *
 *     public UserService(I18nMessages i18n) {
 *         this.i18n = i18n;
 *     }
 *
 *     public void validate() {
 *         throw BusinessException.of(ErrorCode.X).msg(i18n.resolve("key"));
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.18
 * @see I18nMessages
 * @see MessageSourceHolder
 */
public final class I18n {

  /** 私有构造器禁止实例化 */
  private I18n() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * 按当前请求 Locale 解析国际化消息。
   *
   * @param key 消息键（如 userinfo.user.not.found）
   * @return 解析后的消息；key 未找到或 MessageSourceHolder 未就绪时返回 key 本身
   */
  public static String message(String key) {
    return message(key, null);
  }

  /**
   * 按当前请求 Locale 解析国际化消息（带参数）。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @return 解析后的消息；key 未找到时返回 key 本身
   */
  public static String message(String key, Object[] params) {
    return message(key, params, currentLocale());
  }

  /**
   * 按指定 Locale 解析国际化消息。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @param locale 区域设置（可为 null，回退到系统默认）
   * @return 解析后的消息；key 未找到时返回 key 本身
   */
  public static String message(String key, Object[] params, Locale locale) {
    MessageSourceHolder.MessageResolver resolver = MessageSourceHolder.getResolver();
    if (resolver == null) {
      return key;
    }
    Locale resolvedLocale = locale != null ? locale : currentLocale();
    return resolver.resolve(key, params, key, resolvedLocale);
  }

  /**
   * 获取当前请求线程绑定的 Locale
   *
   * @return 当前 Locale，永不为 null
   */
  public static Locale currentLocale() {
    Locale locale = LocaleContextHolder.getLocale();
    return locale != null ? locale : Locale.ROOT;
  }

  /**
   * 判断当前线程是否有已解析的 Locale（非系统默认）
   *
   * <p>适用于需要区分"用户主动选择"与"系统默认"的场景（如记录语言偏好日志）。
   *
   * @return 是否已解析自定义 Locale
   */
  public static boolean hasLocale() {
    return LocaleContextHolder.getLocale() != null;
  }
}
