package com.njydsz.common.sentry.spi;

import java.util.List;

import com.njydsz.common.sentry.domain.LogEvent;

/**
 * "日志发布器 SPI"
 *
 * <p>"统一日志发布抽象，底层可切换 ELK（Logstash） / Loki / 双发。"
 *
 * @author ydsz-team
 * @since 1.5.0
 */
public interface LogPublisher {

    boolean publish(LogEvent event);

    default boolean publishBatch(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        boolean anySuccess = false;
        for (LogEvent event : events) {
            if (publish(event)) {
                anySuccess = true;
            }
        }
        return anySuccess;
    }

    boolean isAvailable();

    String getName();

    String getScheme();
}
