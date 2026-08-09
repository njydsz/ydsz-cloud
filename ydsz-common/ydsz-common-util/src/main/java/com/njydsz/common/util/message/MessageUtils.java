package com.njydsz.common.util.message;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

import com.njydsz.common.util.auth.AuthInfoUtils;
import com.njydsz.common.util.spring.SpringContextHolder;
import com.njydsz.common.util.string.StringUtils;

/**
 * 消息工具类
 * 提供国际化消息处理的相关方法
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class MessageUtils {
    private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);

    /**
     * 缓存的 MessageSource 实例
     *
     * <p>避免每次 getMessage 都查询 BeanFactory，首次成功解析后缓存。
     * volatile 保证多线程可见性。null 表示尚未解析或解析失败（不缓存"未找到"状态，
     * 以便下次调用可重新尝试解析）。
     */
    private static volatile MessageSource cachedMessageSource;

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
     * <p>首次调用时通过类型安全重载 {@code getBean("messageSource", MessageSource.class)}
     * 解析 Bean 并缓存，避免高频调用重复查询 BeanFactory。
     * 解析失败时不缓存（返回 null），以便下次调用可重新尝试。
     *
     * @return MessageSource 实例，未找到或上下文未初始化时返回 null
     */
    private static MessageSource resolveMessageSource() {
        MessageSource messageSource = cachedMessageSource;
        if (messageSource != null) {
            return messageSource;
        }
        try {
            // 类型安全重载，避免强转；getBean 找不到时会抛 NoSuchBeanDefinitionException
            messageSource = SpringContextHolder.getBean("messageSource", MessageSource.class);
            // 仅缓存成功解析的实例，避免缓存"未找到"状态
            cachedMessageSource = messageSource;
            return messageSource;
        } catch (Exception e) {
            logger.warn("MessageSource bean not found", e);
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
