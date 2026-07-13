package com.njydsz.pmis.common.sentry.spi;

import com.njydsz.pmis.common.sentry.domain.LogEvent;

/**
 * 日志发布器 SPI
 *
 * <p>统一日志发布抽象，底层可切换 ELK（Logstash） / Loki / 双发。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface LogPublisher {

    /**
     * 发布日志事件
     *
     * @param event 日志事件
     * @return 是否发布成功
     */
    boolean publish(LogEvent event);

    /**
     * 判断发布器是否可用
     */
    boolean isAvailable();

    /**
     * 获取发布器名称
     */
    String getName();

    /**
     * 获取发布器方案标识（elk / loki / dual）
     */
    String getScheme();
}
