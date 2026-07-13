package com.njydsz.pmis.message.server.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 死信告警事件监听器（P1-4）。
 *
 * <p>当前实现为日志告警（WARN 级别），可扩展为钉钉机器人 / 邮件 / 站内告警等通道。
 * 监听器同步执行且不抛异常，避免影响死信标记主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DeadLetterAlertListener {

    /**
     * 处理死信告警事件：输出告警日志。
     *
     * @param event 死信告警事件
     */
    @EventListener
    public void onDeadLetterAlert(DeadLetterAlertEvent event) {
        try {
            log.warn("[DeadLetterAlert] 死信告警触发: channel={} currentCount={} threshold={} window={}min triggeredAt={}",
                    event.getChannel(),
                    event.getCurrentCount(),
                    event.getThreshold(),
                    event.getWindowMinutes(),
                    event.getTriggeredAt());
            // 扩展告警通道（钉钉机器人 / 邮件 / 站内告警）时在此追加发送逻辑,当前仅日志告警
        } catch (Exception e) {
            log.error("[DeadLetterAlert] 告警处理异常,不影响主流程: {}", e.getMessage(), e);
        }
    }
}
