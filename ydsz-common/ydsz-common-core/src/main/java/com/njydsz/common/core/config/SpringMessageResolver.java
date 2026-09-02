package com.njydsz.common.core.config;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import com.njydsz.common.core.response.YdszResponse;

/**
 * 基于 Spring {@link MessageSource} 的国际化消息解析器。
 *
 * <p>将 Spring 的 {@link MessageSource} 适配为 {@link YdszResponse.MessageResolver}， 使 {@link
 * YdszResponse} 的成功/失败消息支持国际化。
 *
 * <p>解析流程：
 *
 * <ol>
 *   <li>从 {@link LocaleContextHolder} 获取当前请求的 Locale
 *   <li>调用 {@link MessageSource#getMessage(String, Object[], Locale)} 解析消息
 *   <li>解析失败时返回 defaultValue
 * </ol>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * # messages_zh_CN.properties
 * response.success=操作成功
 * response.error=操作失败
 *
 * # messages_en_US.properties
 * response.success=Operation succeeded
 * response.error=Operation failed
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see YdszResponse.MessageResolver
 */
public class SpringMessageResolver implements YdszResponse.MessageResolver {

  private final MessageSource messageSource;

  /**
   * 创建 SpringMessageResolver 实例
   *
   * @param messageSource Spring 消息源
   */
  public SpringMessageResolver(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @Override
  public String resolve(String key, String defaultValue) {
    if (key == null || key.isEmpty()) {
      return defaultValue;
    }
    Locale locale = LocaleContextHolder.getLocale();
    try {
      // 使用非抛异常的重载：key 不存在时直接返回 defaultMessage，避免异常构造成本
      return messageSource.getMessage(key, null, defaultValue, locale);
    } catch (NoSuchMessageException e) {
      // 防御性兜底：理论上 getMessage 三参数重载不会抛，但 MessageSource 实现可能不一致
      return defaultValue;
    }
  }
}
