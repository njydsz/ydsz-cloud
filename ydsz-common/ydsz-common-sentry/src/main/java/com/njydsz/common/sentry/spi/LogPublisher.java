package com.njydsz.common.sentry.spi;

import java.util.List;
import com.njydsz.common.sentry.domain.LogEvent;

/**
 * "日志发布器 SPI"
 *
 * <p>"统一日志发布抽象，底层可切换 ELK（Logstash） / Loki / 双发。"
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LogPublisher {

    /**
     * 发布单条日志事件
     *
     * @param event 日志事件
     * @return 是否发布成功
     */
    boolean publish(LogEvent event);

    /**
     * 批量发布日志事件
     *
     * @param events 日志事件列表
     * @return 是否全部发布成功（至少一条成功即返回 true）
     */
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

    /**
     * 判断日志发布器是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取日志发布器名称
     *
     * @return 发布器名称
     */
    String getName();

    /**
     * 获取日志发布器协议方案
     *
     * @return 协议方案标识
     */
    String getScheme();
}
