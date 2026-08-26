package com.njydsz.workflow.server.queue;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.workflow.engine.FlowEventContext;
import com.njydsz.workflow.engine.FlowWorkflowEvent;

/**
 * 工作流消息队列发布者
 *
 * <p>监听 Spring 内部 {@link FlowWorkflowEvent}，将工作流生命周期事件 通过 common-queue 发布到消息队列通道 {@link
 * FlowQueueChannels#FLOW_EVENT}， 使其他服务（如 project 模块）可以跨服务异步消费工作流事件。
 *
 * <p><b>设计说明：</b>
 *
 * <ul>
 *   <li>使用 {@link EventListener} + {@link Async} 异步监听，不影响主流程事务
 *   <li>队列发布失败仅记录日志，不抛出异常（Spring 内部事件已保证本服务内通信）
 *   <li>消息体为 JSON 格式，包含 eventType、instanceId、taskId、data 等字段
 *   <li>消息头携带 eventType，便于消费者做消息过滤
 * </ul>
 *
 * <p><b>使用示例（消费方）：</b>
 *
 * <pre>{@code
 * IMessageQueue queue = queueProvider.createMessageQueue(QueueType.STREAM);
 * IMessageSubscriber subscriber = queue.createSubscriber(FlowQueueChannels.FLOW_EVENT);
 * subscriber.subscribeAsync(message -> {
 *     String eventType = message.getHeader("eventType");
 *     String body = message.getBody();
 *     // 解析 body JSON 并处理...
 * });
 * }</pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowQueuePublisher {

    /** Map 初始容量：小集合（8） */
  private static final int MAP_INIT_CAPACITY_8 = 8;

  private final IMessageQueueProvider messageQueueProvider;
  private IMessageQueue flowEventQueue;
  private IMessagePublisher flowEventPublisher;

  /**
   * 应用启动后创建工作流事件通道的发布者。
   *
   * <p><b>失败降级：</b>队列基础设施不可用时<b>不抛异常</b>，仅记录 warn 并使 {@code flowEventPublisher} 保持 {@code
   * null}。此后所有发布方法都会在入口处静默返回， 系统退化为「仅本地 Spring 事件」——本服务内的监听逻辑照常工作， 只是 project 等跨服务消费方收不到消息。这样设计是为了让
   * MQ 故障不阻断审批主流程。
   *
   * <p>降级为一次性判定：本方法<b>不会</b>自动重试，MQ 恢复后需重启实例才能恢复发布能力。
   */
  @PostConstruct
  public void init() {
    try {
      flowEventQueue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
      flowEventPublisher = flowEventQueue.createPublisher(FlowQueueChannels.FLOW_EVENT);
      log.info("[FlowQueue] 工作流事件队列发布者已启动, channel={}", FlowQueueChannels.FLOW_EVENT);
    } catch (Exception e) {
      log.warn("[FlowQueue] 工作流事件队列发布者启动失败, 将降级为仅本地事件: {}", e.getMessage());
    }
  }

  /**
   * 异步监听工作流事件并发布到消息队列
   *
   * @param event 工作流事件
   */
  @Async("flowQueueExecutor")
  @EventListener
  public void onFlowWorkflowEvent(FlowWorkflowEvent event) {
    if (flowEventPublisher == null) {
      return;
    }
    try {
      Map<String, Object> payload = new HashMap<>(MAP_INIT_CAPACITY_8);
      payload.put("eventType", event.getEventType());
      payload.put("instanceId", event.getInstanceId());
      payload.put("taskId", event.getTaskId());
      payload.put("data", event.getData());

      QueueMessage message = QueueMessage.of(YdszJson.toJson(payload));
      message.addHeader("eventType", event.getEventType());
      message.addHeader("instanceId", event.getInstanceId());
      message.addHeader("source", "workflow");

      flowEventPublisher.publish(message);
      log.debug(
          "[FlowQueue] 事件已发布到队列: type={} instanceId={} taskId={}",
          event.getEventType(),
          event.getInstanceId(),
          event.getTaskId());
    } catch (Exception e) {
      log.warn("[FlowQueue] 事件发布到队列失败: type={} err={}", event.getEventType(), e.getMessage());
    }
  }

  /**
   * 发布带上下文的工作流事件到消息队列
   *
   * <p>供需要携带 {@link FlowEventContext} 元数据的场景使用， 如 P2-37 增强的终止/完成事件。
   *
   * @param eventType 事件类型
   * @param ctx 事件上下文
   */
  public void publishWithContext(String eventType, FlowEventContext ctx) {
    if (flowEventPublisher == null || ctx == null) {
      return;
    }
    try {
      Map<String, Object> payload = new HashMap<>(MAP_INIT_CAPACITY_8);
      payload.put("eventType", eventType);
      payload.put("instanceId", ctx.getInstanceId());
      payload.put("taskId", ctx.getTaskId());
      payload.put("operatorId", ctx.getOperatorId());
      payload.put("action", ctx.getAction());
      payload.put("tenantId", ctx.getTenantId());
      payload.put("traceId", ctx.getTraceId());

      QueueMessage message = QueueMessage.of(YdszJson.toJson(payload));
      message.addHeader("eventType", eventType);
      message.addHeader("instanceId", ctx.getInstanceId());
      message.addHeader("operatorId", ctx.getOperatorId());
      message.addHeader("source", "workflow");

      flowEventPublisher.publish(message);
      log.debug("[FlowQueue] 上下文事件已发布到队列: type={} instanceId={}", eventType, ctx.getInstanceId());
    } catch (Exception e) {
      log.warn("[FlowQueue] 上下文事件发布到队列失败: type={} err={}", eventType, e.getMessage());
    }
  }

  /**
   * 发布业务自定义事件到消息队列
   *
   * <p>P0-7: 供业务监听器（如 ProjectInitiationFlowListener）跨服务联动业务状态使用。 与 {@link #publishWithContext}
   * 不同，本方法直接接收任意数据负载， 适用于无法用 {@link FlowEventContext} 表达的业务事件（如立项状态联动）。
   *
   * <p><b>使用示例：</b>
   *
   * <pre>{@code
   * Map<String, Object> data = new HashMap<>();
   * data.put("initiationId", "12345");
   * data.put("action", "markProcessing");
   * queuePublisher.publish("INITIATION_STATUS_SYNC", data);
   * }</pre>
   *
   * @param eventType 事件类型（建议使用业务前缀，如 INITIATION_STATUS_SYNC）
   * @param data 业务数据负载（不可为 null）
   * @since 1.0.0
   */
  public void publish(String eventType, Map<String, Object> data) {
    if (flowEventPublisher == null || eventType == null || data == null) {
      return;
    }
    try {
      Map<String, Object> payload = new HashMap<>(data.size() + 2);
      payload.put("eventType", eventType);
      payload.putAll(data);

      QueueMessage message = QueueMessage.of(YdszJson.toJson(payload));
      message.addHeader("eventType", eventType);
      message.addHeader("source", "workflow");
      // 透传 instanceId/initiationId 便于消费者做消息路由
      Object instanceId = data.get("instanceId");
      if (instanceId != null) {
        message.addHeader("instanceId", String.valueOf(instanceId));
      }
      Object bizId = data.get("initiationId");
      if (bizId != null) {
        message.addHeader("initiationId", String.valueOf(bizId));
      }

      flowEventPublisher.publish(message);
      log.debug("[FlowQueue] 业务事件已发布到队列: type={} payload={}", eventType, data.keySet());
    } catch (Exception e) {
      log.warn("[FlowQueue] 业务事件发布到队列失败: type={} err={}", eventType, e.getMessage());
    }
  }

  /**
   * 应用关闭前关闭发布者与队列连接。
   *
   * <p>按「先关发布者、后关队列」的顺序释放，确保缓冲区内已提交的消息先行 flush， 避免停机瞬间丢失刚发出的事件。两个字段在 {@link #init()} 降级失败时可能为
   * {@code null}，故均做空值保护；方法幂等，可重复调用。
   *
   * <p><b>注意：</b>{@code @Async} 线程池中尚未执行的事件监听任务不在本方法管辖范围， 极端情况下停机会丢失少量异步事件，跨服务一致性由消费侧的对账逻辑兜底。
   */
  @PreDestroy
  public void destroy() {
    if (flowEventPublisher != null) {
      flowEventPublisher.close();
    }
    if (flowEventQueue != null) {
      flowEventQueue.close();
    }
    log.info("[FlowQueue] 工作流事件队列发布者已关闭");
  }
}
