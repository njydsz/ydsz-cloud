package com.njydsz.pmis.common.notify.i18n;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.notify.preference.NotifyPreference;
import com.njydsz.pmis.common.notify.preference.NotifyPreferenceManager;

/**
 * 通知国际化语言解析器（P3-3）
 *
 * <p>按优先级解析用户语言偏好：
 * <ol>
 *   <li>用户通知偏好中配置的语言</li>
 *   <li>请求头 Accept-Language（站内信场景）</li>
 *   <li>系统默认语言（zh_CN）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class NotifyI18nResolver {

    private static final Logger log = LoggerFactory.getLogger(NotifyI18nResolver.class);

    private static final String DEFAULT_LOCALE = "zh_CN";

    private final NotifyPreferenceManager preferenceManager;
    private final NotifyI18nService i18nService;

    /**
     * 构造国际化解析器
     *
     * @param preferenceManager 用户偏好管理器
     * @param i18nService       国际化服务
     */
    public NotifyI18nResolver(NotifyPreferenceManager preferenceManager, NotifyI18nService i18nService) {
        this.preferenceManager = preferenceManager;
        this.i18nService = i18nService;
    }

    /**
     * 解析用户语言偏好
     *
     * @param userId      用户ID（可为 null）
     * @param acceptLanguage 请求头 Accept-Language 值（可为 null）
     * @return 语言代码（如 zh_CN、en_US）
     */
    public String resolveLanguage(String userId, String acceptLanguage) {
        // 1. 从用户通知偏好获取
        if (userId != null && !userId.isEmpty() && preferenceManager != null) {
            try {
                NotifyPreference pref = preferenceManager.getPreference(userId);
                if (pref != null) {
                    String lang = getUserLanguageFromPreference(pref);
                    if (lang != null && !lang.isEmpty()) {
                        log.debug("[NotifyI18nResolver] 从用户偏好获取语言: userId={}, lang={}", userId, lang);
                        return lang;
                    }
                }
            } catch (Exception e) {
                log.debug("[NotifyI18nResolver] 获取用户偏好失败: userId={}, error={}", userId, e.getMessage());
            }
        }

        // 2. 从 Accept-Language 解析
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            String lang = parseAcceptLanguage(acceptLanguage);
            if (lang != null) {
                log.debug("[NotifyI18nResolver] 从 Accept-Language 获取语言: {}", lang);
                return lang;
            }
        }

        // 3. 默认语言
        return DEFAULT_LOCALE;
    }

    /**
     * 获取国际化消息（自动解析用户语言）
     *
     * @param key            消息键
     * @param userId         用户ID
     * @param acceptLanguage 请求头 Accept-Language
     * @return 国际化消息
     */
    public String getMessage(String key, String userId, String acceptLanguage) {
        String lang = resolveLanguage(userId, acceptLanguage);
        return i18nService.getMessage(key, lang);
    }

    /**
     * 获取国际化消息（带参数替换，自动解析用户语言）
     *
     * @param key            消息键
     * @param userId         用户ID
     * @param acceptLanguage 请求头 Accept-Language
     * @param params         参数
     * @return 国际化消息
     */
    public String getMessage(String key, String userId, String acceptLanguage,
                             java.util.Map<String, Object> params) {
        String lang = resolveLanguage(userId, acceptLanguage);
        return i18nService.getMessage(key, lang, params);
    }

    /**
     * 从用户偏好中获取语言设置
     *
     * @param pref 用户偏好
     * @return 语言代码，未设置返回 null
     */
    private String getUserLanguageFromPreference(NotifyPreference pref) {
        return pref.getLanguage();
    }

    /**
     * 解析 Accept-Language 请求头
     *
     * @param acceptLanguage Accept-Language 值
     * @return 语言代码（如 zh_CN、en_US），无法解析返回 null
     */
    private String parseAcceptLanguage(String acceptLanguage) {
        try {
            Locale locale = Locale.forLanguageTag(acceptLanguage.split(",")[0].trim().split(";")[0].trim());
            String lang = locale.getLanguage();
            String country = locale.getCountry();
            if (!lang.isEmpty() && !country.isEmpty()) {
                return lang + "_" + country;
            }
            if (!lang.isEmpty()) {
                // 根据语言推断默认国家
                return switch (lang) {
                    case "zh" -> "zh_CN";
                    case "en" -> "en_US";
                    case "ja" -> "ja_JP";
                    default -> lang;
                };
            }
        } catch (Exception e) {
            log.debug("[NotifyI18nResolver] 解析 Accept-Language 失败: {}", acceptLanguage);
        }
        return null;
    }
}
