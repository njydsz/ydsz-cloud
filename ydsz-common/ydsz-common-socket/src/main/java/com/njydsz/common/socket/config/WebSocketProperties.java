package com.njydsz.common.socket.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * WebSocket 可配置化属性。
 *
 * <p>支持通过 YAML 配置文件灵活控制 WebSocket 端点、心跳、消息大小限制、
 * 空闲超时、跨域策略、集群广播开关、熔断降级、消息可靠性、等，避免硬编码。
 *
 * <p>配置示例：
 * <pre>{@code
 * ydsz:
 *   websocket:
 *     enabled: true
 *     endpoint: /ws
 *     allowed-origin-patterns: ["*"]
 *     sock-js-enabled: true
 *     heartbeat:
 *       server-interval: 10000
 *       client-interval: 10000
 *       stale-session-timeout: 60000
 *     message-size-limit: 65536
 *     send-timeout-ms: 5000
 *     session-ttl-seconds: 3600
 *     cluster:
 *       enabled: true
 *       channel: ydsz:ws:cluster:push
 *     offline:
 *       enabled: true
 *       max-cache: 100
 *       ttl: 30d
 *       db-persist-threshold: 50
 *     rate-limit:
 *       enabled: false
 *       max-per-user-per-minute: 60
 *       max-per-ip-per-minute: 300
 *     circuit-breaker:
 *       failure-rate-threshold: 0.5
 *       sliding-window-size: 20
 *       half-open-after: 30s
 *     retry:
 *       enabled: true
 *       max-retries: 3
 *       retry-delay: 5s
 *       dead-letter-enabled: true
 *     ack:
 *       enabled: false
 *       timeout: 30s
 *     connection-limit:
 *       max-global-connections: 10000
 *       max-per-user-connections: 5
 *     compression:
 *       enabled: false
 *       min-size: 1024
 *     slow-connection:
 *       enabled: true
 *       threshold-ms: 5000
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.websocket")
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

    /** 熔断降级配置 */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /** 消息重试配置 */
    private Retry retry = new Retry();

    /** ACK 确认配置 */
    private Ack ack = new Ack();

    /** 连接数限制配置 */
    private ConnectionLimit connectionLimit = new ConnectionLimit();

    /** 消息压缩配置 */
    private Compression compression = new Compression();

    /** 慢连接检测配置 */
    private SlowConnection slowConnection = new SlowConnection();

    /** 多端登录策略配置 */
    private MultiDevice multiDevice = new MultiDevice();

    /**
     * 心跳保活配置。
     *
     * <p>定义服务端与客户端的心跳发送间隔，以及判定僵尸会话的超时阈值；
     * 超时未续期的 Session 将被心跳处理器主动清理。
     */
    @Data
    public static class Heartbeat {
        /** 服务端心跳间隔（毫秒） */
        private long serverInterval = 10000L;
        /** 客户端心跳间隔（毫秒） */
        private long clientInterval = 10000L;
        /** 僵尸 Session 超时阈值（毫秒），超过此时间未收到心跳则清理 */
        private long staleSessionTimeout = 60000L;
    }

    /**
     * 集群广播配置。
     *
     * <p>多实例部署时通过 Redis Pub/Sub 将推送消息广播到其他实例，
     * 实现跨节点消息可达；未启用时退化为仅本地推送。
     */
    @Data
    public static class Cluster {
        /** 是否启用集群广播（Redis Pub/Sub） */
        private boolean enabled = true;
        /** Redis Channel 名称 */
        private String channel = "ydsz:ws:cluster:push";
    }

    /**
     * 离线消息补偿配置。
     *
     * <p>接收方离线期间缓存待投递消息，上线后拉取补投；缓存超出上限时
     * 溢出到数据库持久化，防止 Redis 内存被离线消息打满。
     */
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

    /**
     * 速率限制配置。
     *
     * <p>按用户/IP 维度限制消息发送频率，防止单客户端刷屏或恶意压测
     * 打爆服务端；超限消息被直接拒绝。
     */
    @Data
    public static class RateLimit {
        /** 是否启用速率限制 */
        private boolean enabled = false;
        /** 每用户每分钟最大消息数 */
        private int maxPerUserPerMinute = 60;
        /** 每 IP 每分钟最大消息数 */
        private int maxPerIpPerMinute = 300;
    }

    /**
     * 熔断降级配置。
     *
     * <p>基于滑动窗口内失败率统计，超过阈值后熔断对外部依赖（如 Redis）的调用，
     * 熔断期满后进入半开状态试探恢复，保护推送链路在主链路故障时不被拖垮。
     */
    @Data
    public static class CircuitBreaker {
        /** 失败率阈值（0-1），超过则熔断 */
        private double failureRateThreshold = 0.5;
        /** 滑动窗口大小 */
        private int slidingWindowSize = 20;
        /** 熔断后多久进入半开状态 */
        private Duration halfOpenAfter = Duration.ofSeconds(30);
    }

    /**
     * 消息重试配置。
     *
     * <p>未确认或发送失败的消息按延迟策略重新投递，达到最大重试次数后
     * 转入死信队列，避免无限重试占用队列资源。
     */
    @Data
    public static class Retry {
        /** 是否启用消息重试 */
        private boolean enabled = true;
        /** 最大重试次数 */
        private int maxRetries = 3;
        /** 重试延迟 */
        private Duration retryDelay = Duration.ofSeconds(5);
        /** 是否启用死信队列 */
        private boolean deadLetterEnabled = true;
    }

    /**
     * ACK 确认配置。
     *
     * <p>客户端收到消息后回执 ACK，服务端在超时时间内未收到回执的
     * 消息将被视为未送达，进而触发重试或转入离线存储。
     */
    @Data
    public static class Ack {
        /** 是否启用 ACK 确认 */
        private boolean enabled = false;
        /** ACK 超时时间 */
        private Duration timeout = Duration.ofSeconds(30);
    }

    /**
     * 连接数限制配置。
     *
     * <p>在握手阶段对全局与单用户连接数设限，防止连接数超载导致
     * 线程/内存资源耗尽（OOM 或雪崩）。
     */
    @Data
    public static class ConnectionLimit {
        /** 全局最大连接数 */
        private int maxGlobalConnections = 10000;
        /** 每用户最大连接数 */
        private int maxPerUserConnections = 5;
    }

    /**
     * 消息压缩配置。
     *
     * <p>超过最小大小阈值的推送消息先压缩再发送，降低带宽与 Redis 存储开销；
     * 对极小消息压缩收益有限，默认关闭以避免 CPU 浪费。
     */
    @Data
    public static class Compression {
        /** 是否启用消息压缩 */
        private boolean enabled = false;
        /** 触发压缩的最小消息大小（字节） */
        private int minSize = 1024;
    }

    /**
     * 慢连接检测配置。
     *
     * <p>检测处理耗时超过阈值的连接并告警/干预，识别异常客户端
     * 或网络抖动导致的慢消费，防止其拖慢整体推送吞吐。
     */
    @Data
    public static class SlowConnection {
        /** 是否启用慢连接检测 */
        private boolean enabled = true;
        /** 慢连接阈值（毫秒） */
        private long thresholdMs = 5000L;
    }

    /**
     * 多端登录策略配置。
     *
     * <p>控制同一用户多设备同时在线的行为策略：
     * <ul>
     *   <li>ALLOW_ALL — 允许所有设备同时在线（默认）</li>
     *   <li>MUTEX — 同一时刻仅允许一个设备在线，新连接建立时关闭旧连接</li>
     *   <li>NEW_REPLACE_OLD — 新连接建立时踢出最早的连接</li>
     * </ul>
     */
    @Data
    public static class MultiDevice {
        /** 多端登录策略：ALLOW_ALL / MUTEX / NEW_REPLACE_OLD */
        private String policy = "ALLOW_ALL";
        /** 每用户最大并发 Session 数 */
        private int maxSessionsPerUser = 5;
    }
}
