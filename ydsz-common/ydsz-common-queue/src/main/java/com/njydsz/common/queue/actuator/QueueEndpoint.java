package com.njydsz.common.queue.actuator;

import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.manager.QueueManager;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

/**
 * 消息队列 Actuator 端点
 *
 * <p>提供 /actuator/queues 端点，用于查询消息队列的运行状态和监控信息。
 *
 * <p><b>暴露的端点：</b>
 *
 * <ul>
 *   <li>{@code GET /actuator/queues} - 列出所有队列的概要信息
 *   <li>{@code GET /actuator/queues/{queueId}} - 查询指定队列的详细信息
 * </ul>
 *
 * <p><b>响应示例：</b>
 *
 * <pre>{@code
 * {
 *   "queueType": "stream",
 *   "channel": "order-events",
 *   "consumedCount": 12345,
 *   "failedCount": 23,
 *   "isRunning": true,
 *   "consumerCount": 3
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Endpoint(id = "queues")
public class QueueEndpoint {

  private final QueueProperties queueProperties;
  private final QueueManager queueManager;

  public QueueEndpoint(QueueProperties queueProperties, QueueManager queueManager) {
    this.queueProperties = queueProperties;
    this.queueManager = queueManager;
  }

  /**
   * 获取所有队列的概要信息
   *
   * @return 队列概要信息 Map
   */
  @ReadOperation
  public Map<String, Object> queuesSummary() {
    Map<String, Object> result = new HashMap<>();
    result.put("queueType", queueProperties.getResolvedType().getValue());
    result.put("enabled", queueProperties.isEnabled());
    result.put("streamGroup", queueProperties.getStreamGroup());
    result.put("managedQueues", queueManager.getQueueCount());
    return result;
  }

  /**
   * 获取指定队列类型的详细信息
   *
   * @param queueType 队列类型（如 stream, list, pubsub）
   * @return 队列详细信息
   */
  @ReadOperation
  public Map<String, Object> queueDetail(@Selector String queueType) {
    Map<String, Object> detail = new HashMap<>();
    detail.put("queueType", queueType);
    detail.put("configuration", queueProperties.toString());

    // 根据队列类型返回特定配置
    switch (queueType.toLowerCase()) {
      case "stream":
        detail.put("streamGroup", queueProperties.getStreamGroup());
        detail.put("streamConsumer", queueProperties.getStreamConsumer());
        detail.put("streamRetryMax", queueProperties.getStreamRetryMax());
        detail.put("streamBatchSize", queueProperties.getStreamBatchSize());
        detail.put("streamBlockMillis", queueProperties.getStreamBlockMillis());
        detail.put("deadLetterSuffix", queueProperties.getStreamDeadLetterSuffix());
        break;
      case "list":
        detail.put("listBlockTimeoutSeconds", queueProperties.getListBlockTimeoutSeconds());
        break;
      case "pubsub":
        detail.put("note", "PubSub 模式无额外配置，仅支持广播消费");
        break;
      default:
        detail.put("supportedTypes", "stream, list, pubsub, kafka, rocket, rabbit");
        break;
    }

    // 死信队列配置
    detail.put("deadLetterRetryEnabled", queueProperties.isDeadLetterRetryEnabled());
    detail.put("deadLetterMaxRetries", queueProperties.getDeadLetterMaxRetries());
    detail.put("deadLetterRetryInterval", queueProperties.getDeadLetterRetryInterval());

    return detail;
  }
}
