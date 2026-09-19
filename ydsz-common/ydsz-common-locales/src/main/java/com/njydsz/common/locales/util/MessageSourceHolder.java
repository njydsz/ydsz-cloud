package com.njydsz.common.locales.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化消息源静态持有者（L2 基础设施）
 *
 * <p>为异常体系（L3）提供无侵入的 i18n 消息解析能力。由 {@link
 * com.njydsz.common.locales.config.LocalesAutoConfiguration} 在启动时注入 Spring MessageSource，
 * 使异常类在不直接依赖 Spring 上下文的情况下实现 i18n 消息懒加载解析。
 *
 * <p>本类位于 L2（ydsz-common-locales），可供异常模块/领域模块/业务模块平等引用 —— 不违反层级单向依赖原则。
 *
 * <p><b>线程安全：</b>使用 volatile 引用 + 双重检查，保证多线程可见性。 一旦注入完成（应用就绪后），仅读取不写入，无并发风险。
 *
 * <p><b>使用约束：</b>
 *
 * <ul>
 *   <li>未注入 MessageSource 时（如单元测试、非 Spring 环境），{@link #resolve} 直接返回 key
 *   <li>注入后若 key 在 MessageSource 中不存在，返回 key 本身（兜底）
 *   <li>解析 Locale 默认取当前请求线程绑定的 Locale（{@link LocaleContextHolder}）， 无 Web 上下文时回退到 {@link
 *       Locale#ROOT}，保证多语言切换真实生效
 * </ul>
 *
 * <p>此持有者由 {@link com.njydsz.common.locales.util.I18n} 工具类与 {@link
 * com.njydsz.common.exception.custom.AbstractYdszException} 共同消费。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class MessageSourceHolder {

  private static final Logger LOG = LoggerFactory.getLogger(MessageSourceHolder.class);

  private MessageSourceHolder() {
    // 工具类禁止实例化
  }

  /**
   * 内部函数式接口，模拟 MessageSource.getMessage(key, params, defaultMsg, Locale) 签名。 使用内部接口而非直接持有
   * MessageSource 引用，避免模块对 Spring 的编译期依赖。
   */
  @FunctionalInterface
  public interface MessageResolver {
    /**
     * 解析国际化消息
     *
     * @param key 消息键
     * @param params 消息参数（可为 null）
     * @param defaultMsg 默认消息
     * @param locale 区域设置
     * @return 解析后的消息
     */
    String resolve(String key, Object[] params, String defaultMsg, Locale locale);
  }

  private static volatile MessageResolver resolver;

  /**
   * 注入消息解析器（由 LocalesAutoConfiguration 在启动时调用一次）
   *
   * @param newResolver 消息解析器，为 null 则清除当前解析器
   */
  public static void setResolver(MessageResolver newResolver) {
    resolver = newResolver;
    if (newResolver != null) {
      LOG.debug("MessageSourceResolver 已注入，国际化静态工具 I18n / 异常 AbstractYdszException 启用 i18n 解析");
    }
  }

  /**
   * 获取当前注入的解析器（供 I18n 工具与异常体系使用）
   *
   * @return 当前解析器，未注入时返回 null
   */
  public static MessageResolver getResolver() {
    return resolver;
  }

  /**
   * 检查解析器是否已注入
   *
   * @return 已注入返回 true
   */
  public static boolean isAvailable() {
    return resolver != null;
  }

  /**
   * 获取当前请求线程绑定的 Locale。
   *
   * <p>Web 请求场景返回 {@link LocaleContextHolder} 绑定的请求 Locale； 无请求上下文（定时任务、MQ 消费等）时返回系统默认 Locale，永不为
   * null。
   *
   * @return 当前解析使用的 Locale，永不为 null
   */
  public static Locale currentLocale() {
    Locale locale = LocaleContextHolder.getLocale();
    return locale != null ? locale : Locale.ROOT;
  }

  /** 负缓存运行状态引用（通过 CAS 切换，无锁读取） */
  private static final AtomicReference<NegativeCacheState> NEG_CACHE_STATE =
      new AtomicReference<>(NegativeCacheState.disabled());

  /**
   * 运行时翻译覆盖层（重启失效）
   *
   * <p>key 格式为 "messageKey|localeTag"（如 "error.user.not.found|en_US"），value 为覆盖后的翻译文案。
   * 通过 {@link #override(String, String, String)} 写入，通过 {@link #resolve(String, Object[], Locale)}
   * 在解析时优先命中，绕过底层 MessageSource basename 扫描。用于紧急翻译修复或 A/B 测试文案，无需重启应用。
   */
  private static final Map<String, String> RUNTIME_OVERRIDES = new ConcurrentHashMap<>();

  /**
   * 解析国际化消息（供 AbstractYdszException.getMessage() 与 I18n.message() 调用）。
   *
   * <p>按当前请求线程的 Locale 解析，保证同一异常在不同语言请求下返回对应文案。 若解析器未注入，直接返回 messageKey 本身（保持向后兼容）。 若解析器已注入但解析失败（如
   * key 不存在），同样返回 messageKey 兜底。
   *
   * @param messageKey 消息键
   * @param messageParams 消息参数
   * @return 已解析的国际化消息；解析失败时返回 messageKey
   */
  public static String resolve(String messageKey, Object[] messageParams) {
    return resolve(messageKey, messageParams, currentLocale());
  }

  /**
   * 按指定 Locale 解析国际化消息（供显式指定语言场景使用）。
   *
   * @param messageKey 消息键
   * @param messageParams 消息参数
   * @param locale Locale，为 null 时回退到 {@link Locale#ROOT}
   * @return 已解析的国际化消息；解析失败时返回 messageKey
   */
  public static String resolve(String messageKey, Object[] messageParams, Locale locale) {
    MessageResolver r = resolver;
    if (r == null || messageKey == null) {
      return messageKey;
    }
    Locale resolvedLocale = locale != null ? locale : Locale.ROOT;

    // ① 运行时覆盖层最高优先级（重启失效的紧急翻译修复 / A/B 文案）
    if (!RUNTIME_OVERRIDES.isEmpty()) {
      String overrideKey = messageKey + "|" + resolvedLocale;
      String override = RUNTIME_OVERRIDES.get(overrideKey);
      if (override != null) {
        return override;
      }
    }

    // ② 负缓存快速路径：已知该 key+Locale 不存在，直接返回 key，避免重复遍历所有 basename Properties
    NegativeCacheState cacheState = NEG_CACHE_STATE.get();
    if (cacheState.isEnabled() && cacheState.getCache().isMissing(messageKey, resolvedLocale)) {
      return messageKey;
    }

    try {
      String resolved = r.resolve(messageKey, messageParams, messageKey, resolvedLocale);
      if (resolved == null) {
        return messageKey;
      }
      // 当 useCodeAsDefaultMessage=true 时，未解析的 key 会返回 key 本身，此处触发缺失翻译告警 + 负缓存记录
      if (resolved.equals(messageKey)) {
        MissingTranslationLogger.tryWarn(messageKey, resolvedLocale);
        if (cacheState.isEnabled()) {
          cacheState.getCache().markMissing(messageKey, resolvedLocale);
        }
      }
      return resolved;
    } catch (Exception e) {
      // 解析失败时兜底返回 key，避免异常信息丢失
      return messageKey;
    }
  }

  /**
   * 配置负缓存运行参数。
   *
   * <p>由 {@link com.njydsz.common.locales.config.LocalesAutoConfiguration} 在启动时调用，控制负缓存的启用/容量。负缓存在开发环境（{@code devCacheSeconds=0}）下效果最显著，
   * 生产环境（大 cacheSeconds）因命中底层缓存而收益较小，但仍可减少重复遍历 basename 列表的开销。
   *
   * @param enabled 是否启用负缓存
   * @param capacity 缓存容量上限（仅 enabled=true 时生效）
   */
  public static void configureNegativeCache(boolean enabled, int capacity) {
    if (!enabled) {
      NEG_CACHE_STATE.set(NegativeCacheState.disabled());
      return;
    }
    NEG_CACHE_STATE.set(NegativeCacheState.enabled(Math.max(1, capacity)));
  }

  /**
   * 写入运行时翻译覆盖（重启失效）。
   *
   * <p>通过 admin API 调用；同样自动清空负缓存，让新翻译立即可见。覆盖层按 key+locale 独立存储； 不传 locale 则针对所有 Locale
   * 生效（deprecated 路径，不推荐）。
   *
   * @param key i18n 消息键（非 null、非空）
   * @param localeTag 语言标签（如 en_US、zh_CN）
   * @param translatedText 翻译后的文案（非 null）
   * @return 之前的覆盖值；首次写入返回 null
   */
  public static String override(String key, String localeTag, String translatedText) {
    if (key == null || key.isEmpty() || localeTag == null || translatedText == null) {
      throw new IllegalArgumentException(
          "override parameters must not be null/empty: key=" + key + ", locale=" + localeTag);
    }
    String overrideKey = key + "|" + localeTag;
    String previous = RUNTIME_OVERRIDES.put(overrideKey, translatedText);
    // 自动清除负缓存：让刚写入的覆盖值立即可见（不需要等的缓存刷新）
    NegativeCacheState cacheState = NEG_CACHE_STATE.get();
    if (cacheState.isEnabled()) {
      cacheState.getCache().clear();
    }
    return previous;
  }

  /**
   * 移除单次运行时翻译覆盖。
   *
   * @param key i18n 消息键
   * @param localeTag 语言标签
   * @return 被移除的覆盖值；不存在返回 null
   */
  public static String removeOverride(String key, String localeTag) {
    if (key == null || localeTag == null) {
      return null;
    }
    return RUNTIME_OVERRIDES.remove(key + "|" + localeTag);
  }

  /**
   * 获取当前运行时覆盖的只读视图（用于 admin API 展示）。
   *
   * @return 覆盖层的快照（key=messageKey|localeTag, value=已翻译文案）
   */
  public static Map<String, String> snapshotOverrides() {
    return Map.copyOf(RUNTIME_OVERRIDES);
  }

  /**
   * 清空运行时覆盖层（移除所有条目）。
   *
   * @return 被清空的条目数
   */
  public static int clearOverrides() {
    int size = RUNTIME_OVERRIDES.size();
    RUNTIME_OVERRIDES.clear();
    return size;
  }

  /**
   * 清空所有 i18n 缓存：负缓存 + 运行时覆盖层。
   *
   * <p>仅供 admin API reload 端点调用，在翻译资源文件变更后触发。
   */
  public static void clearCaches() {
    NegativeCacheState cacheState = NEG_CACHE_STATE.get();
    if (cacheState.isEnabled()) {
      cacheState.getCache().clear();
    }
    MissingTranslationLogger.reset();
  }

  /** 负缓存状态对象（通过 AtomicReference CAS 切换，无锁读取） */
  private static final class NegativeCacheState {
    private final boolean enabled;
    private final I18nNegativeCache cache;

    NegativeCacheState(boolean enabled, I18nNegativeCache cache) {
      this.enabled = enabled;
      this.cache = cache;
    }

    static NegativeCacheState disabled() {
      return new NegativeCacheState(false, null);
    }

    static NegativeCacheState enabled(int capacity) {
      return new NegativeCacheState(true, new I18nNegativeCache(capacity));
    }

    boolean isEnabled() {
      return enabled;
    }

    I18nNegativeCache getCache() {
      return cache;
    }
  }
}
