package com.njydsz.common.excel.util;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Excel 模块国际化消息辅助类
 *
 * <p>通过反射调用 ydsz-common-util 的 MessageUtils（如果运行时 classpath 中存在），
 * 不存在时直接返回默认消息。解耦方式避免 L1 模块对 L2 的编译期硬依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ExcelI18nHelper {

  private static final Logger log = LoggerFactory.getLogger(ExcelI18nHelper.class);

  private static final String MESSAGE_UTILS_CLASS = "com.njydsz.common.util.message.MessageUtils";
  private static final String GET_MESSAGE_METHOD = "getMessage";

  private ExcelI18nHelper() {
    // 工具类禁止实例化
  }

  /**
   * 获取国际化消息，若 MessageUtils 不可用则返回默认消息
   *
   * @param key 消息键（MessageSource 中的 key）
   * @param params 消息参数
   * @param defaultMessage 默认消息（MessageUtils 不可用时返回）
   * @return 国际化后的消息或默认消息，不会为 {@code null}
   */
  public static String getMessage(String key, Object[] params, String defaultMessage) {
    try {
      Class<?> clazz = Class.forName(MESSAGE_UTILS_CLASS);
      Method method =
          clazz.getMethod(GET_MESSAGE_METHOD, String.class, Object[].class, String.class);
      return (String) method.invoke(null, key, params, defaultMessage);
    } catch (ClassNotFoundException e) {
      // ydsz-common-util 不在 classpath 中，使用默认消息
      log.trace("MessageUtils not found, using default message for key: {}", key);
      return defaultMessage;
    } catch (Exception e) {
      // 反射调用异常，降级到默认消息
      log.warn("MessageUtils invoke failed for key={}, using default", key, e);
      return defaultMessage;
    }
  }
}
