package com.njydsz.workflow.server.message;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.event.DomainEventPublisher;
import com.njydsz.workflow.domain.event.FlowMessageEvent;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;

/**
 * 消息事件服务默认实现
 *
 * <p>基于 Redis Pub/Sub + ydzsz_flow_event_subscription 表实现消息订阅分发。
 *
 * <p>核心流程：
 * <ol>
 *   <li>消息事件捕获节点到达时调用 {@link #subscribeMessage} 创建订阅
 *   <li>外部系统调用 {@link #publishMessageEvent} 发布消息
 *   <li>匹配订阅记录并触发流程继续
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEventServiceImpl implements MessageEventService {

  /** Redis Pub/Sub 通道前缀 */
  private static final String CHANNEL_PREFIX = "flow:message:";

  /** JSON 构建初始缓冲区大小 */
  private static final int JSON_BUFFER_SIZE = 64;

  private final FlowEventSubscriptionService eventSubscriptionService;

  private final DomainEventPublisher domainEventPublisher;

  private final RedisTemplate<String, String> redisTemplate;

  /** {@inheritDoc} */
  @Override
  public int publishMessageEvent(String messageName, Map<String, Object> correlationKeys) {
    if (messageName == null || messageName.isBlank()) {
      log.warn("[Flow-MessageEvent] 消息名称为空，跳过发布");
      return 0;
    }

    log.info("[Flow-MessageEvent] 发布消息事件: messageName={} keys={}", messageName, correlationKeys);

    // 1. 通过 eventSubscriptionService 匹配并触发订阅
    String payload = correlationKeys != null ? YdszJson.toJson(correlationKeys) : "{}";
    int triggered =
        eventSubscriptionService.correlateMessage(
            null, messageName, null, payload);

    // 2. 通过 Redis Pub/Sub 广播消息事件（供跨服务消费）
    try {
      String channel = CHANNEL_PREFIX + messageName;
      String message = buildMessageJson(messageName, correlationKeys);
      redisTemplate.convertAndSend(channel, message);
      log.info("[Flow-MessageEvent] Redis广播完成: channel={} triggered={}", channel, triggered);
    } catch (Exception e) {
      log.warn("[Flow-MessageEvent] Redis广播失败(不影响核心流程): err={}", e.getMessage());
    }

    // 3. 发布领域事件
    try {
      FlowMessageEvent event = new FlowMessageEvent(this, messageName, correlationKeys, null);
      domainEventPublisher.publish(event);
    } catch (Exception e) {
      log.warn("[Flow-MessageEvent] 领域事件发布失败: err={}", e.getMessage());
    }

    return triggered;
  }

  /** {@inheritDoc} */
  @Override
  public String subscribeMessage(String nodeId, String messageName) {
    log.info("[Flow-MessageEvent] 节点订阅消息: nodeId={} messageName={}", nodeId, messageName);
    // 实际订阅由 eventSubscriptionService 在流程到达事件捕获节点时创建
    // 此接口预留供手动订阅场景
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public int handleMessageEvent(String messageName, Map<String, Object> correlationKeys) {
    if (messageName == null || messageName.isBlank()) {
      return 0;
    }

    log.info(
        "[Flow-MessageEvent] 处理消息事件: messageName={} keys={}",
        messageName, correlationKeys);

    // 匹配订阅并触发
    String payload = correlationKeys != null ? YdszJson.toJson(correlationKeys) : "{}";
    return eventSubscriptionService.correlateMessage(null, messageName, null, payload);
  }

  /**
   * 构建消息 JSON
   *
   * @param messageName 消息名称
   * @param correlationKeys 关联键
   * @return JSON 字符串
   */
  private String buildMessageJson(String messageName, Map<String, Object> correlationKeys) {
    StringBuilder sb = new StringBuilder(JSON_BUFFER_SIZE);
    sb.append("{\"messageName\":\"").append(messageName).append("\"");
    if (correlationKeys != null && !correlationKeys.isEmpty()) {
      sb.append(",\"correlationKeys\":").append(YdszJson.toJson(correlationKeys));
    }
    sb.append(",\"timestamp\":").append(System.currentTimeMillis()).append("}");
    return sb.toString();
  }
}