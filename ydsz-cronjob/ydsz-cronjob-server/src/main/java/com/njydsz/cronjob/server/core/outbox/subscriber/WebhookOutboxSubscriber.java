package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.Collections;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.cronjob.server.core.dispatch.WebhookEventDispatcher;

/**
 * WebHook 事件订阅者（Outbox 模式收敛至 ydsz-common-event）。
 *
 * <p>消费 Outbox 事件中 topic={@code webhook} 的事件，委托 {@link WebhookEventDispatcher} 推送到已配置的 WebHook 端点。
 *
 * <p>幂等保证：基于 {@code eventKey} 去重（WebHook 接收方也应做幂等处理）。
 *
 * <p><b>迁移说明（26.09.29）：</b>自建 {@code OutboxEvent} 体系迁移至 ydsz-common-event 标准
 * {@link OutboxMessage}。topic 字段对应原 OutboxEvent.topic；eventKey 由 extInfo JSON 携带。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.29 迁移至 ydsz-common-event {@link OutboxMessage}，废弃自建 OutboxEventVO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookOutboxSubscriber {

  private static final String TOPIC = "webhook";

  private final WebhookEventDispatcher webhookEventDispatcher;

  /**
   * 监听 OutboxMessage 事件，过滤 topic=webhook 的事件并推送。
   *
   * <p>由 ydsz-common-event {@code OutboxService} 在事务提交后发布 Spring 事件触发。
   *
   * @param message Outbox 消息
   */
  @EventListener
  public void onOutboxMessage(OutboxMessage message) {
    if (!TOPIC.equals(message.getTopic())) {
      return;
    }
    try {
      Map<String, Object> payload = parsePayload(message.getPayload());
      String jobKey = payload != null ? (String) payload.get("jobKey") : null;
      if (jobKey == null || jobKey.isBlank()) {
        log.warn("[WebhookSubscriber] payload 中 jobKey 为空, eventKey={}", message.getId());
        return;
      }
      String eventType = message.getEventType() != null ? message.getEventType() : "UNKNOWN";
      webhookEventDispatcher.dispatchEvent(eventType, jobKey, payload);
      log.debug("[WebhookSubscriber] WebHook 事件推送完成: eventKey={} eventType={}", message.getId(), eventType);
    } catch (Exception e) {
      log.error("[WebhookSubscriber] WebHook 事件推送异常: eventKey={} reason={}",
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
    } catch (Exception e) {
      log.warn("[WebhookSubscriber] payload 解析失败: {}", e.getMessage());
      return Collections.emptyMap();
    }
  }
}
