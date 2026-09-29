package com.njydsz.common.base.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.njydsz.common.base.i18n.MessageResolverHolder;
import com.njydsz.common.base.i18n.MessageResolverRegistry;
import com.njydsz.common.base.i18n.SpringMessageResolver;

/**
 * 国际化配置基类（Web/App 共享）
 *
 * <p>提供基于 {@link ResourceBundleMessageSource} 的国际化支持。 子类覆盖 {@link #getBasenames()} 即可接入不同的 i18n
 * 资源文件。
 *
 * <p><b>注意：</b>自 26.09.29 起，{@link #messageSource()} 和 {@link #localeResolver()} Bean 已移除——国际化
 * 基础设施统一由 {@code ydsz-common-locales} 的 {@link
 * com.njydsz.common.locales.config.LocalesAutoConfiguration} 提供（支持负缓存、缺失节流、运行时覆盖、user-priority
 * Locale 解析等增强能力）。本类仅保留 {@link #messageResolverRegistry()} 用于向后兼容。
 *
 * <p><b>特性：</b>
 *
 * <ul>
 *   <li>通过 {@link MessageResolverRegistry} 桥接 Spring MessageSource 到框架 SPI
 *   <li>默认编码 UTF-8，缺失 key 时回退到 code 而非抛出异常
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
 * @since 26.09.01
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
   * 注册消息解析器注册表（向后兼容路径）。
   *
   * <p>桥接 Spring MessageSource 到框架统一的 MessageResolverHolder SPI。 新代码请直接使用 {@link
   * com.njydsz.common.locales.util.MessageSourceHolder} 和 {@link
   * com.njydsz.common.locales.util.I18nMessages}。
   *
   * @param messageSource Spring 消息源
   * @return MessageResolverRegistry 实例
   * @deprecated 自 26.09.29 起废弃，{@link com.njydsz.common.locales.util.MessageSourceHolder} 已提供
   *     完整的负缓存 + 缺失节流 + 运行时覆盖能力。对齐 A-1/P0 统一 i18n 底座改造。
   */
  @Deprecated
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
