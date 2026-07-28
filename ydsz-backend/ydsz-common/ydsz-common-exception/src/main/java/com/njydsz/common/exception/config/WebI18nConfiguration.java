package com.njydsz.common.exception.config;

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
 * Web 端国际化配置（ydsz-web）。
 *
 * <p>提供 ydsz-web 模块的 Locale 解析策略与 {@code LocaleResolver} Bean。
 *
 * <p>优先级：{@code X-Lang} Header > {@code Accept-Language} > Cookie > Session。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.LocaleResolver",
        "org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver"

/**
 * WebI18nConfiguration 自动配置类，注册模块 Bean 并管理装配条件。
 *
 * <p>所属包：{@code com.njydsz.common.exception.config}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
})
public class WebI18nConfiguration {

    private final I18nProperties properties;

    /**
     * 构造函数，注入国际化配置属性
     *
     * @param properties 国际化配置属性
     */
    public WebI18nConfiguration(I18nProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建区域解析器 Bean
     *
     * <p>基于 Accept-Language 请求头解析用户语言环境，默认语言为中文。
     *
     * @return 区域解析器实例
     */
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

    /**
     * 创建语言切换拦截器 Bean
     *
     * <p>支持通过请求参数动态切换语言环境。
     *
     * @return 语言切换拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(properties.getLangParamName());
        return interceptor;
    }
}
