package com.njydsz.pmis.common.notify.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通知国际化服务（P3-14）
 *
 * <p>根据用户的语言偏好加载对应的通知文案，支持多语言邮件主题和内容模板。
 *
 * <p><b>资源文件结构：</b>
 * <pre>
 * resources/
 *   i18n/
 *     notify_messages_zh_CN.properties
 *     notify_messages_en_US.properties
 *     notify_messages_ja_JP.properties
 * </pre>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * i18nService.getMessage("email.subject.approval", "zh_CN");
 * i18nService.getMessage("email.content.greeting", "en_US", Map.of("name", "John"));
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NotifyI18nService {

	private static final Logger log = LoggerFactory.getLogger(NotifyI18nService.class);

	private static final String BASE_NAME = "i18n/notify_messages";
	private static final String DEFAULT_LOCALE = "zh_CN";

	private final ConcurrentMap<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();

	/**
	 * 获取国际化消息
	 *
	 * @param key    消息键
	 * @param locale 语言代码（如 zh_CN、en_US），null 时使用默认语言
	 * @return 国际化消息文本
	 */
	public String getMessage(String key, String locale) {
		String effectiveLocale = locale != null ? locale : DEFAULT_LOCALE;
		ResourceBundle bundle = getBundle(effectiveLocale);
		if (bundle != null && bundle.containsKey(key)) {
			return bundle.getString(key);
		}
		// 降级到默认语言
		if (!DEFAULT_LOCALE.equals(effectiveLocale)) {
			ResourceBundle defaultBundle = getBundle(DEFAULT_LOCALE);
			if (defaultBundle != null && defaultBundle.containsKey(key)) {
				return defaultBundle.getString(key);
			}
		}
		// 返回 key 本身
		return key;
	}

	/**
	 * 获取国际化消息（支持参数替换）
	 *
	 * @param key    消息键
	 * @param locale 语言代码
	 * @param params 参数映射
	 * @return 格式化后的国际化消息
	 */
	public String getMessage(String key, String locale, Map<String, Object> params) {
		String message = getMessage(key, locale);
		if (params != null && !params.isEmpty()) {
			for (Map.Entry<String, Object> entry : params.entrySet()) {
				message = message.replace("${" + entry.getKey() + "}",
						String.valueOf(entry.getValue()));
			}
		}
		return message;
	}

	/**
	 * 获取或加载 ResourceBundle
	 */
	private ResourceBundle getBundle(String locale) {
		return bundleCache.computeIfAbsent(locale, loc -> {
			try {
				String[] parts = loc.split("_");
				Locale javaLocale = parts.length >= 2
						? Locale.of(parts[0], parts[1])
						: Locale.of(parts[0]);
				return ResourceBundle.getBundle(BASE_NAME, javaLocale,
						getClass().getClassLoader());
			} catch (Exception e) {
				log.debug("[NotifyI18nService] 资源文件未找到: locale={}", loc);
				return null;
			}
		});
	}

	/**
	 * 清除缓存
	 */
	public void clearCache() {
		bundleCache.clear();
	}
}
