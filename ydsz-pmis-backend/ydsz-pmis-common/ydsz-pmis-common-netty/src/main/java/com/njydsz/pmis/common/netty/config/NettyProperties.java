package com.njydsz.pmis.common.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;


/**
 * Netty 通用配置属性。
 *
 * <p>支持通过 YAML 配置文件控制 TCP Server/Client 的线程模型、连接超时、
 * 保活策略、SSL/TLS、空闲检测、流量整形等参数。
 *
 * <p>配置示例：
 * <pre>{@code
 * pmis:
 *   netty:
 *     boss-threads: 1
 *     worker-threads: 0
 *     so-keep-alive: true
 *     so-backlog: 128
 *     connect-timeout-millis: 5000
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
 *     reconnect:
 *       enabled: true
 *       initial-delay-ms: 1000
 *       max-delay-ms: 60000
 *       max-retries: -1
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.netty")
public class NettyProperties {

    /** Boss 线程数（接受连接） */
    private int bossThreads = 1;

    /** Worker 线程数（0 = 默认 CPU 核数 * 2） */
    private int workerThreads = 0;

    /** SO_KEEPALIVE */
    private boolean soKeepAlive = true;

    /** SO_BACKLOG */
    private int soBacklog = 128;

    /** TCP_NODELAY */
    private boolean tcpNoDelay = true;

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 5000;

    /** 空闲检测配置 */
    private Idle idle = new Idle();

    /** SSL/TLS 配置 */
    private Ssl ssl = new Ssl();

    /** 流量整形配置 */
    private TrafficShaping trafficShaping = new TrafficShaping();

    /** 断线重连配置 */
    private Reconnect reconnect = new Reconnect();

    @Data
    public static class Idle {
        /** 读空闲超时（秒），0 表示不检测 */
        private long readerIdleSeconds = 60L;
        /** 写空闲超时（秒），0 表示不检测 */
        private long writerIdleSeconds = 30L;
        /** 全双工空闲超时（秒），0 表示不检测 */
        private long allIdleSeconds = 0L;
    }

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

    @Data
    public static class TrafficShaping {
        /** 是否启用流量整形 */
        private boolean enabled = false;
        /** 写限速（bytes/s），0 表示不限 */
        private long writeLimit = 0L;
        /** 读限速（bytes/s），0 表示不限 */
        private long readLimit = 0L;
        /** 检查间隔（毫秒） */
        private long checkIntervalMs = 1000L;
    }

    @Data
    public static class Reconnect {
        /** 是否启用断线重连 */
        private boolean enabled = true;
        /** 初始重连延迟（毫秒） */
        private long initialDelayMs = 1000L;
        /** 最大重连延迟（毫秒） */
        private long maxDelayMs = 60000L;
        /** 最大重试次数（-1 = 无限重试） */
        private int maxRetries = -1;
    }
}
