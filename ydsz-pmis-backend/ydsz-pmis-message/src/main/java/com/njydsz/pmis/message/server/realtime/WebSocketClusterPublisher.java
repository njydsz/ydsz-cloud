package com.njydsz.pmis.message.server.realtime;

import com.njydsz.pmis.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 集群广播发布者（Redis Pub/Sub）。
 *
 * <p>将推送指令发布到 Redis Channel {@code pmis:ws:cluster:push}，所有应用实例
 * 通过 {@link WebSocketClusterSubscriber} 订阅该 Channel，收到消息后推送到本地
 * WebSocket session，实现多节点集群推送。
 *
 * <p>降级策略：Redis 异常时回退到本地直接推送（保证单节点可用）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketClusterPublisher {

    /** Redis Channel 名称 */
    public static final String CHANNEL = "pmis:ws:cluster:push";

    private final StringRedisTemplate redisTemplate;

    /**
     * 发布集群推送消息到 Redis Channel。
     *
     * <p>所有订阅该 Channel 的实例都会收到消息并推送到本地 WebSocket session。
     *
     * @param message 集群推送消息
     * @return true 表示发布成功
     */
    public boolean publish(WebSocketClusterMessage message) {
        if (message == null) {
            return false;
        }
        try {
            String json = JsonUtils.toJson(message);
            redisTemplate.convertAndSend(CHANNEL, json);
            log.debug("[WS-Cluster] 发布集群推送: type={} userId={} topic={}",
                    message.getPushType(), message.getUserId(), message.getTopic());
            return true;
        } catch (Exception e) {
            log.warn("[WS-Cluster] 发布失败,降级本地推送: err={}", e.getMessage());
            return false;
        }
    }
}
