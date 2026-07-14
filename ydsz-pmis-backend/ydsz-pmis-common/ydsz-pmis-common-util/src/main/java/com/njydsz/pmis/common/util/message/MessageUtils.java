package com.njydsz.pmis.common.util.message;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

import com.njydsz.pmis.common.util.auth.AuthInfoUtils;
import com.njydsz.pmis.common.util.spring.SpringBeanUtils;
import com.njydsz.pmis.common.util.string.StringUtils;

/**
 * 消息工具类
 * 提供国际化消息处理的相关方法
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class MessageUtils {
    private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);

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

        try {
            Object messageSource = SpringBeanUtils.getBean("messageSource");
            if (messageSource == null) {
                logger.warn("MessageSource bean not found");
                return key;
            }
            return ((MessageSource) messageSource).getMessage(key, params, locale);
        } catch (Exception e) {
            logger.error("Get locale message error for key: {}", key, e);
        }
        return key;
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
            Object messageSource = SpringBeanUtils.getBean("messageSource");
            if (messageSource == null) {
                logger.warn("MessageSource bean not found");
                return defaultMsg;
            }
            return ((MessageSource) messageSource).getMessage(key, params, defaultMsg, locale);
        } catch (Exception e) {
            logger.error("Get locale message error for key: {}", key, e);
        }
        return defaultMsg;
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

            switch (userLanguage.toLowerCase()) {
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
