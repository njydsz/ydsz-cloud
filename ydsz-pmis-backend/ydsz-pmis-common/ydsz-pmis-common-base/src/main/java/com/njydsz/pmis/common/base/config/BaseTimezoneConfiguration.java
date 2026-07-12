package com.njydsz.pmis.common.base.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * 时区配置基类（Web/App 共享）
 *
 * <p>通过 {@link PostConstruct} 在 Bean 初始化时强制将 JVM 默认时区设置为
 * {@code Asia/Shanghai}（UTC+8），保证全局时间一致性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseTimezoneConfiguration {

    /**
     * 初始化时区
     */
    @PostConstruct
    public void defaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}
