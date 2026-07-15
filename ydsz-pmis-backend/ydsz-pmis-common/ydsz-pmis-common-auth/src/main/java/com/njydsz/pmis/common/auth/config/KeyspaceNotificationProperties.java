package com.njydsz.pmis.common.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Redis Keyspace Notification 配置属性。
 *
 * <p>用于配置权限缓存失效的精确通知机制。
 * 当权限数据在 Redis 中被修改/删除时，通过 Keyspace Notification 精确触发缓存失效，
 * 替代原有的 Pub/Sub 广播模式。
 *
 * @since 1.0.0
 * 
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.keyspace-notification")
public class KeyspaceNotificationProperties {

    /**
     * 是否启用 Keyspace Notification 缓存失效。
     * 默认启用，替代 Pub/Sub 模式实现精确缓存失效。
     */
    private boolean enabled = true;

    /**
     * 监听的 Redis key 前缀。
     * 当匹配前缀的 key 发生变更时，触发缓存失效。
     * 默认监听 ydsz-auth:role-* 相关 key。
     */
    private String keyPrefixPattern = "ydsz-auth:role*";
}
