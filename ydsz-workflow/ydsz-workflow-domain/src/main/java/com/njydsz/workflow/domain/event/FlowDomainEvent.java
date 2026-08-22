package com.njydsz.workflow.domain.event;

import java.io.Serial;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.ToString;

/**
 * 工作流领域事件基类。
 *
 * <p>所有工作流领域事件继承本类，携带事件发生时间戳。
 * 领域事件是不可变的对象，用于表示领域中已发生的重要业务事实。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>领域事件置于 {@code domain/event/} 包下、
 * 以 {@code Event} 结尾（符合 §34.2.1 表格：event/ 领域事件类）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@ToString
public abstract class FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件源（发布者对象） */
  private final Object source;

  /** 事件发生时间 */
  private final LocalDateTime occurredAt;

  protected FlowDomainEvent(Object source) {
    this.source = source;
    this.occurredAt = LocalDateTime.now();
  }
}
