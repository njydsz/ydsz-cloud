package com.njydsz.pmis.common.socket.health;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;
import com.njydsz.pmis.common.socket.session.OnlineUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 模块健康检查（P0-1）。
 *
 * <p>通过 Actuator {@code /health/websocket} 端点暴露以下状态：
 * <ul>
 *   <li>{@code local.activeConnections} — 本节点活跃 WebSocket 连接数</li>
 *   <li>{@code cluster.enabled} — 集群广播是否启用</li>
 *   <li>{@code cluster.redisReachable} — Redis 连通性（PING 探测）</li>
 *   <li>{@code offline.enabled} — 离线消息存储是否启用</li>
 *   <li>{@code rateLimit.enabled} — 速率限制是否启用</li>
 *   <li>{@code heartbeat.serverInterval} — 服务端心跳间隔（ms）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketHealthIndicator implements HealthIndicator {

    private final WebSocketProperties properties;
    private final OnlineUserService onlineUserService;
    private final AtomicLong activeConnections;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        try {
            builder.withDetail("local.activeConnections", activeConnections.get());

            boolean clusterEnabled = properties.getCluster().isEnabled();
            builder.withDetail("cluster.enabled", clusterEnabled);
            if (clusterEnabled && redisTemplate != null) {
                String pong = redisTemplate.getConnectionFactory().getConnection().ping();
                builder.withDetail("cluster.redisReachable", "PONG".equalsIgnoreCase(pong));
            } else {
                builder.withDetail("cluster.redisReachable", false);
            }

            builder.withDetail("offline.enabled", properties.getOffline().isEnabled());
            builder.withDetail("rateLimit.enabled", properties.getRateLimit().isEnabled());
            builder.withDetail("heartbeat.serverIntervalMs", properties.getHeartbeat().getServerInterval());
            builder.withDetail("heartbeat.clientIntervalMs", properties.getHeartbeat().getClientInterval());
            builder.withDetail("messageSizeLimitBytes", properties.getMessageSizeLimit());
            builder.withDetail("sessionTtlSeconds", properties.getSessionTtlSeconds());

            if (redisTemplate != null) {
                builder.withDetail("onlineUserService", "redis-backed");
            } else {
                builder.withDetail("onlineUserService", "no-op (Redis unavailable)");
            }

        } catch (Exception e) {
            log.warn("[WS-Health] 健康检查异常: {}", e.getMessage());
            builder.down(e).withDetail("error", e.getMessage());
        }

        return builder.build();
    }
}
