package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.vo.OutboxEventVO;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * Metrics 事件订阅者（P0-2：Outbox 模式）。
 *
 * <p>消费 Outbox 事件中 topic={@code metrics} 的事件，记录 Prometheus 指标。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsOutboxSubscriber implements Consumer<OutboxEventVO> {

  private static final String TOPIC = "metrics";

  private final CronjobMetrics cronjobMetrics;

  /** 事件类型：任务执行成功（发布方约定的事件类型字符串） */
  private static final String JOB_SUCCESS = "JOB_SUCCESS";

  /** 事件类型：任务执行失败 */
  private static final String JOB_FAILED = "JOB_FAILED";

  /** 事件类型：任务执行超时 */
  private static final String JOB_TIMEOUT = "JOB_TIMEOUT";

  @Override
  public void accept(OutboxEventVO event) {
    if (!TOPIC.equals(event.getTopic())) {
      return;
    }
    try {
      if (event.getEventType() == null) {
        return;
      }
      switch (event.getEventType()) {
        case JOB_SUCCESS -> cronjobMetrics.incJobSuccess(event.getEventKey());
        case JOB_FAILED -> cronjobMetrics.incJobFailed(event.getEventKey());
        case JOB_TIMEOUT -> cronjobMetrics.incJobTimeout(event.getEventKey());
        default -> log.debug("[MetricsSubscriber] 忽略非指标事件: eventType={}", event.getEventType());
      }
      log.debug("[MetricsSubscriber] 指标记录完成: eventKey={} eventType={}", event.getEventKey(), event.getEventType());
    } catch (Exception e) {
      log.error("[MetricsSubscriber] 指标记录异常: eventKey={} reason={}", event.getEventKey(), e.getMessage(), e);
      throw e;
    }
  }
}
