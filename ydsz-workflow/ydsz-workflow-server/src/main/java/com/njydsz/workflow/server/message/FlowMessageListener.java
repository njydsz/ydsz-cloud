package com.njydsz.workflow.server.message;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;

/**
 * 流程消息 Redis Pub/Sub 监听器。
 *
 * <p>订阅 Redis 频道，接收跨服务发送的流程事件消息，匹配 event_subscription 表中的 WAITING 订阅并触发流程推进。
 *
 * <p><b>消息格式：</b>
 * <pre>{@code
 * {
 *   "messageName": "order_created",
 *   "correlationKeys": {"orderId": "12345"},
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowMessageListener implements MessageListener {

  private final FlowEventSubscriptionService eventSubscriptionService;

  /** {@inheritDoc} */
  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null || message.getBody() == null) {
      return;
    }

    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    String channel = pattern != null ? new String(pattern, StandardCharsets.UTF_8) : "unknown";
    log.info("[FlowMessageListener] 收到 Redis 消息: channel={} body={}", channel, body);

    try {
      // 解析消息体
      Map<String, Object> messageMap = YdszJson.parseMap(body);
      if (messageMap == null || messageMap.isEmpty()) {
        log.warn("[FlowMessageListener] 消息体为空，跳过: channel={}", channel);
        return;
      }

      String messageName = messageMap.get("messageName") != null
          ? messageMap.get("messageName").toString() : null;
      if (messageName == null || messageName.isBlank()) {
        log.warn("[FlowMessageListener] 消息体缺少 messageName，跳过: channel={}", channel);
        return;
      }

      // 提取 correlationKeys
      // YDIZ-WARN-001 允许保留：泛型擦除，消息队列反序列化后 Map 类型编译期无法验证
      @SuppressWarnings("unchecked")
      Map<String, Object> correlationKeys = messageMap.get("correlationKeys") instanceof Map
          ? (Map<String, Object>) messageMap.get("correlationKeys") : null;

      // 提取 correlationKey（用于精确匹配订阅）
      String correlationKey = null;
      if (correlationKeys != null && !correlationKeys.isEmpty()) {
        // 取第一个值作为 correlationKey（简化处理）
        Object firstValue = correlationKeys.values().iterator().next();
        if (firstValue != null) {
          correlationKey = firstValue.toString();
        }
      }

      // 调用 eventSubscriptionService 匹配并触发订阅
      String payload = YdszJson.toJson(messageMap);
      int triggered = eventSubscriptionService.correlateMessage(null, messageName, correlationKey, payload);

      log.info(
          "[FlowMessageListener] 消息处理完成: messageName={}, correlationKey={}, triggered={}",
          messageName, correlationKey, triggered);
    } catch (Exception e) {
      log.error(
          "[FlowMessageListener] 消息处理异常: channel={}, err={}",
          channel, e.getMessage(), e);
    }
  }
}
