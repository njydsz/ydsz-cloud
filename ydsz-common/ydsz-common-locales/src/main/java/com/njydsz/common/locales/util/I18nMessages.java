package com.njydsz.common.locales.util;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化消息工具类
 *
 * <p>作为可注入 Bean（{@code i18nMessages}），替代静态 {@link I18n#message(String)} 的可注入方案，
 * 适用于需要通过 Spring DI 获取 i18n 消息解析能力的场景（如 Service 层、工具类）。
 *
 * <p>内部通过 {@link MessageSourceHolder#resolve(String, Object[], Locale)} 间接访问 Spring
 * MessageSource，从而获得与静态工具统一的负缓存 + 缺失节流行为，无需在可注入路径重复实现一次。
 *
 * <hr>
 *
 * <h3>与静态 {@link I18n} 的路径对比</h3>
 *
 * <table border="1" style="border-collapse:collapse">
 *   <tr><th>入口</th><th>消费方</th><th>内部实现</th><th>负缓存/节流</th></tr>
 *   <tr><td>{@code I18n.message(key)}</td><td>异常、DTO、常量类</td><td>{@link MessageSourceHolder#resolve}</td><td>✅</td></tr>
 *   <tr><td>{@code I18nMessages.resolve(key)}</td><td>Service、Spring 托管工具类</td><td>{@link MessageSourceHolder#resolve}</td><td>✅</td></tr>
 * </table>
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
 *             throw BusinessException.of(PARAM_ERROR)
 *                 .msg(i18n.resolve("user.null"));
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>静态场景（如异常构造器）请直接使用 {@link I18n} 工具类，无需注入。
 *
 * @author ydsz-team
 * @since 26.09.18
 * @see I18n
 * @see MessageSourceHolder
 */
public class I18nMessages {

  private final MessageSource messageSource;

  /**
   * 构造消息工具实例
   *
   * @param messageSource Spring MessageSource（不可为 null。实际解析时若 {@link MessageSourceHolder} 已注入 resolver
   *     则走负缓存/降级路径；否则直接使用 messageSource 做兜底）
   */
  public I18nMessages(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * 按当前请求 Locale 解析国际化消息。
   *
   * <p>内部委托 {@link MessageSourceHolder#resolve(String, Object[])}，享有负缓存 + 缺失节流统一行为。
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
   * <p>内部委托 {@link MessageSourceHolder#resolve(String, Object[], Locale)}，享有统一行为。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @return 解析后的消息；key 未找到或 MessageSourceHolder 未就绪时：返回 key 本身（fallback 走内部 messageSource）
   */
  public String resolve(String key, Object[] params) {
    Locale currentLocale = currentLocale();
    // 若 MessageSourceHolder 已注入 resolver（应用启动后），走统一路径（负缓存 + 缺失节流）
    if (MessageSourceHolder.isAvailable()) {
      return MessageSourceHolder.resolve(key, params, currentLocale);
    }
    // 未就绪时（启动前/测试/非 Spring 路径）直接用 messageSource 兜底，与旧行为保持一致
    return directResolve(key, params, currentLocale, key);
  }

  /**
   * 按当前请求 Locale 解析国际化消息（带参数与默认文案）。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @param defaultMsg key 未找到时的默认文案
   * @return 解析后的消息
   */
  public String resolve(String key, Object[] params, String defaultMsg) {
    if (key == null) {
      return defaultMsg;
    }
    Locale currentLocale = currentLocale();
    if (!MessageSourceHolder.isAvailable()) {
      return directResolve(key, params, currentLocale, defaultMsg);
    }
    // 有 resolver 时走 MessageSourceHolder，但 MessageSourceHolder.resolve 未暴露 defaultMsg 参数，
    // 故在 holder 返回等于 key 时给出 caller 指定的 defaultMsg
    String resolved = MessageSourceHolder.resolve(key, params, currentLocale);
    return key.equals(resolved) ? defaultMsg : resolved;
  }

  /**
   * 按指定 Locale 解析国际化消息。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @param locale 区域设置（可为 null，回退到系统默认）
   * @return 解析后的消息；key 未找到时返回 key 本身
   */
  public String resolve(String key, Object[] params, Locale locale) {
    if (key == null) {
      return null;
    }
    Locale resolvedLocale = locale != null ? locale : Locale.ROOT;
    if (!MessageSourceHolder.isAvailable()) {
      return directResolve(key, params, resolvedLocale, key);
    }
    return MessageSourceHolder.resolve(key, params, resolvedLocale);
  }

  /**
   * 按指定 Locale 解析国际化消息（带参数与默认文案）。
   *
   * <p>在指定的 Locale 解析消息；若返回 key（即 fallback）则使用 defaultMsg。
   *
   * @param key 消息键
   * @param params 消息参数（可为 null）
   * @param locale 区域设置（可为 null，回退到系统默认）
   * @param defaultMsg key 未找到时返回的默认文案
   * @return 解析后的消息
   */
  public String resolve(String key, Object[] params, Locale locale, String defaultMsg) {
    if (key == null) {
      return defaultMsg;
    }
    Locale resolvedLocale = locale != null ? locale : Locale.ROOT;
    if (!MessageSourceHolder.isAvailable()) {
      return directResolve(key, params, resolvedLocale, defaultMsg);
    }
    String resolved = MessageSourceHolder.resolve(key, params, resolvedLocale);
    return key.equals(resolved) ? defaultMsg : resolved;
  }

  /**
   * 获取当前请求线程绑定的 Locale。
   *
   * <p>Web 请求场景返回 {@link LocaleContextHolder} 绑定的请求 Locale；无请求上下文（定时任务、MQ 消费等）时返回系统默认 Locale，永不为 null。
   *
   * @return 当前解析使用的 Locale，永不为 null
   */
  public Locale currentLocale() {
    Locale locale = LocaleContextHolder.getLocale();
    return locale != null ? locale : Locale.ROOT;
  }

  /**
   * 检查底层 MessageSource 是否可用（注入时的 field 非 null）。
   *
   * <p><b>注意：</b>本方法仅判断注入时的引用非 null，不代表 {@link MessageSourceHolder} 已注入 resolver。 查询运行时是否可走统一路径，请使用 {@link MessageSourceHolder#isAvailable()}。
   *
   * @return 注入返回 true；IS-A 注入返回 true
   */
  public boolean isAvailable() {
    return messageSource != null;
  }

  /**
   * 直接通过底层 messageSource 解析消息（不经过 MessageSourceHolder）。
   *
   * <p>作为 resolver 不可用时的兜底（启动早期、{@code @PostConstruct} 中嵌套调用、MessageSourceHolder 卸载等极端场景）。 行为与改造前一致：调用 {@link MessageSource#getMessage(String, Object[], String, Locale)}，异常时返回 defaultMsg。
   *
   * @param key 消息键（非 null）
   * @param params 消息参数（可为 null）
   * @param locale 区域设置（非 null）
   * @param defaultMsg 解析失败时的返回
   * @return 解析后的消息
   */
  private String directResolve(String key, Object[] params, Locale locale, String defaultMsg) {
    try {
      return messageSource.getMessage(key, params, defaultMsg, locale);
    } catch (Exception e) {
      return defaultMsg;
    }
  }
}
