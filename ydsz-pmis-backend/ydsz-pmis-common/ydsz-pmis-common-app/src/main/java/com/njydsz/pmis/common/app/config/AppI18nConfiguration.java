package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseI18nConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * App 端国际化配置
 *
 * <p>继承 {@link BaseI18nConfiguration}，使用 {@code i18n/app-messages} 作为基础资源名。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
public class AppI18nConfiguration extends BaseI18nConfiguration {

    @Override
    protected String[] getBasenames() {
        return new String[]{"i18n/app-messages"};
    }
}
