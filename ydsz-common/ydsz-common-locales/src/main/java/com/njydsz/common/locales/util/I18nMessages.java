package com.njydsz.common.locales.util;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;

/**
 * 国际化消息工具类
 *
 * <p>作为可注入 Bean（{@code i18nMessages}），替代静态 {@link I18n#message(String)} 的可注入方案，
 * 适用于需要通过 Spring DI 获取 i18n 消息解析能力的场景（如 Service 层、工具类）。
 *
 * <p>与普通 {@link MessageSource} 相比，额外提供：
 *
 * <ul>
 *   <li>自动取当前请求线程 Locale（{@link LocaleContextHolder}）的便捷方法
 *   <li>无 MessageSource 时的安全兜底（返回 key 本身，不抛异常）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Service
 * public class UserServiceImpl implements UserService {
 *     private final I18nMessages i18n;
 *
 *     public UserServiceImpl(I18nMessages i18n) {
 *         this.i18n = i18n;
 *     }
 *
 *     public void validate(User user) {
 *         if (user == null) {
 *             throw BusinessException.of(CoreExceptionCode.PARAM_ERROR)
 *                 .msg(i18n.resolve("user.null"));
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>静态场景（如异常构造器）请直接使用 {@link I18n} 工具类 + {@link
 * com.njydsz.common.exception.custom.MessageSourceHolder}，无需注入。
 *
 * @author ydsz-team
 * @since 26.09.18
 * @see I18n
 */
public class I18nMessages {

  private final MessageSource messageSource;

  /**
   * 构造消息工具实例
   *
   * @param messageSource Spring MessageSource（不可为 null）
   */
  public I18nMessages(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * 按当前请求 Locale 解析国际化消息。
   *
   * @param key 消息键
   * @return 解析后的消息；key 未找到时返回 key 本身
   */
  public String resolve(String key) {
    return resolve(key, null);
  }

  /**
   * 按当前请求 Locale 解析国际化消息（带参数）。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @return 解析后的消息；key 未找到或 MessageSource 不可用时返回 key 本身
   */
  public String resolve(String key, @Nullable Object[] params) {
    return resolve(key, params, currentLocale());
  }

  /**
   * 按指定 Locale 解析国际化消息（带参数与默认文案）。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @param defaultMsg key 未找到时的默认文案
   * @return 解析后的消息
   */
  public String resolve(String key, @Nullable Object[] params, String defaultMsg) {
    if (key == null) {
      return defaultMsg;
    }
    try {
      return messageSource.getMessage(key, params, defaultMsg, currentLocale());
    } catch (Exception e) {
      return defaultMsg;
    }
  }

  /**
   * 按指定 Locale 解析国际化消息。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @param locale 区域设置（可为 null，回退到系统默认）
   * @return 解析后的消息；key 未找到时返回 key 本身
   */
  public String resolve(String key, @Nullable Object[] params, @Nullable Locale locale) {
    if (key == null) {
      return null;
    }
    Locale resolvedLocale = locale != null ? locale : Locale.ROOT;
    try {
      return messageSource.getMessage(key, params, key, resolvedLocale);
    } catch (Exception e) {
      return key;
    }
  }

  /**
   * 获取当前请求线程绑定的 Locale。
   *
   * <p>Web 请求场景返回 {@link LocaleContextHolder} 绑定的请求 Locale； 无请求上下文（定时任务、MQ 消费等）时返回系统默认 Locale，永不为
   * null。
   *
   * @return 当前解析使用的 Locale，永不为 null
   */
  public Locale currentLocale() {
    Locale locale = LocaleContextHolder.getLocale();
    return locale != null ? locale : Locale.ROOT;
  }

  /**
   * 检查底层 MessageSource 是否可用。
   *
   * @return 始终返回 true（构造时即要求 MessageSource 非 null）
   */
  public boolean isAvailable() {
    return messageSource != null;
  }
}
