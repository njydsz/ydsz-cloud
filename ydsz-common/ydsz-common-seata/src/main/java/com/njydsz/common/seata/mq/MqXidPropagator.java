package com.njydsz.common.seata.mq;

import java.util.HashMap;
import java.util.Map;

/**
 * MQ XID 传播器 SPI
 *
 * <p>定义消息中间件感知 XID 传播的抽象接口，支持多种 MQ 实现 （RocketMQ、Kafka、RabbitMQ 等）的透明接入。
 *
 * <p>各 MQ 实现类需：
 *
 * <ol>
 *   <li>将当前线程的 XID、事务类型/名称注入消息元数据
 *   <li>从消息元数据中提取 XID 并绑定到消费者线程
 * </ol>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 发送端：注入 XID
 * Message msg = new MqXidPropagator.MessageBuilder(topic, body)
 *     .withXidPropagator(rocketMqXidPropagator)
 *     .build();
 * producer.send(msg);
 *
 * // 消费者端：恢复 XID
 * rocketMqXidPropagator.restoreXid(message);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public interface MqXidPropagator {

  /** 消息头 - 全局事务 ID */
  String HEADER_XID = "XID";

  /** 消息头 - 分支事务 ID */
  String HEADER_BRANCH_ID = "BRANCH_ID";

  /** 消息头 - 事务类型 */
  String HEADER_TX_TYPE = "TX_TYPE";

  /** 消息头 - 事务名称 */
  String HEADER_TX_NAME = "TX_NAME";

  /** 消息头 - 链路追踪 ID */
  String HEADER_TRACE_ID = "TRACE_ID";

  /**
   * 获取当前支持的 MQ 类型标识
   *
   * @return MQ 类型，如 "rocketmq"、"kafka"、"rabbitmq"
   */
  String getMqType();

  /**
   * 判断当前线程是否在事务上下文中
   *
   * @return 在事务上下文中返回 true，否则返回 false
   */
  default boolean isInTransaction() {
    return getCurrentXid() != null;
  }

  /**
   * 获取当前线程 XID
   *
   * @return 当前线程 XID，无事务上下文时返回 null
   */
  String getCurrentXid();

  /**
   * 获取当前线程事务类型
   *
   * @return 事务类型，无上下文时返回 null
   */
  String getCurrentTxType();

  /**
   * 获取当前线程事务名称
   *
   * @return 事务名称，无上下文时返回 null
   */
  String getCurrentTxName();

  /**
   * 获取当前链路追踪 ID
   *
   * @return traceId，无时返回 null
   */
  String getCurrentTraceId();

  /**
   * 构建携带 XID 的消息属性 Map
   *
   * <p>由 MQ 实现类调用此方法获取应注入消息头的属性集合。
   *
   * @return 属性 Map，无事务上下文时返回空 Map
   */
  default Map<String, String> buildXidProperties() {
    Map<String, String> props = new HashMap<>(4);
    String xid = getCurrentXid();
    if (xid != null) {
      props.put(HEADER_XID, xid);
    }
    String txType = getCurrentTxType();
    if (txType != null) {
      props.put(HEADER_TX_TYPE, txType);
    }
    String txName = getCurrentTxName();
    if (txName != null && !txName.isEmpty()) {
      props.put(HEADER_TX_NAME, txName);
    }
    String traceId = getCurrentTraceId();
    if (traceId != null) {
      props.put(HEADER_TRACE_ID, traceId);
    }
    return props;
  }
}
