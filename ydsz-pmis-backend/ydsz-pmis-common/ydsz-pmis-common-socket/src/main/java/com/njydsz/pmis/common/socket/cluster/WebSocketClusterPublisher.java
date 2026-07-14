package com.njydsz.pmis.common.socket.cluster;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;
import com.njydsz.pmis.common.json.Json;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 集群广播发布者（Redis Pub/Sub）。
 *
 * <p>将推送指令发布到 Redis Channel，所有应用实例通过
 * {@link WebSocketClusterSubscriber} 订阅该 Channel，收到消息后推送到本地
 * WebSocket session，实现多节点集群推送。
 *
 * <p>降级策略：Redis 异常时返回 false，调用方回退到本地直接推送。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketClusterPublisher {

    private final StringRedisTemplate redisTemplate;
    private final WebSocketProperties properties;

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
        try {
            String json = Json.toJson(message);
            String channel = properties.getCluster().getChannel();
            redisTemplate.convertAndSend(channel, json);
            log.debug("[WS-Cluster] 发布集群推送: type={} userId={} topic={}",
                    message.getPushType(), message.getUserId(), message.getTopic());
            return true;
        } catch (Exception e) {
            log.warn("[WS-Cluster] 发布失败,降级本地推送: err={}", e.getMessage());
            return false;
        }
    }
}
