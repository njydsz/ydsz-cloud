package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseI18nConfiguration;

/**
 * Web 端国际化配置
 *
 * <p>继承 {@link BaseI18nConfiguration}，为 Web 端配置国际化消息文件路径。
 * 默认加载 {@code i18n.messages} 基础消息文件。
 *
 * @author ydsz-team
* 
 * @see BaseI18nConfiguration
 */
@AutoConfiguration
public class WebI18nConfiguration extends BaseI18nConfiguration {

    /**
     * 获取国际化消息文件基础路径
     *
     * @return 基础路径数组
     */
    @Override
    protected String[] getBasenames() {
        return new String[]{"i18n.messages"};
    }
}
