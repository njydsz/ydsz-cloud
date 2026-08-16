package com.njydsz.common.sentry.spi;

import java.util.List;
import com.njydsz.common.sentry.domain.AlertEvent;

/**
 * 告警发布器 SPI
 *
 * <p>统一告警发布抽象，支持告警收敛、去重和静默。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AlertPublisher {

    /**
     * 发布告警事件
     *
     * @param event 告警事件
     * @return 是否发布成功（经过收敛后可能被丢弃）
     */
    boolean publish(AlertEvent event);

    /**
     * 批量发布告警事件
     *
     * @param events 告警事件列表
     * @return 实际发布的数量
     */
    default int publishBatch(List<AlertEvent> events) {
        int count = 0;
        for (AlertEvent event : events) {
            if (publish(event)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断发布器是否可用
     */
    boolean isAvailable();

    /**
     * 获取发布器名称
     */
    String getName();
}
