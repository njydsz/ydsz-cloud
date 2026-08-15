package com.njydsz.common.util.message;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;

import com.njydsz.common.util.auth.AuthInfoUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * 消息工具类
 * 提供国际化消息处理的相关方法
 *
 * <p>Spring 环境下通过 {@link MessageSourceConfiguration} 注入 {@link ObjectProvider}，
 * 非 Spring 环境下静态方法仍可通过降级路径返回默认消息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class MessageUtils {
    private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);

    private MessageUtils() {
        throw new UnsupportedOperationException("MessageUtils is a utility class and cannot be instantiated");
    }

    /**
     * 可选的 MessageSource 提供者（Spring 环境下由 {@link MessageSourceConfiguration} 注入）。
     * <p>使用 {@link ObjectProvider} 而非直接引用，避免启动期缺少 MessageSource 时抛出异常。
     */
    private static volatile ObjectProvider<MessageSource> messageSourceProvider;

    /**
     * 缓存的 MessageSource 实例（已成功解析后缓存，避免重复 getIfAvailable）。
     *
     * <p>volatile 保证多线程可见性。null 表示首次解析尚未完成或解析失败。
     */
    private static volatile MessageSource cachedMessageSource;

    /**
     * 注入 MessageSource 提供者（Spring 容器启动后调用）。
     *
     * <p>由 {@link MessageSourceConfiguration} 通过 {@code @Configuration} 注册，
     * 将 {@code ObjectProvider<MessageSource>} 传入本类。
     *
     * @param provider MessageSource 提供者；允许 null（此时使用降级路径）
     * @since 2.2.0
     */
    public static void setMessageSourceProvider(ObjectProvider<MessageSource> provider) {
        messageSourceProvider = provider;
        // 注入后清空缓存，以便首次调用重新解析（可能在 Spring 上下文就绪后 MessageSource 才可用）
        cachedMessageSource = null;
    }

    /**
     * 获得多语言内容，默认根据当前用户的 Locale 获取
     *
     * @param key 多语言 key
     * @return 翻译后的值，如获取不到则返回 key
     */
    public static String getMessage(String key) {
        if (StringUtils.isBlank(key)) {
            return key;
        }
        return getMessage(key, new Object[]{});
    }

    /**
     * 获得多语言内容--带参数，默认根据当前用户的 Locale 获取
     *
     * @param key    多语言 key
     * @param params 参数
     * @return 翻译后的值，如获取不到则返回 key
     */
    public static String getMessage(String key, Object[] params) {
        if (StringUtils.isBlank(key)) {
            return key;
        }

        Locale locale = getLocale();
        if (params == null) {
            params = new Object[]{};
        }
        return getMessage(locale, key, params);
    }

    /**
     * 获取多语言内容--带默认值，默认根据当前用户的 Locale 获取
     *
     * @param key        多语言 key
     * @param defaultMsg 默认返回值
     * @return 翻译后的值，获取不到返回默认值
     */
    public static String getMessage(String key, String defaultMsg) {
        if (StringUtils.isBlank(key)) {
            return defaultMsg;
        }
        return getMessage(key, new Object[]{}, defaultMsg);
    }

    /**
     * 获得多语言内容--带参数--带默认值，默认根据当前用户的 Locale 获取
     *
     * @param key        多语言 key
     * @param params     参数
     * @param defaultMsg 默认返回值
     * @return 翻译后的值，获取不到返回默认值
     */
    public static String getMessage(String key, Object[] params, String defaultMsg) {
        if (StringUtils.isBlank(key)) {
            return defaultMsg;
        }

        Locale locale = getLocale();
        return getMessage(locale, key, params, defaultMsg);
    }

    /**
     * 不带参数
     *
     * @param locale 语言信息
     * @param key    多语言 key
     * @return 翻译后的值
     */
    public static String getMessage(Locale locale, String key) {
        if (StringUtils.isBlank(key)) {
            return key;
        }
        return getMessage(locale, key, new Object[]{});
    }

    /**
     * 获取多语言数据
     *
     * @param locale 语言信息
     * @param key    多语言 key
     * @param params 参数
     * @return 翻译后的值
     */
    public static String getMessage(Locale locale, String key, Object[] params) {
        if (StringUtils.isBlank(key)) {
            return key;
        }
        // 统一语义：未找到时返回 key（即 defaultMsg = key），不依赖 catch 吞 NoSuchMessageException
        return getMessage(locale, key, params, key);
    }

    /**
     * 获取多语言数据
     *
     * @param locale     语言信息
     * @param key        多语言 key
     * @param params     参数
     * @param defaultMsg 默认返回值
     * @return 翻译后的值
     */
    public static String getMessage(Locale locale, String key, Object[] params, String defaultMsg) {
        if (StringUtils.isBlank(key)) {
            return defaultMsg;
        }

        try {
            MessageSource messageSource = resolveMessageSource();
            if (messageSource == null) {
                return defaultMsg;
            }
            // 使用带 defaultMessage 的重载，未找到时返回 defaultMsg 而非抛 NoSuchMessageException
            return messageSource.getMessage(key, params, defaultMsg, locale);
        } catch (Exception e) {
            logger.error("Get locale message error for key: {}", key, e);
        }
        return defaultMsg;
    }

    /**
     * 解析并缓存 MessageSource
     *
     * <p>使用已注入的 {@link ObjectProvider}（Spring 环境下由 {@link MessageSourceConfiguration} 注入）
     * 解析并缓存 MessageSource 实例。解析成功时缓存结果，解析失败时返回 null 以便下次调用可重新尝试。
     *
     * @return MessageSource 实例，未找到或上下文未初始化时返回 null
     */
    private static MessageSource resolveMessageSource() {
        MessageSource messageSource = cachedMessageSource;
        if (messageSource != null) {
            return messageSource;
        }
        try {
            ObjectProvider<MessageSource> provider = messageSourceProvider;
            if (provider != null) {
                messageSource = provider.getIfAvailable();
                if (messageSource != null) {
                    cachedMessageSource = messageSource;
                    return messageSource;
                }
            }
            return null;
        } catch (Exception e) {
            logger.warn("MessageSource bean not found: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前语言环境
     *
     * @return Locale 当前语言环境
     */
    private static Locale getLocale() {
        try {
            String userLanguage = AuthInfoUtils.getUserLanguage();
            if (StringUtils.isBlank(userLanguage)) {
                return Locale.getDefault();
            }

            // 使用 Locale.ROOT 避免土耳其语 locale bug（如 "I".toLowerCase() 在 tr 下变为 "ı"）
            switch (userLanguage.toLowerCase(Locale.ROOT)) {
                case "zh_cn":
                case "zh-cn":
                case "zh":
                    return Locale.SIMPLIFIED_CHINESE;
                case "en":
                case "en_us":
                case "en-us":
                    return Locale.ENGLISH;
                default:
                    return Locale.getDefault();
            }
        } catch (Exception e) {
            logger.warn("Failed to get user language, using default locale", e);
            return Locale.getDefault();
        }
    }
}





