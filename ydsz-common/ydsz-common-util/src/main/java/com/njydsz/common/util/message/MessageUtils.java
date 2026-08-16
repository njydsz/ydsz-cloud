package com.njydsz.common.util.message;

import com.njydsz.common.util.config.StaticBridge;
import com.njydsz.common.util.internal.proxy.CoreConstants;
import com.njydsz.common.util.internal.proxy.RequestContextProxy;
import com.njydsz.common.util.string.StringUtils;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;

/**
 * 消息工具类 提供国际化消息处理的相关方法
 *
 * <p>Spring 环境下通过 {@link MessageSourceConfiguration} 注入 {@link ObjectProvider}， 非 Spring
 * 环境下静态方法仍可通过降级路径返回默认消息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class MessageUtils {

  private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);

  /**
   * MessageSource 桥接器（Spring 环境下由 {@link MessageSourceConfiguration} 注入）。
   *
   * <p>使用 {@link ObjectProvider} 而非直接引用，避免启动期缺少 MessageSource 时抛出异常。
   */
  private static final StaticBridge<MessageSource> MESSAGE_SOURCE_BRIDGE = new StaticBridge<>();

  private MessageUtils() {
    throw new UnsupportedOperationException(
        "MessageUtils is a utility class and cannot be instantiated");
  }

  /**
   * 注入 MessageSource 提供者（Spring 容器启动后调用）。
   *
   * <p>由 {@link MessageSourceConfiguration} 通过 {@code @Configuration} 注册， 将 {@code
   * ObjectProvider<MessageSource>} 传入本类。
   *
   * @param provider MessageSource 提供者；允许 null（此时使用降级路径）
   * @since 2.2.0
   */
  public static void setMessageSourceProvider(ObjectProvider<MessageSource> provider) {
    MESSAGE_SOURCE_BRIDGE.registerSupplier(provider::getIfAvailable);
  }

  /**
   * 获得多语言内容，默认根据当前用户的 Locale 获取
   *
   * @param key 多语言 key
   * @return 翻译后的值，如获取不到则返回 key
   */
  public static String getMessage(String key) {
    if (StringUtils.isBlank(key)) {
      return key;
    }
    return getMessage(key, new Object[] {});
  }

  /**
   * 获得多语言内容--带参数，默认根据当前用户的 Locale 获取
   *
   * @param key 多语言 key
   * @param params 参数
   * @return 翻译后的值，如获取不到则返回 key
   */
  public static String getMessage(String key, Object[] params) {
    if (StringUtils.isBlank(key)) {
      return key;
    }

    Locale locale = getLocale();
    if (params == null) {
      params = new Object[] {};
    }
    return getMessage(locale, key, params);
  }

  /**
   * 获取多语言内容--带默认值，默认根据当前用户的 Locale 获取
   *
   * @param key 多语言 key
   * @param defaultMsg 默认返回值
   * @return 翻译后的值，获取不到返回默认值
   */
  public static String getMessage(String key, String defaultMsg) {
    if (StringUtils.isBlank(key)) {
      return defaultMsg;
    }
    return getMessage(key, new Object[] {}, defaultMsg);
  }

  /**
   * 获得多语言内容--带参数--带默认值，默认根据当前用户的 Locale 获取
   *
   * @param key 多语言 key
   * @param params 参数
   * @param defaultMsg 默认返回值
   * @return 翻译后的值，获取不到返回默认值
   */
  public static String getMessage(String key, Object[] params, String defaultMsg) {
    if (StringUtils.isBlank(key)) {
      return defaultMsg;
    }

    Locale locale = getLocale();
    return getMessage(locale, key, params, defaultMsg);
  }

  /**
   * 不带参数
   *
   * @param locale 语言信息
   * @param key 多语言 key
   * @return 翻译后的值
   */
  public static String getMessage(Locale locale, String key) {
    if (StringUtils.isBlank(key)) {
      return key;
    }
    return getMessage(locale, key, new Object[] {});
  }

  /**
   * 获取多语言数据
   *
   * @param locale 语言信息
   * @param key 多语言 key
   * @param params 参数
   * @return 翻译后的值
   */
  public static String getMessage(Locale locale, String key, Object[] params) {
    if (StringUtils.isBlank(key)) {
      return key;
    }
    // 统一语义：未找到时返回 key（即 defaultMsg = key），不依赖 catch 吞 NoSuchMessageException
    return getMessage(locale, key, params, key);
  }

  /**
   * 获取多语言数据
   *
   * @param locale 语言信息
   * @param key 多语言 key
   * @param params 参数
   * @param defaultMsg 默认返回值
   * @return 翻译后的值
   */
  public static String getMessage(Locale locale, String key, Object[] params, String defaultMsg) {
    if (StringUtils.isBlank(key)) {
      return defaultMsg;
    }

    try {
      MessageSource messageSource = resolveMessageSource();
      if (messageSource == null) {
        return defaultMsg;
      }
      // 使用带 defaultMessage 的重载，未找到时返回 defaultMsg 而非抛 NoSuchMessageException
      return messageSource.getMessage(key, params, defaultMsg, locale);
    } catch (Exception e) {
      logger.error("Get locale message error for key: {}", key, e);
    }
    return defaultMsg;
  }

  /**
   * 解析并缓存 MessageSource。
   *
   * <p>使用已注入的桥接器（Spring 环境下由 {@link MessageSourceConfiguration} 注入） 解析并缓存 MessageSource
   * 实例。解析成功时缓存结果，解析失败时返回 null 以便下次调用可重新尝试。
   *
   * @return MessageSource 实例，未找到或上下文未初始化时返回 null
   */
  private static MessageSource resolveMessageSource() {
    return MESSAGE_SOURCE_BRIDGE.getIfAvailable();
  }

  /**
   * 获取当前语言环境
   *
   * <p>优先从认证上下文读取用户语言（通过反射代理访问 RequestContext）， 不可用时降级为系统默认 Locale。
   *
   * <p><b>设计说明：</b>本方法通过反射获取 AuthInfo 对象的 userLanguage 属性， 避免 util 层对 common-auth 的编译期依赖，保持 L1
   * 工具层纯净。
   *
   * @return Locale 当前语言环境
   */
  private static Locale getLocale() {
    try {
      Object langObj = RequestContextProxy.get(CoreConstants.KEY_AUTH_INFO);
      String userLanguage = invokeGetUserLanguage(langObj);
      if (StringUtils.isBlank(userLanguage)) {
        return Locale.getDefault();
      }

      // 使用 Locale.ROOT 避免土耳其语 locale bug（如 "I".toLowerCase() 在 tr 下变为 "ı"）
      switch (userLanguage.toLowerCase(Locale.ROOT)) {
        case "zh_cn":
        case "zh-cn":
        case "zh":
          return Locale.SIMPLIFIED_CHINESE;
        case "en":
        case "en_us":
        case "en-us":
          return Locale.ENGLISH;
        default:
          return Locale.getDefault();
      }
    } catch (Exception e) {
      logger.warn("Failed to get user language, using default locale", e);
      return Locale.getDefault();
    }
  }

  /**
   * 通过反射调用对象的 getUserLanguage 方法（避免对 AuthInfo 的编译期依赖）。
   *
   * <p>L1 工具层禁止依赖 common-auth，本方法通过反射桥接获取用户语言。
   *
   * @param authObj 认证信息对象
   * @return 用户语言码；获取失败返回 null
   */
  private static String invokeGetUserLanguage(Object authObj) {
    if (authObj == null) {
      return null;
    }
    try {
      java.lang.reflect.Method method = authObj.getClass().getMethod("getUserLanguage");
      Object result = method.invoke(authObj);
      return result instanceof String str ? str : null;
    } catch (Exception e) {
      logger.debug("反射获取 userLanguage 失败: {}", e.getMessage());
      return null;
    }
  }
}
