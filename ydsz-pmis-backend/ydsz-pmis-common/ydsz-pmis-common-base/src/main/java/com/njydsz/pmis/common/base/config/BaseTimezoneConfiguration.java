package com.njydsz.pmis.common.base.config;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

/**
 * 时区配置基类（Web/App 共享）
 *
 * <p>通过 {@link PostConstruct} 在 Bean 初始化时强制将 JVM 默认时区设置为
 * {@code Asia/Shanghai}（UTC+8），保证全局时间一致性。
 *
 * <p><b>注意：</b>本配置会影响整个 JVM 的 {@link TimeZone#getDefault()}，
 * 需确保所有时间相关操作（{@code new Date()}、{@code System.currentTimeMillis()} 格式化等）
 * 均基于此设置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 */
public abstract class BaseTimezoneConfiguration {

    /**
     * 初始化时区
     *
     * <p>在 Bean 初始化完成后立即执行，确保应用启动后所有线程
     * 使用的都是 Asia/Shanghai 时区。
     */
    @PostConstruct
    public void defaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}
