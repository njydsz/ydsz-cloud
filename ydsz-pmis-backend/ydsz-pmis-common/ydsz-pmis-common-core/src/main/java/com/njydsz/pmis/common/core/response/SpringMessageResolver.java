package com.njydsz.pmis.common.core.response;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 基于 Spring {@link MessageSource} 的消息解析器实现
 *
 * <p>作为 {@link BaseResponse.MessageResolver} 的默认实现，
 * 替代了原有的静态 MessageResolver，避免跨 Spring 上下文共享导致的问题。
 * 通过 {@link LocaleContextHolder} 自动适配当前请求的区域语言。</p>
 *
 * <p><b>线程安全性：</b>本类为无状态 Bean，多线程并发调用安全。</p>
 *
 * <p><b>使用流程：</b></p>
 * <ol>
 *   <li>Spring Boot 启动时自动注册（{@code @Component}）</li>
 *   <li>调用 {@link BaseResponse#setResolver(BaseResponse.MessageResolver)} 注入到 {@link BaseResponse}</li>
 *   <li>业务代码调用 {@link BaseResponse#success()} 等方法时自动应用国际化</li>
 * </ol>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see BaseResponse.MessageResolver
 * @see BaseResponse#setResolver(BaseResponse.MessageResolver)
 */
@Component
public class SpringMessageResolver implements BaseResponse.MessageResolver {

    private final MessageSource messageSource;

    /**
     * 构造方法（Spring 注入）
     *
     * @param messageSource Spring 国际化消息源
     */
    public SpringMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 解析国际化消息
     *
     * <p>解析失败或 key 不存在时返回 {@code defaultValue}，保证业务调用方不会因 i18n 异常而中断。</p>
     *
     * @param key          国际化消息 key
     * @param defaultValue 默认消息文本（当 key 为空或解析失败时返回）
     * @return 解析后的消息内容
     */
    @Override
    public String resolve(String key, String defaultValue) {
        if (key == null) {
            return defaultValue;
        }

        Locale locale = LocaleContextHolder.getLocale();
        try {
            String message = messageSource.getMessage(key, null, defaultValue, locale);
            return message != null ? message : defaultValue;
        } catch (Exception e) {
            // 消息解析失败时返回默认值，避免影响主流程
            return defaultValue;
        }
    }
}
