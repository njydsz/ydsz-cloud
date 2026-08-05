package com.remisoft.common.base.i18n;

import java.util.Locale;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.remisoft.common.base.i18n.MessageResolverHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * 国际化消息自动配置。
 *
 * <p>P3-2: 统一 i18n 消息管理 — 配置 Spring {@link MessageSource}，
 * 各模块通过 {@code MessageSource.getMessage(code, args, locale)} 获取多语言消息。
 *
 * <p>消息资源文件位于 classpath:i18n/ 目录：
 * <ul>
 *   <li>{@code i18n/messages*.properties} — 通用消息（common-base）</li>
 *   <li>{@code i18n/core/messages*.properties} — 核心错误码消息（common-core）</li>
 *   <li>{@code i18n/file-messages*.properties} — 文件存储消息（common-file）</li>
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
        // 覆盖全部消息包：base(common)/core(错误码)/file(文件存储)
        source.setBasenames("i18n/messages", "i18n/core/messages", "i18n/file-messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        log.info("[I18nAutoConfiguration] MessageSource bean registered, basenames=i18n/messages,i18n/core/messages,i18n/file-messages");
        return source;
    }

    /**
     * 注册 SpringMessageResolver 并绑定到框架统一的 MessageResolverHolder SPI。
     *
     * <p>将 Spring MessageSource 适配为框架的国际化消息解析器，
     * 使响应消息（BaseResponse）支持 i18n。</p>
     *
     * <p>当 classpath 上存在 {@link MessageSource} 且容器中有对应 Bean 时生效。
     * 采用一次性设置语义，确保解析器在应用生命周期内不可变。</p>
     *
     * @param messageSource Spring 消息源
     * @return SpringMessageResolver 实例
     * @since 2.1.0
     */
    @Bean
    @ConditionalOnClass(MessageSource.class)
    @ConditionalOnBean(MessageSource.class)
    @ConditionalOnMissingBean(SpringMessageResolver.class)
    public SpringMessageResolver springMessageResolver(MessageSource messageSource) {
        SpringMessageResolver resolver = new SpringMessageResolver(messageSource);
        MessageResolverHolder.setResolverIfAbsent(resolver);
        log.info("[I18nAutoConfiguration] SpringMessageResolver registered, bound to MessageResolverHolder");
        return resolver;
    }
}
