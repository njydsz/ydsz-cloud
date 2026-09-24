package com.njydsz.cronjob.server.core.outbox.subscriber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * Metrics 事件订阅者（Outbox 模式收敛至 ydsz-common-event）。
 *
 * <p>消费 Outbox 事件中 topic={@code metrics} 的事件，记录 Prometheus 指标。
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
public class MetricsOutboxSubscriber {

  private static final String TOPIC = "metrics";

  private final CronjobMetrics cronjobMetrics;

  /** 事件类型：任务执行成功（发布方约定的事件类型字符串） */
  private static final String JOB_SUCCESS = "JOB_SUCCESS";

  /** 事件类型：任务执行失败 */
  private static final String JOB_FAILED = "JOB_FAILED";

  /** 事件类型：任务执行超时 */
  private static final String JOB_TIMEOUT = "JOB_TIMEOUT";

  /**
   * 监听 OutboxMessage 事件，过滤 topic=metrics 的事件并记录指标。
   *
   * @param message Outbox 消息
   */
  @EventListener
  public void onOutboxMessage(OutboxMessage message) {
    if (!TOPIC.equals(message.getTopic())) {
      return;
    }
    try {
      String eventType = message.getEventType();
      if (eventType == null) {
        return;
      }
      String eventKey = OutboxEventKeyExtractor.extractEventKey(message.getExtInfo());
      switch (eventType) {
        case JOB_SUCCESS -> cronjobMetrics.incJobSuccess(eventKey);
        case JOB_FAILED -> cronjobMetrics.incJobFailed(eventKey);
        case JOB_TIMEOUT -> cronjobMetrics.incJobTimeout(eventKey);
        default -> log.debug("[MetricsSubscriber] 忽略非指标事件: eventType={}", eventType);
      }
      log.debug("[MetricsSubscriber] 指标记录完成: eventKey={} eventType={}", eventKey, eventType);
    } catch (Exception e) {
      log.error("[MetricsSubscriber] 指标记录异常: eventKey={} reason={}", message.getId(), e.getMessage(), e);
      throw e;
    }
  }
}
