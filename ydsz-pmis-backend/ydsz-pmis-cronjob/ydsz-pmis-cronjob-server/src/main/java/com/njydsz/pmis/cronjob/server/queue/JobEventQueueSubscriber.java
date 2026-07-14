package com.njydsz.pmis.cronjob.server.queue;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.enums.QueueType;
import com.njydsz.pmis.common.queue.queue.IMessageQueue;
import com.njydsz.pmis.common.queue.queue.IMessageQueueProvider;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.cronjob.server.core.EventDrivenScheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务事件驱动调度队列订阅者
 *
 * <p>订阅 {@link JobQueueChannels#JOB_EVENT_TRIGGER} 通道的消息，
 * 收到消息后调用 {@link EventDrivenScheduler#triggerByEvent} 触发任务执行。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>在 {@link PostConstruct} 阶段启动异步订阅，应用启动即开始监听</li>
 *   <li>使用 common-queue 的 {@link IMessageSubscriber#subscribeAsync} 持续消费</li>
 *   <li>消息体为 JSON 格式：{"jobKey":"sync-job", "msgId":"msg-001", "payload":"{...}"}</li>
 *   <li>去重由 {@link EventDrivenScheduler} 内部 Redis SETNX 保证</li>
 *   <li>队列消费失败不影响应用启动，仅记录告警</li>
 * </ul>
 *
 * <p><b>与 EventDrivenScheduler 的关系：</b>
 * <p>EventDrivenScheduler 提供了 {@code triggerByEvent} 方法但原本需要外部 MQ Consumer 手动调用。
 * 本类作为 common-queue 的订阅者，自动消费消息并调用该方法，补全了事件驱动调度的消费端闭环。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobEventQueueSubscriber {

    private final IMessageQueueProvider messageQueueProvider;
    private final EventDrivenScheduler eventDrivenScheduler;

    private IMessageQueue eventTriggerQueue;
    private IMessageSubscriber eventTriggerSubscriber;

    @PostConstruct
    public void init() {
        try {
            eventTriggerQueue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
            eventTriggerSubscriber = eventTriggerQueue.createSubscriber(JobQueueChannels.JOB_EVENT_TRIGGER);
            eventTriggerSubscriber.subscribeAsync(this::handleEventTrigger);
            log.info("[JobQueue] 事件驱动调度订阅者已启动, channel={}", JobQueueChannels.JOB_EVENT_TRIGGER);
        } catch (Exception e) {
            log.warn("[JobQueue] 事件驱动调度订阅者启动失败, 事件触发功能不可用: {}", e.getMessage());
        }
    }

    /**
     * 处理事件触发消息
     *
     * @param message 队列消息
     */
    private void handleEventTrigger(QueueMessage message) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            Map<String, Object> payload = YdszJson.fromJsonToMap(message.getBody(), String.class, Object.class);
            String jobKey = payload.get("jobKey") == null ? null : String.valueOf(payload.get("jobKey"));
            String msgId = payload.get("msgId") == null ? null : String.valueOf(payload.get("msgId"));
            String payloadStr = payload.get("payload") == null ? null : String.valueOf(payload.get("payload"));

            if (jobKey == null || jobKey.isBlank()) {
                log.warn("[JobQueue] 事件触发消息缺少 jobKey, 跳过: traceId={}", message.getTraceId());
                return;
            }

            boolean triggered = eventDrivenScheduler.triggerByEvent(jobKey, msgId, payloadStr);
            log.info("[JobQueue] 事件触发任务: jobKey={} msgId={} triggered={} traceId={}",
                    jobKey, msgId, triggered, message.getTraceId());
        } catch (Exception e) {
            log.error("[JobQueue] 事件触发消息处理失败: traceId={} err={}",
                    message.getTraceId(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (eventTriggerSubscriber != null) {
            eventTriggerSubscriber.stop();
        }
        if (eventTriggerQueue != null) {
            eventTriggerQueue.close();
        }
        log.info("[JobQueue] 事件驱动调度订阅者已关闭");
    }
}
