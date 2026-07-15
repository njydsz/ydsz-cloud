package com.njydsz.pmis.common.redis.config;

/**
 * Redis Key 前缀提供者接口
 *
 * <p>业务模块可通过实现此接口，从配置中获取 Redis Key 前缀，
 * 统一管理 Redis Key 命名规范。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RedisKeyPrefixProvider {

    /**
     * 获取 Redis Key 前缀
     *
     * @return Key 前缀
     */
    String getKeyPrefix();

    /**
     * 拼接完整的 Redis Key（前缀 + 业务 Key）
     *
     * @param businessKey 业务 Key
     * @return 完整的 Redis Key
     */
    default String buildKey(String businessKey) {
        String prefix = getKeyPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return businessKey;
        }
        return prefix + ":" + businessKey;
    }
}
