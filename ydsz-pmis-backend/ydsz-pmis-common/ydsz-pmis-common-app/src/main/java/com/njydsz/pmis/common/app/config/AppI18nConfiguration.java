package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseI18nConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * App 端国际化配置
 *
 * <p>继承 {@link BaseI18nConfiguration}，使用 {@code i18n/app-messages}
 * 作为基础资源名（basename）加载 App 端专属的国际化资源文件。
 *
 * <p><b>资源文件查找路径：</b>
 * <ul>
 *   <li>classpath:i18n/app-messages.properties（默认）</li>
 *   <li>classpath:i18n/app-messages_zh_CN.properties（中文）</li>
 *   <li>classpath:i18n/app-messages_en_US.properties（英文）</li>
 * </ul>
 *
 * <p><b>线程安全性：</b>无状态配置类，线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see BaseI18nConfiguration
 */
@AutoConfiguration
public class AppI18nConfiguration extends BaseI18nConfiguration {

    /**
     * 返回 i18n 资源文件的基础名称数组
     *
     * @return 基础名称数组，默认 {@code ["i18n/app-messages"]}
     */
    @Override
    protected String[] getBasenames() {
        return new String[]{"i18n/app-messages"};
    }
}
