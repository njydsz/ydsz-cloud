package com.njydsz.cronjob.server.core.outbox.subscriber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.consumer.OutboxSubscriber;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * Metrics 事件订阅者（YDIZ-EVENT-002 OutboxSubscriber SPI 实现）。
 *
 * <p>消费 Outbox 事件中 topic={@code metrics} 的事件，记录 Prometheus 指标。
 *
 * <p>由 {@link com.njydsz.common.event.consumer.OutboxSubscriberDispatcher} 统一分发，
 * 无需自行过滤 topic（已在 {@link #getTopic()} 声明）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.24 实现 OutboxSubscriber SPI（YDIZ-EVENT-002），移除 @EventListener 手动过滤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsOutboxSubscriber implements OutboxSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsOutboxSubscriber.class);

  /** 事件类型：任务执行成功（发布方约定的事件类型字符串） */
  private static final String JOB_SUCCESS = "JOB_SUCCESS";

  /** 事件类型：任务执行失败 */
  private static final String JOB_FAILED = "JOB_FAILED";

  /** 事件类型：任务执行超时 */
  private static final String JOB_TIMEOUT = "JOB_TIMEOUT";

  private final CronjobMetrics cronjobMetrics;

  @Override
  public String getTopic() {
    return "metrics";
  }

  /**
   * 处理 metrics 事件，记录 Prometheus 指标。
   *
   * @param message Outbox 消息
   */
  @Override
  public void onMessage(OutboxMessage message) {
    String eventType = message.getEventType();
    if (eventType == null) {
      return;
    }
    String eventKey = OutboxEventKeyExtractor.extractEventKey(message.getExtInfo());
    switch (eventType) {
      case JOB_SUCCESS -> cronjobMetrics.incJobSuccess(eventKey);
      case JOB_FAILED -> cronjobMetrics.incJobFailed(eventKey);
      case JOB_TIMEOUT -> cronjobMetrics.incJobTimeout(eventKey);
      default -> LOG.debug("[MetricsSubscriber] 未知 eventType={}, 跳过", eventType);
    }
    LOG.debug("[MetricsSubscriber] 指标已记录: eventKey={} eventType={}", eventKey, eventType);
  }
}
