package com.njydsz.pmis.common.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.pmis.common.base.config.BaseTimezoneConfiguration;

/**
 * App 端时区配置
 *
 * <p>继承 {@link BaseTimezoneConfiguration}，强制 JVM 默认时区为 {@code Asia/Shanghai}，
 * 避免分布式环境或不同主机时区差异引发的日期/时间字段偏差。
 *
 * <p><b>线程安全性：</b>{@code TimeZone.setDefault} 属于全局副作用，本类应仅在应用启动时执行一次。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see BaseTimezoneConfiguration
 */
@AutoConfiguration
public class AppTimezoneConfiguration extends BaseTimezoneConfiguration {
}