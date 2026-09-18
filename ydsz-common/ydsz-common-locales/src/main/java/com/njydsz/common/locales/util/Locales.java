package com.njydsz.common.locales.util;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;

/**
 * Locale 上下文工具类
 *
 * <p>封装 {@link LocaleContextHolder} 的常用操作，提供：
 *
 * <ul>
 *   <li>获取/设置当前请求线程绑定的 Locale
 *   <li>可选值获取（返回 {@link Optional} 避免 NPE）
 *   <li>固定 Locale 上下文中执行一段代码（临时切换，执行后自动恢复）
 * </ul>
 *
 * <p><b>与 {@link I18n} 的关系：</b>{@link I18n} 管消息解析，{@code Locales} 管 Locale 上下文本身。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 临时用英文执行一段代码
 * Locales.withLocale(Locale.US, () -> {
 *     String englishMsg = i18nService.generateReport();
 *     System.out.println(englishMsg);
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public final class Locales {

  /** 私有构造器禁止实例化 */
  private Locales() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * 获取当前请求线程绑定的 Locale
   *
   * @return 当前 Locale，永不为 null
   */
  public static Locale current() {
    return MessageSourceHolder.currentLocale();
  }

  /**
   * 获取当前请求线程绑定的 Locale（Optional 包装）
   *
   * @return 当前 Locale 的 Optional；注意始终非 null（LocaleContextHolder 默认为系统 Locale）
   */
  public static Optional<Locale> currentOptional() {
    return Optional.of(current());
  }

  /**
   * 设置当前请求线程绑定的 Locale
   *
   * @param locale 要设置的 Locale；传 null 清除绑定（回退到系统默认）
   */
  public static void set(@Nullable Locale locale) {
    LocaleContextHolder.setLocale(locale);
  }

  /**
   * 设置当前请求线程绑定的 Locale（使用语言标签字符串）
   *
   * @param localeTag 语言标签（如 zh_CN、en_US）；传 null 清除绑定
   * @throws IllegalArgumentException localeTag 格式非法时抛出
   */
  public static void set(String localeTag) {
    if (localeTag == null || localeTag.isEmpty()) {
      LocaleContextHolder.setLocale(null);
      return;
    }
    Locale locale = parseLocaleTag(localeTag);
    LocaleContextHolder.setLocale(locale);
  }

  /**
   * 在临时切换的 Locale 上下文中执行操作，执行完毕后自动恢复原 Locale
   *
   * @param locale 临时切换的 Locale
   * @param action 要执行的操作
   */
  public static void withLocale(Locale locale, Runnable action) {
    Locale previous = LocaleContextHolder.getLocale();
    try {
      LocaleContextHolder.setLocale(locale);
      action.run();
    } finally {
      LocaleContextHolder.setLocale(previous);
    }
  }

  /**
   * 在临时切换的 Locale 上下文中执行操作并返回结果，执行完毕后自动恢复原 Locale
   *
   * @param locale 临时切换的 Locale
   * @param action 要执行的操作
   * @param <T> 返回值类型
   * @return 操作返回结果
   */
  public static <T> T withLocale(Locale locale, Supplier<T> action) {
    Locale previous = LocaleContextHolder.getLocale();
    try {
      LocaleContextHolder.setLocale(locale);
      return action.get();
    } finally {
      LocaleContextHolder.setLocale(previous);
    }
  }

  /**
   * 解析语言标签字符串为 Locale
   *
   * @param localeTag 语言标签（如 zh_CN、en_US）
   * @return 对应的 Locale；传入 null/空串时返回 null
   * @throws IllegalArgumentException localeTag 格式非法时抛出
   */
  public static Locale parseLocaleTag(@Nullable String localeTag) {
    if (localeTag == null || localeTag.isEmpty()) {
      return null;
    }
    String[] parts = localeTag.split("_");
    if (parts.length == 2) {
      return new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
    }
    if (parts.length == 1) {
      return new Locale.Builder().setLanguage(parts[0]).build();
    }
    throw new IllegalArgumentException("Invalid locale tag format: " + localeTag);
  }
}
