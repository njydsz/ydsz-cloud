package com.njydsz.workflow.server.message;

import java.util.List;
import java.util.Map;

/**
 * 消息事件服务
 *
 * <p>流程节点可订阅消息主题，外部系统发送消息到指定主题触发流程继续。
 * 使用 Redis Pub/Sub + ydzsz_flow_event_subscription 表实现消息订阅分发。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MessageEventService {

  /**
   * 发布消息事件
   *
   * <p>向指定消息主题发送事件，唤醒所有订阅该主题的等待节点。
   *
   * @param messageName 消息名称（对应 BPMN messageRef）
   * @param correlationKeys 关联键（业务标识，用于精确匹配订阅），可为 null
   * @return 唤醒的等待节点数量
   */
  int publishMessageEvent(String messageName, Map<String, Object> correlationKeys);

  /**
   * 节点订阅消息
   *
   * <p>流程到达消息事件捕获节点时调用，创建订阅记录等待外部消息触发。
   *
   * @param nodeId 节点 ID
   * @param messageName 消息名称
   * @return 订阅 ID
   */
  String subscribeMessage(String nodeId, String messageName);

  /**
   * 处理收到的消息事件
   *
   * <p>当消息到达时，匹配订阅记录并唤醒对应等待节点推进。
   *
   * @param messageName 消息名称
   * @param correlationKeys 关联键
   * @return 触发的订阅数量
   */
  int handleMessageEvent(String messageName, Map<String, Object> correlationKeys);
}
