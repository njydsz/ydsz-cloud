package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.exception.i18n.MessageResolver;
import com.njydsz.pmis.common.exception.i18n.MessageResolverHolder;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * 国际化（i18n）配置
 *
 * <p>配置 {@link MessageSource}，加载 {@code messages} 和 {@code validation-messages}
 * 资源包，支持通过 Accept-Language 请求头切换语言。
 *
 * <p>同时注册 {@link MessageResolver} 到 {@link MessageResolverHolder}，
 * 使 {@code common-core} 中的异常类可以懒加载解析 i18n 消息（P0-3）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class I18nConfig {

    /**
     * 配置基于资源包的消息源
     *
     * <p>加载 {@code messages} 和 {@code validation-messages} 两个基础名资源包，
     * 使用 UTF-8 编码。当找不到对应 key 时，直接返回 key 本身作为默认消息。
     *
     * @return 消息源
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages", "validation-messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        source.setFallbackToSystemLocale(false);
        return source;
    }

    /**
     * 注册 i18n 消息解析器到静态持有器
     *
     * <p>使 {@code AbstractPmisException.getMessage()} 可以通过
     * {@link MessageResolverHolder} 懒加载解析 i18n 消息。
     *
     * @param messageSource Spring 消息源
     * @return MessageResolver 实例
     */
    @Bean
    public MessageResolver messageResolver(MessageSource messageSource) {
        MessageResolver resolver = (key, args, locale) ->
                messageSource.getMessage(key, args, key, locale != null ? locale : Locale.getDefault());
        MessageResolverHolder.setResolver(resolver);
        return resolver;
    }
}
