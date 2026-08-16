package com.njydsz.common.base.i18n;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Spring MessageSource 适配器，将 Spring 国际化机制桥接到框架统一的 {@link MessageResolverHolder.MessageResolver}
 * SPI。
 *
 * <p>支持从 Spring {@link MessageSource} 按当前 {@link Locale} 解析消息， 解析失败时静默回退到 defaultValue。
 *
 * @author ydsz-team
 * @since 1.8.0
 * @see MessageResolverHolder
 * @see MessageResolverHolder.MessageResolver
 */
public class SpringMessageResolver implements MessageResolverHolder.MessageResolver {

  private static final Logger log = LoggerFactory.getLogger(SpringMessageResolver.class);

  private final MessageSource messageSource;

  /**
   * 创建 SpringMessageResolver 实例
   *
   * @param messageSource Spring 消息源
   */
  public SpringMessageResolver(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * 按当前 locale 解析国际化消息。
   *
   * <p>解析异常时静默返回 defaultValue，并输出 DEBUG 日志便于排查 i18n 配置问题。
   *
   * @param key 消息 key
   * @param defaultValue 默认消息文本
   * @return 解析后的消息内容
   */
  @Override
  public String resolveMessage(String key, String defaultValue) {
    try {
      Locale locale = LocaleContextHolder.getLocale();
      String message = messageSource.getMessage(key, null, locale);
      return message != null ? message : defaultValue;
    } catch (Exception e) {
      // DEBUG 级别记录，不影响正常流程，便于排查 i18n 配置问题
      log.debug(
          "Failed to resolve i18n message for key '{}', locale '{}'. "
              + "Falling back to default value: {}",
          key,
          LocaleContextHolder.getLocale(),
          defaultValue,
          e);
      return defaultValue;
    }
  }
}
