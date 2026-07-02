package com.njydsz.pmis.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 国际化（i18n）配置
 *
 * <p>配置 {@link MessageSource}，加载 {@code messages} 和 {@code validation-messages}
 * 资源包，支持通过 Accept-Language 请求头切换语言。
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
}
