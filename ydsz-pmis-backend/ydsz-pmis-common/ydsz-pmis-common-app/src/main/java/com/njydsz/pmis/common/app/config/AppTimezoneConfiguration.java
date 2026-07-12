package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseTimezoneConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * App 端时区配置
 *
 * <p>继承 {@link BaseTimezoneConfiguration}，强制 JVM 默认时区为 {@code Asia/Shanghai}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
public class AppTimezoneConfiguration extends BaseTimezoneConfiguration {
}
