package com.njydsz.common.core.config;

import com.njydsz.common.core.response.BaseResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * 基于 Spring {@link MessageSource} 的国际化消息解析器。
 *
 * <p>将 Spring 的 {@link MessageSource} 适配为 {@link BaseResponse.MessageResolver}，
 * 使 {@link BaseResponse} 的成功/失败消息支持国际化。</p>
 *
 * <p>解析流程：
 * <ol>
 *   <li>从 {@link LocaleContextHolder} 获取当前请求的 Locale</li>
 *   <li>调用 {@link MessageSource#getMessage(String, Object[], Locale)} 解析消息</li>
 *   <li>解析失败时返回 defaultValue</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * # messages_zh_CN.properties
 * response.success=操作成功
 * response.error=操作失败
 *
 * # messages_en_US.properties
 * response.success=Operation succeeded
 * response.error=Operation failed
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseResponse.MessageResolver
 */
public class SpringMessageResolver implements BaseResponse.MessageResolver {

    private final MessageSource messageSource;

    /**
     * 创建 SpringMessageResolver 实例
     *
     * @param messageSource Spring 消息源
     */
    public SpringMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public String resolve(String key, String defaultValue) {
        if (key == null || key.isEmpty()) {
            return defaultValue;
        }
        try {
            Locale locale = LocaleContextHolder.getLocale();
            String message = messageSource.getMessage(key, null, locale);
            return message != null ? message : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
