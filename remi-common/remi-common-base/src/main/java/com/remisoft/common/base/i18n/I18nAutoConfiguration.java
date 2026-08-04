package com.remisoft.common.base.i18n;

import java.util.Locale;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 国际化消息自动配置。
 *
 * <p>P3-2: 统一 i18n 消息管理 — 配置 Spring {@link MessageSource}，
 * 各模块通过 {@code MessageSource.getMessage(code, args, locale)} 获取多语言消息。
 *
 * <p>消息资源文件位于 classpath:i18n/ 目录：
 * <ul>
 *   <li>{@code messages_zh_CN.properties} — 简体中文</li>
 *   <li>{@code messages_en_US.properties} — English</li>
 * </ul>
 *
 * <p>前端通过 {@code Accept-Language} 请求头指定语言，后端通过
 * {@code LocaleContextHolder.getLocale()} 获取当前请求语言。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MessageSource.class)
public class I18nAutoConfiguration {

    /**
     * 配置 MessageSource Bean。
     *
     * @return ResourceBundleMessageSource 实例
     */
    @Bean
    @ConditionalOnMissingBean(MessageSource.class)
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        log.info("[I18nAutoConfiguration] MessageSource bean registered, basenames=i18n/messages");
        return source;
    }
}
