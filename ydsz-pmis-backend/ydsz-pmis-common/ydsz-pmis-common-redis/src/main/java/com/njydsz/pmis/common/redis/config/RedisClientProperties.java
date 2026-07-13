package com.njydsz.pmis.common.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Redis 客户端配置属性类
 *
 * <p>提供 Redis 客户端选择及相关配置，支持通过 application.yml 中的
 * {@code ydsz.redis.client} 前缀注入配置。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   redis:
 *     client:
 *       type: jedis
 *       read-from: REPLICA_PREFERRED
 *       pool:
 *         max-active: 16
 *         max-idle: 8
 *         min-idle: 2
 *         max-wait: 3000
 *       ssl:
 *         enabled: true
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.redis.client")
public class RedisClientProperties {

    /**
     * 客户端类型（默认 JEDIS）
     */
    private RedisClientType type = RedisClientType.JEDIS;

    /**
     * 连接池配置
     */
    private Pool pool = new Pool();

    /**
     * SSL 配置
     */
    private Ssl ssl = new Ssl();

    /**
     * 读策略（仅 Lettuce 客户端生效，用于读写分离场景）
     * <p>可选值：MASTER、MASTER_PREFERRED、REPLICA_PREFERRED、REPLICA、NEAREST
     * <p>默认值：MASTER（仅从主节点读取）
     */
    private ReadFrom readFrom = ReadFrom.MASTER;

    /**
     * Redis 读策略枚举（对应 Lettuce 的 io.lettuce.core.ReadFrom）
     * <p>MASTER / MASTER_PREFERRED 已弃用，建议使用 UPSTREAM / UPSTREAM_PREFERRED
     */
    public enum ReadFrom {
        /** 仅从主节点读取（已弃用，请使用 UPSTREAM） */
        MASTER,
        /** 优先从主节点读取，主节点不可用时从副本读取（已弃用，请使用 UPSTREAM_PREFERRED） */
        MASTER_PREFERRED,
        /** 仅从上游（主）节点读取 */
        UPSTREAM,
        /** 优先从上游（主）节点读取，主节点不可用时从副本读取 */
        UPSTREAM_PREFERRED,
        /** 优先从副本读取，副本不可用时从主节点读取 */
        REPLICA_PREFERRED,
        /** 仅从副本读取 */
        REPLICA,
        /** 从网络拓扑最近的节点读取 */
        NEAREST
    }

    /**
     * 连接池配置类
     */
    @Data
    public static class Pool {

        /**
         * 最大连接数（默认 16）
         */
        private int maxActive = 16;

        /**
         * 最大空闲连接数（默认 8）
         */
        private int maxIdle = 8;

        /**
         * 最小空闲连接数（默认 2）
         */
        private int minIdle = 2;

        /**
         * 获取连接最大等待时间（毫秒），-1 表示无限制
         */
        private long maxWait = -1;

        /**
         * 是否启用连接池（默认启用）
         */
        private boolean enabled = true;
    }

    /**
     * SSL 配置类
     */
    @Data
    public static class Ssl {

        /**
         * 是否启用 SSL（默认 false）
         */
        private boolean enabled = false;
    }
}
