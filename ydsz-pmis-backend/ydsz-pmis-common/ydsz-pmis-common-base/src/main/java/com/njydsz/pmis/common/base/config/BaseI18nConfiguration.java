package com.njydsz.pmis.common.base.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * 国际化配置基类（Web/App 共享）
 *
 * <p>提供基于 {@link ResourceBundleMessageSource} 的国际化支持。
 * 子类覆盖 {@link #getBasenames()} 即可接入不同的 i18n 资源文件。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseI18nConfiguration {

    /**
     * 子类覆盖此方法提供不同的 i18n 资源文件名
     *
     * @return 资源文件 basename 数组
     */
    protected abstract String[] getBasenames();

    /**
     * 注册 LocaleResolver
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
     * @return MessageSource 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(getBasenames());
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}
