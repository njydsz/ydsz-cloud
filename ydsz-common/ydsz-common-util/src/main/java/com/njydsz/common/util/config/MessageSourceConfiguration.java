package com.njydsz.common.util.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.util.message.MessageUtils;

/**
 * MessageSource 自动装配配置。
 *
 * <p>将 Spring 容器的 {@link MessageSource} 以 {@link ObjectProvider} 形式注入到 {@link MessageUtils}，打破
 * {@code MessageUtils} 与静态上下文的耦合。
 *
 * <p>仅当容器中存在 {@link MessageSource} Bean 时才激活，不会影响无 Spring 上下文场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 自 26.09.29 起废弃。国际化解析请统一使用 {@link
 *     com.njydsz.common.locales.util.I18n}（静态场景）或 {@link
 *     com.njydsz.common.locales.util.I18nMessages}（注入场景），享有负缓存 + 缺失节流 + 运行时覆盖
 *     全体系能力。对齐 A-1/P0 统一 i18n 底座改造。
 */
@Deprecated
@AutoConfiguration(after = UtilAutoConfiguration.class)
@ConditionalOnClass(MessageSource.class)
@ConditionalOnBean(MessageSource.class)
public class MessageSourceConfiguration {

  /**
   * 将 MessageSource ObjectProvider 注入到 MessageUtils。
   *
   * @param messageSourceProvider Spring 容器提供 MessageSource 的 ObjectProvider
   * @return 标记 Bean（仅触发注入逻辑，无需外部引用）
   */
  @Bean
  public ObjectProvider<MessageSource> messageUtilsMessageSource(
      ObjectProvider<MessageSource> messageSourceProvider) {
    MessageUtils.setMessageSourceProvider(messageSourceProvider);
    return messageSourceProvider;
  }
}
