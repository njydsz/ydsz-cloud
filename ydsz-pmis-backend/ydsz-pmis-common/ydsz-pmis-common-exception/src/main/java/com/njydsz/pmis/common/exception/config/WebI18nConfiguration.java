package com.njydsz.pmis.common.exception.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import lombok.extern.slf4j.Slf4j;

/**
 * Web MVC 场景下的 i18n 配置
 *
 * <p>本类通过 {@link ConditionalOnClass} 条件装配，
 * 仅在类路径存在 {@code org.springframework.web.servlet.LocaleResolver} 时生效。
 * 这样 exception 模块可不依赖 spring-webmvc 编译期强引用。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.LocaleResolver",
        "org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver"
})
public class WebI18nConfiguration {

    private final I18nProperties properties;

    public WebI18nConfiguration(I18nProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(LocaleResolver.class)
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.CHINA);

        List<Locale> localeList = new ArrayList<>();
        for (String localeStr : properties.getSupportedLocales()) {
            String[] parts = localeStr.split("_");
            if (parts.length == 2) {
                localeList.add(new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build());
            } else {
                localeList.add(Locale.CHINA);
            }
        }
        resolver.setSupportedLocales(localeList);

        return resolver;
    }

    @Bean
    @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(properties.getLangParamName());
        return interceptor;
    }
}
