package com.njydsz.pmis.cronjob.server.queue;

import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.enums.QueueType;
import com.njydsz.pmis.common.queue.queue.IMessageQueue;
import com.njydsz.pmis.common.queue.queue.IMessageQueueProvider;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.cronjob.server.core.TaskCompletedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务执行结果队列发布者
 *
 * <p>监听 Spring 内部 {@link TaskCompletedEvent}，将任务执行结果
 * 通过 common-queue 发布到 {@link JobQueueChannels#JOB_RESULT} 通道，
 * 使其他服务可以异步感知任务执行状态。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>project 模块订阅此通道，在数据同步任务完成后触发报表刷新</li>
 *   <li>workflow 模块订阅此通道，在 DAG 节点任务完成后推进流程</li>
 *   <li>监控服务订阅此通道，统计任务成功率和执行时长</li>
 * </ul>
 *
 * <p><b>消息格式：</b>
 * <pre>{@code
 * {
 *   "jobId": "123",
 *   "jobKey": "data-sync-job",
 *   "success": true,
 *   "logId": "456789"
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobResultQueuePublisher {

    private final IMessageQueueProvider messageQueueProvider;
    private IMessageQueue resultQueue;
    private IMessagePublisher resultPublisher;

    @PostConstruct
    public void init() {
        try {
            resultQueue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
            resultPublisher = resultQueue.createPublisher(JobQueueChannels.JOB_RESULT);
            log.info("[JobQueue] 任务结果队列发布者已启动, channel={}", JobQueueChannels.JOB_RESULT);
        } catch (Exception e) {
            log.warn("[JobQueue] 任务结果队列发布者启动失败: {}", e.getMessage());
        }
    }

    /**
     * 异步监听任务完成事件并发布到消息队列
     *
     * @param event 任务完成事件
     */
    @Async("auditExecutor")
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (resultPublisher == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>(4);
            payload.put("jobId", event.jobId());
            payload.put("jobKey", event.jobKey());
            payload.put("success", event.success());
            payload.put("logId", event.logId());

            QueueMessage message = QueueMessage.of(JsonUtils.toJson(payload));
            message.addHeader("jobKey", event.jobKey());
            message.addHeader("success", String.valueOf(event.success()));
            message.addHeader("source", "cronjob");

            resultPublisher.publish(message);
            log.debug("[JobQueue] 任务结果已发布到队列: jobKey={} success={}",
                    event.jobKey(), event.success());
        } catch (Exception e) {
            log.warn("[JobQueue] 任务结果发布到队列失败: jobKey={} err={}",
                    event.jobKey(), e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (resultPublisher != null) {
            resultPublisher.close();
        }
        if (resultQueue != null) {
            resultQueue.close();
        }
        log.info("[JobQueue] 任务结果队列发布者已关闭");
    }
}
