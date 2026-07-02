package com.njydsz.pmis.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * 国际化（i18n）配置
 *
 * <p>注册 {@link MessageSource} 与 {@link LocaleResolver}，基于 Accept-Language 请求头
 * 解析 Locale，从 {@code messages} / {@code validation-messages} 资源文件中获取本地化消息。
 *
 * <p>支持的语言：
 * <ul>
 *   <li>{@link Locale#SIMPLIFIED_CHINESE}（默认）</li>
 *   <li>{@link Locale#US}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class I18nConfig {

    /**
     * 注册消息源
     *
     * <p>加载 {@code messages} 与 {@code validation-messages} 两个 basename，
     * 使用 UTF-8 编码；未找到 key 时直接返回 code，不回退到系统 Locale。
     *
     * @return 消息源实例
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
     * 注册 Locale 解析器
     *
     * <p>基于 HTTP Accept-Language 请求头解析 Locale，
     * 默认 Locale 为简体中文，支持简体中文与英文（US）。
     *
     * @return Locale 解析器实例
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.US));
        return resolver;
    }
}
