package com.njydsz.common.base.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import com.njydsz.common.base.i18n.MessageResolverHolder;
import com.njydsz.common.base.i18n.MessageResolverRegistry;
import com.njydsz.common.base.i18n.SpringMessageResolver;

/**
 * 国际化配置基类（Web/App 共享）
 *
 * <p>提供基于 {@link ResourceBundleMessageSource} 的国际化支持。 子类覆盖 {@link #getBasenames()} 即可接入不同的 i18n
 * 资源文件。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>支持基于 {@code Accept-Language} 请求头的语言解析
 *   <li>默认支持简体中文（zh_CN）和美式英语（en_US）
 *   <li>默认编码 UTF-8，缺失 key 时回退到 code 而非抛出异常
 *   <li>通过 {@link MessageResolverRegistry} 桥接 Spring MessageSource 到框架 SPI
 * </ul>
 *
 * <p><b>资源文件命名规范：</b>
 *
 * <pre>{@code
 * messages.properties          // 默认
 * messages_zh_CN.properties    // 简体中文
 * messages_en_US.properties    // 美式英语
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class BaseI18nConfiguration {

  /**
   * 子类覆盖此方法提供不同的 i18n 资源文件名
   *
   * <p>返回值为 {@link ResourceBundleMessageSource} 接受的 basename 列表， 例如 {@code new String[]{"messages",
   * "i18n/messages"}}。
   *
   * @return 资源文件 basename 数组
   */
  protected abstract String[] getBasenames();

  /**
   * 注册 LocaleResolver
   *
   * <p>基于 {@link AcceptHeaderLocaleResolver} 从 HTTP 请求头 {@code Accept-Language} 解析语言。 默认语言为简体中文，支持
   * zh_CN 和 en_US。
   *
   * @return LocaleResolver 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.US));
    return resolver;
  }

  /**
   * 注册 MessageSource
   *
   * <p>使用 {@link ResourceBundleMessageSource} 加载多语言资源文件， 设置默认编码 UTF-8，开启 {@code
   * useCodeAsDefaultMessage} 以便在缺失 key 时 返回 code 而非抛出 NoSuchMessageException。
   *
   * @return MessageSource 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasenames(getBasenames());
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    return source;
  }

  /**
   * 注册消息解析器注册表。
   *
   * <p>桥接 Spring MessageSource 到框架统一的 MessageResolverHolder SPI， 同时支持 Spring 注入和程序化注册两种模式。
   *
   * @param messageSource Spring 消息源
   * @return MessageResolverRegistry 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public MessageResolverRegistry messageResolverRegistry(MessageSource messageSource) {
    MessageResolverRegistry registry = new MessageResolverRegistry();
    registry.register(new SpringMessageResolver(messageSource));
    // 同步注册到静态持有器，保证非 Spring 上下文也能访问
    MessageResolverHolder.setResolverIfAbsent(new SpringMessageResolver(messageSource));
    return registry;
  }
}
