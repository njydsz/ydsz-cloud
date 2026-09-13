package com.njydsz.common.excel.util;

import com.njydsz.common.util.message.MessageUtils;

/**
 * Excel 模块国际化消息辅助类
 *
 * <p>直接委托 ydsz-common-util 的 {@link MessageUtils} 获取国际化消息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ExcelI18nHelper {

  private ExcelI18nHelper() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
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
    return MessageUtils.getMessage(key, params, defaultMessage);
  }
}
