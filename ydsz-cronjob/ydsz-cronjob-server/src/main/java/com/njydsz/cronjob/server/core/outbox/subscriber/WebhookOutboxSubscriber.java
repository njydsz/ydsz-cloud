package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.Collections;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.consumer.OutboxSubscriber;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.cronjob.server.core.dispatch.WebhookEventDispatcher;

/**
 * WebHook 事件订阅者（YDIZ-EVENT-002 OutboxSubscriber SPI 实现）。
 *
 * <p>消费 Outbox 事件中 topic={@code webhook} 的事件，委托 {@link WebhookEventDispatcher} 推送到 WebHook 端点。
 *
 * <p>由 {@link com.njydsz.common.event.consumer.OutboxSubscriberDispatcher} 统一分发。
 *
 * <p>幂等保证：基于 eventKey 去重（WebHook 接收方也应做幂等处理）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.24 实现 OutboxSubscriber SPI（YDIZ-EVENT-002），移除 @EventListener 手动过滤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookOutboxSubscriber implements OutboxSubscriber {

  private final WebhookEventDispatcher webhookEventDispatcher;

  @Override
  public String getTopic() {
    return "webhook";
  }

  /**
   * 处理 webhook 事件，推送至已配置端点。
   *
   * @param message Outbox 消息
   */
  @Override
  public void onMessage(OutboxMessage message) {
    try {
      Map<String, Object> payload = parsePayload(message.getPayload());
      String jobKey = payload != null ? (String) payload.get("jobKey") : null;
      if (jobKey == null || jobKey.isBlank()) {
        LOG.warn("[WebhookSubscriber] payload 中 jobKey 为空, eventKey={}", message.getId());
        return;
      }
      String eventType = message.getEventType() != null ? message.getEventType() : "UNKNOWN";
      webhookEventDispatcher.dispatchEvent(eventType, jobKey, payload);
      LOG.debug("[WebhookSubscriber] WebHook 事件推送完成: eventKey={} eventType={}", message.getId(), eventType);
    } catch (RuntimeException e) {
      LOG.error("[WebhookSubscriber] WebHook 事件推送异常: eventKey={} reason={}",
          message.getId(), e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 解析 payload JSON 为 Map。
   *
   * @param payloadJson payload JSON 字符串
   * @return 解析后的 Map（解析失败返回空 Map）
   */
  private Map<String, Object> parsePayload(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      return YdszJson.fromJsonToMap(payloadJson, String.class, Object.class);
    } catch (RuntimeException e) {
      LOG.warn("[WebhookSubscriber] payload 解析失败: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }
}
