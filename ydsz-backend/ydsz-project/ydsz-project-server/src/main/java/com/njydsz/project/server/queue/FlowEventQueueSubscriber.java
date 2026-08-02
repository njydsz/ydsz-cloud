package com.njydsz.project.server.queue;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.json.YdszJson;
import com.njydsz.project.server.service.ProjectInitiationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流事件队列订阅者
 *
 * <p>订阅 {@code ydsz:flow:event} 通道的消息，接收 workflow 模块发布的
 * 业务事件（如 {@code INITIATION_STATUS_SYNC}），实现 project↔workflow
 * 跨服务异步联动闭环。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>在 {@link PostConstruct} 阶段启动异步订阅，应用启动即开始监听</li>
 *   <li>使用 common-queue 的 {@link IMessageSubscriber#subscribeAsync} 持续消费</li>
 *   <li>消息体为 JSON 格式，包含 eventType、initiationId、action 等字段</li>
 *   <li>仅处理 {@code INITIATION_STATUS_SYNC} 事件，其他事件类型静默跳过</li>
 *   <li>消费失败不影响应用启动，仅记录告警</li>
 *   <li>通过 {@link ProjectInitiationService#syncWorkflowStatus} 同步立项状态</li>
 * </ul>
 *
 * <p><b>与 workflow 模块的关系：</b>
 * <p>workflow 模块的 {@code FlowQueuePublisher} 将工作流生命周期事件
 * （实例启动/完成/驳回）发布到 {@code ydsz:flow:event} 通道。
 * 本类作为 common-queue 的订阅者，自动消费消息并调用
 * {@code ProjectInitiationService.syncWorkflowStatus} 更新立项状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowEventQueueSubscriber {

    /**
     * 工作流事件通道名称（与 {@code FlowQueueChannels.FLOW_EVENT} 保持一致）
     */
    static final String FLOW_EVENT_CHANNEL = "ydsz:flow:event";

    /**
     * 立项状态联动事件类型（与 {@code ProjectInitiationFlowListener.EVENT_INITIATION_STATUS_SYNC} 保持一致）
     */
    private static final String EVENT_INITIATION_STATUS_SYNC = "INITIATION_STATUS_SYNC";

    private final IMessageQueueProvider messageQueueProvider;
    private final ProjectInitiationService initiationService;

    private IMessageQueue flowEventQueue;
    private IMessageSubscriber flowEventSubscriber;

    /**
     * 应用启动后异步订阅工作流事件通道。
     *
     * <p>创建 {@link QueueType#STREAM} 类型队列并在 {@link #FLOW_EVENT_CHANNEL} 上注册
     * 异步消费者，使 project 服务启动完成即可接收 workflow 侧推送的立项状态变更事件，
     * 无需等待首次业务请求触发。
     *
     * <p><b>失败降级：</b>队列基础设施（Redis Stream）不可用时<b>不向外抛异常</b>，
     * 仅记录 warn 日志并放弃本次订阅，保证应用能正常完成启动。此时 project↔workflow
     * 的异步联动链路失效，立项状态需依赖定时任务或人工补偿，且本方法不会自动重试。
     *
     * <p><b>线程模型：</b>{@code subscribeAsync} 在独立消费线程中回调
     * {@link #handleFlowEvent}，本方法不会阻塞 Spring 容器初始化流程。
     */
    @PostConstruct
    public void init() {
        try {
            flowEventQueue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
            flowEventSubscriber = flowEventQueue.createSubscriber(FLOW_EVENT_CHANNEL);
            flowEventSubscriber.subscribeAsync(this::handleFlowEvent);
            log.info("[FlowEventSubscriber] 工作流事件订阅者已启动, channel={}", FLOW_EVENT_CHANNEL);
        } catch (Exception e) {
            log.warn("[FlowEventSubscriber] 工作流事件订阅者启动失败, project↔workflow 联动功能不可用: {}",
                    e.getMessage());
        }
    }

    /**
     * 处理工作流事件消息
     *
     * <p>解析消息体 JSON，根据 eventType 分发到对应处理器。
     * 当前仅处理 {@code INITIATION_STATUS_SYNC} 事件。
     *
     * @param message 队列消息
     */
    private void handleFlowEvent(QueueMessage message) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            Map<String, Object> payload = YdszJson.fromJsonToMap(
                    message.getBody(), String.class, Object.class);
            if (payload == null) {
                return;
            }

            String eventType = payload.get("eventType") == null
                    ? null : String.valueOf(payload.get("eventType"));
            if (!EVENT_INITIATION_STATUS_SYNC.equals(eventType)) {
                // 非立项状态联动事件，静默跳过（如 TASK_CREATED、INSTANCE_STARTED 等）
                return;
            }

            handleInitiationStatusSync(payload, message.getTraceId());
        } catch (Exception e) {
            log.error("[FlowEventSubscriber] 工作流事件处理失败: traceId={} err={}",
                    message.getTraceId(), e.getMessage(), e);
        }
    }

    /**
     * 处理立项状态联动事件
     *
     * <p>从消息体中提取 initiationId 和 action，调用
     * {@link ProjectInitiationService#syncWorkflowStatus} 同步立项状态。
     *
     * @param payload 消息体解析后的 Map
     * @param traceId 消息追踪 ID
     */
    private void handleInitiationStatusSync(Map<String, Object> payload, String traceId) {
        String initiationId = payload.get("initiationId") == null
                ? null : String.valueOf(payload.get("initiationId"));
        String action = payload.get("action") == null
                ? null : String.valueOf(payload.get("action"));

        if (!StringUtils.hasText(initiationId)) {
            log.warn("[FlowEventSubscriber] INITIATION_STATUS_SYNC 事件缺少 initiationId, traceId={}",
                    traceId);
            return;
        }
        if (!StringUtils.hasText(action)) {
            log.warn("[FlowEventSubscriber] INITIATION_STATUS_SYNC 事件缺少 action, initiationId={} traceId={}",
                    initiationId, traceId);
            return;
        }

        boolean synced = initiationService.syncWorkflowStatus(initiationId, action);
        log.info("[FlowEventSubscriber] 立项状态联动处理完成: initiationId={} action={} synced={} traceId={}",
                initiationId, action, synced, traceId);
    }

    /**
     * 应用关闭前停止消费并释放队列连接。
     *
     * <p>严格按「先停订阅者、后关队列」的顺序释放：若先关闭队列，消费线程可能持有
     * 已失效的连接继续拉取消息，导致停机阶段刷出大量异常日志。
     *
     * <p>当 {@link #init()} 因基础设施异常降级时，两个字段可能为 {@code null}，
     * 故均做空值保护；方法可重复调用（幂等），不会因重复关闭而失败。
     */
    @PreDestroy
    public void destroy() {
        if (flowEventSubscriber != null) {
            flowEventSubscriber.stop();
        }
        if (flowEventQueue != null) {
            flowEventQueue.close();
        }
        log.info("[FlowEventSubscriber] 工作流事件订阅者已关闭");
    }
}