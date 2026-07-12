package com.njydsz.pmis.common.web.config;

import com.njydsz.pmis.common.base.config.BaseTimezoneConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Web 端时区配置
 *
 * <p>继承 {@link BaseTimezoneConfiguration}，强制 JVM 默认时区为 Asia/Shanghai。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseTimezoneConfiguration
 */
@AutoConfiguration
public class WebTimezoneConfiguration extends BaseTimezoneConfiguration {
}