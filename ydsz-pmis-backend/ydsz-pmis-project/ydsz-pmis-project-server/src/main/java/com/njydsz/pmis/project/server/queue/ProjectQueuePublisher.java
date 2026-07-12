package com.njydsz.pmis.project.server.queue;

import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.enums.QueueType;
import com.njydsz.pmis.common.queue.queue.IMessageQueue;
import com.njydsz.pmis.common.queue.queue.IMessageQueueProvider;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.project.server.engine.BudgetAlertEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 项目预算告警队列发布者
 *
 * <p>监听 Spring 内部 {@link BudgetAlertEvent}，将预算告警事件
 * 通过 common-queue 发布到 {@link ProjectQueueChannels#PROJECT_BUDGET_ALERT} 通道，
 * 使其他服务（如通知服务、预警中心）可以跨服务异步消费预算告警。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>使用 {@link EventListener} + {@link Async} 异步监听，不影响主流程事务</li>
 *   <li>消息体为 BudgetAlertEvent 的 JSON 序列化</li>
 *   <li>消息头携带 alertLevel（YELLOW/RED），便于消费者做消息过滤</li>
 *   <li>队列发布失败仅记录日志，Spring 内部事件已保证本服务内通信</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectQueuePublisher {

    private final IMessageQueueProvider messageQueueProvider;
    private IMessageQueue budgetAlertQueue;
    private IMessagePublisher budgetAlertPublisher;

    @PostConstruct
    public void init() {
        try {
            budgetAlertQueue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
            budgetAlertPublisher = budgetAlertQueue.createPublisher(ProjectQueueChannels.PROJECT_BUDGET_ALERT);
            log.info("[ProjectQueue] 预算告警队列发布者已启动, channel={}", ProjectQueueChannels.PROJECT_BUDGET_ALERT);
        } catch (Exception e) {
            log.warn("[ProjectQueue] 预算告警队列发布者启动失败: {}", e.getMessage());
        }
    }

    /**
     * 异步监听预算告警事件并发布到消息队列
     *
     * @param event 预算告警事件
     */
    @Async("auditExecutor")
    @EventListener
    public void onBudgetAlert(BudgetAlertEvent event) {
        if (budgetAlertPublisher == null || event == null) {
            return;
        }
        try {
            String json = JsonUtils.toJson(event);
            QueueMessage message = QueueMessage.of(json);
            message.addHeader("alertLevel", event.getLevel() == null ? "UNKNOWN" : event.getLevel().name());
            message.addHeader("initiationId", event.getInitiationId());
            message.addHeader("projectCode", event.getProjectCode());
            message.addHeader("source", "project");

            budgetAlertPublisher.publish(message);
            log.debug("[ProjectQueue] 预算告警已发布到队列: project={} level={}",
                    event.getProjectCode(), event.getLevel());
        } catch (Exception e) {
            log.warn("[ProjectQueue] 预算告警发布到队列失败: project={} err={}",
                    event.getProjectCode(), e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (budgetAlertPublisher != null) {
            budgetAlertPublisher.close();
        }
        if (budgetAlertQueue != null) {
            budgetAlertQueue.close();
        }
        log.info("[ProjectQueue] 预算告警队列发布者已关闭");
    }
}
