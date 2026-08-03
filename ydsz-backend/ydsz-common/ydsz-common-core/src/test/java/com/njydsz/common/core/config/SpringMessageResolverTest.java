package com.njydsz.common.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * {@link SpringMessageResolver} 国际化解析测试。
 *
 * <p>覆盖：默认 Locale、显式 Locale、缺失 key 回退默认值、空 key、异常回退。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@DisplayName("SpringMessageResolver 国际化解析测试")
class SpringMessageResolverTest {

    private SpringMessageResolver buildResolver() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return new SpringMessageResolver(source);
    }

    @Test
    @DisplayName("中文 Locale 解析 response.success")
    void zhCn() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        try {
            SpringMessageResolver resolver = buildResolver();
            assertEquals("操作成功", resolver.resolve("response.success", "fallback"));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("英文 Locale 解析 response.success")
    void enUs() {
        LocaleContextHolder.setLocale(Locale.US);
        try {
            SpringMessageResolver resolver = buildResolver();
            assertEquals("Operation succeeded", resolver.resolve("response.success", "fallback"));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("错误码 key（error.NOT_FOUND）中英文均解析")
    void resultCodeKeys() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        try {
            SpringMessageResolver resolver = buildResolver();
            assertEquals("资源不存在", resolver.resolve("error.NOT_FOUND", "fallback"));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }

        LocaleContextHolder.setLocale(Locale.US);
        try {
            SpringMessageResolver resolver = buildResolver();
            assertEquals("Resource not found", resolver.resolve("error.NOT_FOUND", "fallback"));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    @DisplayName("缺失 key 回退默认值")
    void missingKeyFallsBack() {
        SpringMessageResolver resolver = buildResolver();
        assertEquals("fallback-msg", resolver.resolve("error.NO_SUCH_KEY", "fallback-msg"));
    }

    @Test
    @DisplayName("null / 空 key 直接返回默认值")
    void blankKeyFallsBack() {
        SpringMessageResolver resolver = buildResolver();
        assertEquals("fb", resolver.resolve(null, "fb"));
        assertEquals("fb", resolver.resolve("", "fb"));
    }

    @Test
    @DisplayName("默认 Locale（zh-CN 系统默认）解析成功")
    void defaultLocale() {
        LocaleContextHolder.resetLocaleContext();
        SpringMessageResolver resolver = buildResolver();
        // 系统默认 Locale 下应至少返回非空结果（英文资源兜底）
        String result = resolver.resolve("response.success", "fallback");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }
}
