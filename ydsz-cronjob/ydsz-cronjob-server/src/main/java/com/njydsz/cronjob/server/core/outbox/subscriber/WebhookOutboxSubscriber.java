package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.cronjob.domain.entity.outbox.OutboxEvent;
import com.njydsz.cronjob.server.core.dispatch.WebhookEventDispatcher;

/**
 * WebHook 事件订阅者（P0-2：Outbox 模式）。
 *
 * <p>消费 Outbox 事件中 topic={@code webhook} 的事件，委托 {@link WebhookEventDispatcher} 推送到已配置的 WebHook 端点。
 *
 * <p>幂等保证：基于 {@code eventKey} 去重（WebHook 接收方也应做幂等处理）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookOutboxSubscriber implements java.util.function.Consumer<OutboxEvent> {

  private static final String TOPIC = "webhook";

  private final WebhookEventDispatcher webhookEventDispatcher;

  @Override
  public void accept(OutboxEvent event) {
    if (!TOPIC.equals(event.getTopic())) {
      return;
    }
    try {
      // 解析 payload 获取 jobKey 和事件数据
      Map<String, Object> payload = parsePayload(event.getPayload());
      String jobKey = payload != null ? (String) payload.get("jobKey") : null;
      if (jobKey == null || jobKey.isBlank()) {
        log.warn("[WebhookSubscriber] payload 中 jobKey 为空, eventKey={}", event.getEventKey());
        return;
      }
      String eventType = event.getEventType() != null ? event.getEventType().name() : "UNKNOWN";
      webhookEventDispatcher.dispatchEvent(eventType, jobKey, payload);
      log.debug("[WebhookSubscriber] WebHook 事件推送完成: eventKey={} eventType={}", event.getEventKey(), eventType);
    } catch (Exception e) {
      log.error("[WebhookSubscriber] WebHook 事件推送异常: eventKey={} reason={}",
          event.getEventKey(), e.getMessage(), e);
      throw e; // 抛出异常让 OutboxPublisher 处理重试
    }
  }

  /**
   * 解析 payload JSON 为 Map。
   *
   * <p>P0-FIX: YdszJson.parseObject 仅支持单参（返回 ObjectNode），改用 {@link YdszJson#fromJson}。
   *
   * @param payloadJson payload JSON 字符串
   * @return 解析后的 Map（解析失败返回 null）
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> parsePayload(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return null;
    }
    try {
      return YdszJson.fromJson(payloadJson, Map.class);
    } catch (Exception e) {
      log.warn("[WebhookSubscriber] payload 解析失败: {}", e.getMessage());
      return null;
    }
  }
}
