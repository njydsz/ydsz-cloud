package com.njydsz.pmis.message.server.event;

import com.njydsz.pmis.message.server.config.MessageProperties;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 死信告警事件（P1-4）�? *
 * <p>当指定时间窗口内某通道死信数量达到 {@link MessageProperties.DeadLetterAlertConfig#getThreshold()}
 * 时由 {@code MessageLogServiceImpl.markDead} 发布，由 {@link DeadLetterAlertListener}
 * 消费执行告警动作（日志告�?/ 钉钉机器�?/ 邮件等，可扩展）�? *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class DeadLetterAlertEvent extends ApplicationEvent {

    /** 触发告警的通道 */
    private final String channel;
    /** 窗口内当前死信数�?*/
    private final long currentCount;
    /** 告警阈�?*/
    private final int threshold;
    /** 统计窗口（分钟） */
    private final int windowMinutes;
    /** 触发时间 */
    private final LocalDateTime triggeredAt;

    /**
     * 构造死信告警事件�?     *
     * @param source       事件�?     * @param channel      触发告警的通道
     * @param currentCount 窗口内当前死信数�?     * @param threshold    告警阈�?     * @param windowMinutes 统计窗口（分钟）
     */
    public DeadLetterAlertEvent(Object source, String channel, long currentCount,
                                int threshold, int windowMinutes) {
        super(source);
        this.channel = channel;
        this.currentCount = currentCount;
        this.threshold = threshold;
        this.windowMinutes = windowMinutes;
        this.triggeredAt = LocalDateTime.now();
    }

    public String getChannel() {
        return channel;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public int getThreshold() {
        return threshold;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }
}
