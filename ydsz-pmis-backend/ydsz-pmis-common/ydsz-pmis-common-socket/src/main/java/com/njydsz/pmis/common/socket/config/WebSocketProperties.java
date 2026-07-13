package com.njydsz.pmis.common.socket.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * WebSocket 可配置化属性。
 *
 * <p>支持通过 YAML 配置文件灵活控制 WebSocket 端点、心跳、消息大小限制、
 * 空闲超时、跨域策略、集群广播开关等，避免硬编码。
 *
 * <p>配置示例：
 * <pre>{@code
 * pmis:
 *   websocket:
 *     enabled: true
 *     endpoint: /ws
 *     allowed-origin-patterns: ["*"]
 *     heartbeat:
 *       server-interval: 10000
 *       client-interval: 10000
 *     message-size-limit: 65536
 *     send-timeout-ms: 5000
 *     session-ttl-seconds: 3600
 *     cluster:
 *       enabled: true
 *       channel: pmis:ws:cluster:push
 *     offline:
 *       enabled: true
 *       max-cache: 100
 *       ttl-seconds: 2592000
 *     rate-limit:
 *       enabled: false
 *       max-per-user-per-minute: 60
 *       max-per-ip-per-minute: 300
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.websocket")
public class WebSocketProperties {

    /** 是否启用 WebSocket 模块 */
    private boolean enabled = true;

    /** WebSocket 端点路径 */
    private String endpoint = "/ws";

    /** 允许的 Origin 模式列表 */
    private List<String> allowedOriginPatterns = List.of("*");

    /** 是否启用 SockJS 回退 */
    private boolean sockJsEnabled = true;

    /** 心跳配置 */
    private Heartbeat heartbeat = new Heartbeat();

    /** 最大消息大小（字节） */
    private int messageSizeLimit = 64 * 1024;

    /** 消息发送超时（毫秒） */
    private long sendTimeoutMs = 5000L;

    /** Session TTL（秒），心跳未续期时自动清理 */
    private long sessionTtlSeconds = 3600L;

    /** 集群广播配置 */
    private Cluster cluster = new Cluster();

    /** 离线消息配置 */
    private Offline offline = new Offline();

    /** 速率限制配置 */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Heartbeat {
        /** 服务端心跳间隔（毫秒） */
        private long serverInterval = 10000L;
        /** 客户端心跳间隔（毫秒） */
        private long clientInterval = 10000L;
    }

    @Data
    public static class Cluster {
        /** 是否启用集群广播（Redis Pub/Sub） */
        private boolean enabled = true;
        /** Redis Channel 名称 */
        private String channel = "pmis:ws:cluster:push";
    }

    @Data
    public static class Offline {
        /** 是否启用离线消息补偿 */
        private boolean enabled = true;
        /** Redis 缓存最大条数 */
        private int maxCache = 100;
        /** 缓存 TTL */
        private Duration ttl = Duration.ofDays(30);
        /** Redis 溢出后的数据库持久化阈值 */
        private int dbPersistThreshold = 50;
    }

    @Data
    public static class RateLimit {
        /** 是否启用速率限制 */
        private boolean enabled = false;
        /** 每用户每分钟最大消息数 */
        private int maxPerUserPerMinute = 60;
        /** 每 IP 每分钟最大消息数 */
        private int maxPerIpPerMinute = 300;
    }
}
