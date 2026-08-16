package com.njydsz.common.netty.config;

import jakarta.validation.constraints.Min;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Netty 通用配置属性。
 *
 * <p>支持通过 YAML 配置文件控制 TCP Server/Client 的线程模型、连接超时、
 * 保活策略、SSL/TLS、空闲检测、流量整形、断线重连等参数。
 *
 * <p>配置示例：
 * <pre>{@code
 * ydsz:
 *   netty:
 *     boss-threads: 1
 *     worker-threads: 0
 *     so-keep-alive: true
 *     so-backlog: 128
 *     connect-timeout-millis: 5000
 *     shared-event-loop: true
 *     shutdown-quiet-period-seconds: 2
 *     shutdown-timeout-seconds: 15
 *     fail-fast: true
 *     native-transport: auto
 *     idle:
 *       reader-idle-seconds: 60
 *       writer-idle-seconds: 30
 *       all-idle-seconds: 0
 *     ssl:
 *       enabled: false
 *       key-store: classpath:keystore.p12
 *       key-store-password: changeit
 *       key-store-type: PKCS12
 *       need-client-auth: false
 *     traffic-shaping:
 *       enabled: false
 *       write-limit: 0
 *       read-limit: 0
 *       global: false
 *     reconnect:
 *       enabled: true
 *       initial-delay-ms: 1000
 *       max-delay-ms: 60000
 *       max-retries: -1
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.netty")
public class NettyProperties {

    /** Boss 线程数（接受连接） */
    @Min(0)
    private int bossThreads = 1;

    /** Worker 线程数（0 = 默认 CPU 核数 * 2） */
    @Min(0)
    private int workerThreads = 0;

    /** SO_KEEPALIVE */
    private boolean soKeepAlive = true;

    /** SO_BACKLOG */
    @Min(1)
    private int soBacklog = 128;

    /** TCP_NODELAY */
    private boolean tcpNoDelay = true;

    /** 连接超时（毫秒） */
    @Min(0)
    private int connectTimeoutMillis = 5000;

    /** 是否共享 EventLoopGroup（true=全局复用，false=每 Server/Client 独立） */
    private boolean sharedEventLoop = true;

    /** 优雅关闭静默期（秒） */
    @Min(0)
    private long shutdownQuietPeriodSeconds = 2L;

    /** 优雅关闭超时（秒） */
    @Min(1)
    private long shutdownTimeoutSeconds = 15L;

    /** Server 启动失败时是否终止应用（fail-fast） */
    private boolean failFast = true;

    /** 原生传输模式（auto=自动检测, enabled=强制启用, disabled=禁用） */
    private NativeTransportMode nativeTransport = NativeTransportMode.AUTO;

    /**
     * Netty 原生传输启用模式。
     *
     * <p>控制是否优先使用 JNI 原生传输（epoll / kqueue / io_uring）。
     * {@link #AUTO} 会根据运行平台自动选择，无法加载原生库时回退到 NIO。
     */
    public enum NativeTransportMode {
        /** 自动检测平台并选择原生传输，失败时回退 NIO */
        AUTO,
        /** 强制启用原生传输 */
        ENABLED,
        /** 禁用原生传输，始终使用 NIO */
        DISABLED
    }

    /** 空闲检测配置 */
    private Idle idle = new Idle();

    /** SSL/TLS 配置 */
    private Ssl ssl = new Ssl();

    /** 流量整形配置 */
    private TrafficShaping trafficShaping = new TrafficShaping();

    /** 断线重连配置 */
    private Reconnect reconnect = new Reconnect();

    /**
     * 空闲检测配置（IdleStateHandler）。
     *
     * <p>用于探测连接空闲状态，配合心跳机制及时发现半开连接与死连接；
     * 任一超时时间设为 0 表示不检测对应方向。
     */
    @Data
    public static class Idle {
        /** 读空闲超时（秒），0 表示不检测 */
        @Min(0)
        private long readerIdleSeconds = 60L;
        /** 写空闲超时（秒），0 表示不检测 */
        @Min(0)
        private long writerIdleSeconds = 30L;
        /** 全双工空闲超时（秒），0 表示不检测 */
        @Min(0)
        private long allIdleSeconds = 0L;
    }

    /**
     * SSL/TLS 加密配置。
     *
     * <p>用于为 TCP Server/Client 通道启用 SSL/TLS 加密，支持单向与双向认证；
     * 密钥库/信任库路径支持 classpath: 前缀。
     */
    @Data
    public static class Ssl {
        /** 是否启用 SSL/TLS */
        private boolean enabled = false;
        /** 密钥库路径 */
        private String keyStore;
        /** 密钥库密码 */
        private String keyStorePassword;
        /** 密钥库类型（PKCS12 / JKS） */
        private String keyStoreType = "PKCS12";
        /** 信任库路径（双向认证时使用） */
        private String trustStore;
        /** 信任库密码 */
        private String trustStorePassword;
        /** 信任库类型 */
        private String trustStoreType = "PKCS12";
        /** 是否要求客户端认证（双向认证） */
        private boolean needClientAuth = false;
    }

    /**
     * 流量整形配置（GlobalTrafficShapingHandler）。
     *
     * <p>限制读写带宽，防止单个连接或整个 Server 的流量峰值打爆对端或上游；
     * 写/读限速设为 0 表示不限速。
     */
    @Data
    public static class TrafficShaping {
        /** 是否启用流量整形 */
        private boolean enabled = false;
        /** 写限速（bytes/s），0 表示不限 */
        @Min(0)
        private long writeLimit = 0L;
        /** 读限速（bytes/s），0 表示不限 */
        @Min(0)
        private long readLimit = 0L;
        /** 检查间隔（毫秒） */
        @Min(100)
        private long checkIntervalMs = 1000L;
        /** 是否使用全局流量整形（true=限制整个 Server 总带宽） */
        private boolean global = false;
    }

    /**
     * 断线重连配置。
     *
     * <p>当连接异常断开时按指数退避策略自动重连：
     * 重连延迟从 initialDelayMs 起翻倍增长，直至达到 maxDelayMs 上限；
     * 若配置的最终延迟（initialDelayMs * 2^(maxRetries-1)）未达 maxDelayMs 则按计算结果重试。
     */
    @Data
    public static class Reconnect {
        /** 是否启用断线重连 */
        private boolean enabled = true;
        /** 初始重连延迟（毫秒） */
        @Min(100)
        private long initialDelayMs = 1000L;
        /** 最大重连延迟（毫秒） */
        @Min(1000)
        private long maxDelayMs = 60000L;
        /** 最大重试次数（-1 = 无限重试） */
        private int maxRetries = -1;
    }
}
