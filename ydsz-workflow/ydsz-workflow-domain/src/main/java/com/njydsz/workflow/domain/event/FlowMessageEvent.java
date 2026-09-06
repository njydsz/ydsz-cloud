package com.njydsz.workflow.domain.event;

import java.io.Serial;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程消息事件
 *
 * <p>外部系统通过发布消息事件触发等待中的流程节点继续执行。
 * 使用 Redis Pub/Sub + ydsz_flow_event_subscription 表实现消息订阅与分发。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowMessageEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息名称（对应 BPMN messageRef） */
  private final String messageName;

  /** 关联键（业务标识，用于精确匹配订阅） */
  private final Map<String, Object> correlationKeys;

  /** 消息载荷 */
  private final Map<String, Object> payload;

  public FlowMessageEvent(
      Object source,
      String messageName,
      Map<String, Object> correlationKeys,
      Map<String, Object> payload) {
    super(source);
    this.messageName = messageName;
    this.correlationKeys = correlationKeys;
    this.payload = payload;
  }
}
