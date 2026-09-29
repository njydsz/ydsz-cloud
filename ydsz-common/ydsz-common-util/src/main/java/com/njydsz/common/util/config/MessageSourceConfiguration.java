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
 * <p><b>后续演进方向：</b>将 {@link MessageUtils} 逐步迁移到 {@code ydsz-common-locales} 的 {@code I18n}
 * / {@code I18nMessages}（负缓存 + 缺失节流 + 运行时覆盖），届时本类可安全移除。当前 {@link MessageUtils}
 * 仍被 8 个 common 子模块文件依赖，暂保留以确保 i18n 能力不退化。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.locales.util.I18n 新 i18n 静态入口
 * @see com.njydsz.common.locales.util.I18nMessages 新 i18n 注入入口
 */
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
