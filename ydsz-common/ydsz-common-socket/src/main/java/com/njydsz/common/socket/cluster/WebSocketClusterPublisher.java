package com.njydsz.common.socket.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

/**
 * WebSocket 集群广播发布者（Redis Pub/Sub）。
 *
 * <p>将推送指令发布到 Redis Channel，所有应用实例通过 {@link WebSocketClusterSubscriber} 订阅该 Channel，收到消息后推送到本地
 * WebSocket session，实现多节点集群推送。
 *
 * <p>降级策略：Redis 异常时返回 false，调用方回退到本地直接推送。 熔断保护：连续失败时触发熔断，直接返回 false 降级（P0-2）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketClusterPublisher {

  private final StringRedisTemplate redisTemplate;
  private final WebSocketProperties properties;
  private final WebSocketCircuitBreaker circuitBreaker;

  /**
   * 发布集群推送消息到 Redis Channel。
   *
   * @param message 集群推送消息
   * @return true 表示发布成功；false 表示发布失败（调用方应降级本地推送）
   */
  public boolean publish(WebSocketClusterMessage message) {
    if (message == null) {
      return false;
    }
    if (message.getTraceId() == null) {
      message.setTraceId(WebSocketTraceContext.getOrGenerateTraceId());
    }
    return circuitBreaker.execute(() -> doPublish(message), () -> false);
  }

  private boolean doPublish(WebSocketClusterMessage message) {
    String json = YdszJson.toJson(message);
    String channel = properties.getCluster().getChannel();
    redisTemplate.convertAndSend(channel, json);
    log.debug(
        "[WS-Cluster] 发布集群推送: type={} userId={} topic={} traceId={}",
        message.getPushType(),
        message.getUserId(),
        message.getTopic(),
        message.getTraceId());
    return true;
  }
}
