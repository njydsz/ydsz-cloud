package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.vo.OutboxEventVO;

/**
 * 审计事件订阅者（P0-2：Outbox 模式）。
 *
 * <p>消费 Outbox 事件中 topic={@code audit} 的事件，记录审计日志。
 *
 * <p>操作审计与任务变更日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditOutboxSubscriber implements Consumer<OutboxEventVO> {
  /** 日志 payload 截断长度 */
  private static final int MAX_PAYLOAD_LOG_LENGTH = 200;


  private static final String TOPIC = "audit";

  @Override
  public void accept(OutboxEventVO event) {
    if (!TOPIC.equals(event.getTopic())) {
      return;
    }
    try {
      // 审计日志：记录任务生命周期事件（实际项目中可对接 ydsz-common-audit 模块）
      log.info("[AuditSubscriber] 审计事件: eventKey={} eventType={} topic={} payload={}",
          event.getEventKey(),
          event.getEventType(),
          event.getTopic(),
          event.getPayload() != null && event.getPayload().length() > MAX_PAYLOAD_LOG_LENGTH
              ? event.getPayload().substring(0, MAX_PAYLOAD_LOG_LENGTH) + "..."
              : event.getPayload());
    } catch (Exception e) {
      log.error("[AuditSubscriber] 审计记录异常: eventKey={} reason={}", event.getEventKey(), e.getMessage(), e);
      throw e;
    }
  }
}
