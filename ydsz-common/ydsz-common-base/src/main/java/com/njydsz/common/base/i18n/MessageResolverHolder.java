package com.njydsz.common.base.i18n;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.LoggerFactory;

/**
 * 国际化消息解析器持有者（SPI）。
 *
 * <p>提供框架统一的国际化消息解析入口，由应用侧通过 {@link #setResolverIfAbsent(MessageResolver)} 注入具体实现 （如 Spring {@code
 * MessageSource} 适配器），避免核心模块硬依赖 Spring。
 *
 * <p><b>迁移说明：</b>本类原定义于 {@code ydsz-common-core}（26.09.01 精简核心时移除）， 因国际化能力属于 ydsz-common-base
 * 应用层，迁移至本模块维护。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class MessageResolverHolder {

  /** 消息解析函数式接口。 */
  @FunctionalInterface
  public interface MessageResolver {
    /**
     * 解析国际化消息。
     *
     * @param key 消息 key
     * @param defaultValue 解析失败时的默认值
     * @return 解析后的消息；解析失败时返回默认值
     */
    String resolveMessage(String key, String defaultValue);
  }

  private static final AtomicReference<MessageResolver> RESOLVER = new AtomicReference<>();

  private MessageResolverHolder() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 一次性注册消息解析器（仅当尚未注册时生效）。
   *
   * @param resolver 消息解析器，可为 null
   * @return true 表示本次注册成功（此前未注册）
   */
  public static boolean setResolverIfAbsent(MessageResolver resolver) {
    boolean success = RESOLVER.compareAndSet(null, resolver);
    if (!success && resolver != null) {
      LoggerFactory.getLogger(MessageResolverHolder.class)
          .debug(
              "MessageResolver already registered, ignoring subsequent setResolverIfAbsent call");
    }
    return success;
  }

  /**
   * 是否已注册消息解析器。
   *
   * @return true 表示已注册
   */
  public static boolean isResolverRegistered() {
    return RESOLVER.get() != null;
  }

  /**
   * 解析国际化消息。
   *
   * @param key 消息 key
   * @param defaultValue 解析失败时的默认值
   * @return 解析后的消息；未注册解析器或解析失败时返回默认值
   */
  public static String resolveMessage(String key, String defaultValue) {
    MessageResolver currentResolver = RESOLVER.get();
    if (currentResolver != null) {
      String result = currentResolver.resolveMessage(key, defaultValue);
      return result != null ? result : defaultValue;
    }
    return defaultValue;
  }

  /** 测试专用：重置已注册的解析器。 */
  public static void testResetResolver() {
    RESOLVER.set(null);
  }
}
