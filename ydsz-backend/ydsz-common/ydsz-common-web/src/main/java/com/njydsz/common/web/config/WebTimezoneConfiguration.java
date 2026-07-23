package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseTimezoneConfiguration;

/**
 * Web 端时区配置
 *
 * <p>继承 {@link BaseTimezoneConfiguration}，强制 JVM 默认时区为 Asia/Shanghai。
 *
 * @author ydsz-team
* 
 * @see BaseTimezoneConfiguration
 */
@AutoConfiguration
public class WebTimezoneConfiguration extends BaseTimezoneConfiguration {
}