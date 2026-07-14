package com.njydsz.pmis.project.server.queue;

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
import com.njydsz.pmis.project.server.service.InitiationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流事件队列订阅者（project 模块消费方）
 *
 * <p>订阅 {@link ProjectQueueChannels#FLOW_EVENT} 通道的工作流事件消息，
 * 根据事件类型调用 {@link InitiationService} 的状态联动方法，
 * 实现 workflow → project 的跨服务异步状态联动。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>作为 Feign 调用（InitiationFeignClient）的补充路径，提供消息队列可靠投递能力</li>
 *   <li>在 {@link PostConstruct} 阶段启动异步订阅，应用启动即开始监听</li>
 *   <li>消息体为 JSON 格式：{"eventType":"INSTANCE_COMPLETED", "instanceId":"...", "data":{...}}</li>
 *   <li>消息头携带 eventType，用于快速过滤</li>
 *   <li>消费失败仅记录日志，不抛出异常（消息会根据队列模式重试或进入死信队列）</li>
 * </ul>
 *
 * <p><b>事件类型 → 立项状态映射：</b>
 * <ul>
 *   <li>INSTANCE_STARTED → markProcessing（审批中）</li>
 *   <li>INSTANCE_COMPLETED → markApproved（已批准）</li>
 *   <li>INSTANCE_REJECTED → markRejected（已驳回）</li>
 * </ul>
 *
 * <p><b>与现有 Feign 调用的关系：</b>
 * <p>workflow 模块的 ProjectInitiationFlowListener 通过 Feign 调用 InitiationFeignClient
 * 进行立项状态联动。本订阅者作为消息队列补充路径：
 * <ul>
 *   <li>当 Feign 调用成功时，队列消息仅做幂等校验（状态已更新则跳过）</li>
 *   <li>当 Feign 调用失败时，队列消息提供补偿机制（确保最终一致性）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventQueueSubscriber {

    private static final String INIT_BIZ_KEY_PREFIX = "PMIS_INIT_";

    private final IMessageQueueProvider messageQueueProvider;
    private final InitiationService initiationService;

    private IMessageQueue flowEventQueue;
    private IMessageSubscriber flowEventSubscriber;

    @PostConstruct
    public void init() {
        try {
            flowEventQueue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
            flowEventSubscriber = flowEventQueue.createSubscriber(ProjectQueueChannels.FLOW_EVENT);
            flowEventSubscriber.subscribeAsync(this::handleFlowEvent);
            log.info("[ProjectQueue] 工作流事件订阅者已启动, channel={}", ProjectQueueChannels.FLOW_EVENT);
        } catch (Exception e) {
            log.warn("[ProjectQueue] 工作流事件订阅者启动失败, 将降级为仅 Feign 调用: {}", e.getMessage());
        }
    }

    /**
     * 处理工作流事件消息
     *
     * @param message 队列消息
     */
    private void handleFlowEvent(QueueMessage message) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            Map<String, Object> payload = YdszJson.fromJsonToMap(message.getBody(), String.class, Object.class);
            String eventType = payload.get("eventType") == null ? null : String.valueOf(payload.get("eventType"));
            String instanceId = payload.get("instanceId") == null ? null : String.valueOf(payload.get("instanceId"));

            if (eventType == null) {
                log.warn("[ProjectQueue] 工作流事件缺少 eventType, 跳过: traceId={}", message.getTraceId());
                return;
            }

            // 从 data 中提取业务键（initiationId）
            Object dataObj = payload.get("data");
            String initiationId = extractInitiationId(dataObj);

            if (initiationId == null) {
                log.debug("[ProjectQueue] 工作流事件非立项相关, 跳过: type={} instanceId={}",
                        eventType, instanceId);
                return;
            }

            dispatchEvent(eventType, initiationId, dataObj, message.getTraceId());
        } catch (Exception e) {
            log.error("[ProjectQueue] 工作流事件处理失败: traceId={} err={}",
                    message.getTraceId(), e.getMessage(), e);
        }
    }

    /**
     * 根据事件类型分发到对应的立项状态联动方法
     *
     * @param eventType    事件类型
     * @param initiationId 立项 ID
     * @param data         事件附加数据
     * @param traceId      追踪 ID
     */
    private void dispatchEvent(String eventType, String initiationId, Object data, String traceId) {
        try {
            switch (eventType) {
                case "INSTANCE_STARTED" -> {
                    initiationService.markProcessing(initiationId);
                    log.info("[ProjectQueue] 立项标记审批中: id={} traceId={}", initiationId, traceId);
                }
                case "INSTANCE_COMPLETED" -> {
                    initiationService.markApproved(initiationId);
                    log.info("[ProjectQueue] 立项标记已批准: id={} traceId={}", initiationId, traceId);
                }
                case "INSTANCE_REJECTED" -> {
                    String reason = extractReason(data);
                    initiationService.markRejected(initiationId, reason);
                    log.info("[ProjectQueue] 立项标记已驳回: id={} reason={} traceId={}",
                            initiationId, reason, traceId);
                }
                default -> log.debug("[ProjectQueue] 未处理的工作流事件类型: type={} id={} traceId={}",
                        eventType, initiationId, traceId);
            }
        } catch (Exception e) {
            log.warn("[ProjectQueue] 立项状态联动失败: type={} id={} err={} traceId={}",
                    eventType, initiationId, e.getMessage(), traceId);
        }
    }

    /**
     * 从事件数据中提取立项 ID
     *
     * <p>业务键格式为 {@code PMIS_INIT_<initiationId>}，兼容直接以数字存储的业务键。
     *
     * @param data 事件附加数据
     * @return 立项 ID，解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    private String extractInitiationId(Object data) {
        if (data == null) {
            return null;
        }
        String bizId = null;
        if (data instanceof Map) {
            Object bizIdObj = ((Map<String, Object>) data).get("businessId");
            bizId = bizIdObj == null ? null : String.valueOf(bizIdObj);
        }
        if (bizId == null || bizId.isBlank()) {
            return null;
        }
        String raw = bizId.startsWith(INIT_BIZ_KEY_PREFIX)
                ? bizId.substring(INIT_BIZ_KEY_PREFIX.length())
                : bizId;
        return raw.trim();
    }

    /**
     * 从事件数据中提取驳回原因
     *
     * @param data 事件附加数据
     * @return 驳回原因，无则返回 null
     */
    @SuppressWarnings("unchecked")
    private String extractReason(Object data) {
        if (data instanceof Map) {
            Object reasonObj = ((Map<String, Object>) data).get("reason");
            return reasonObj == null ? null : String.valueOf(reasonObj);
        }
        return null;
    }

    @PreDestroy
    public void destroy() {
        if (flowEventSubscriber != null) {
            flowEventSubscriber.stop();
        }
        if (flowEventQueue != null) {
            flowEventQueue.close();
        }
        log.info("[ProjectQueue] 工作流事件订阅者已关闭");
    }
}
