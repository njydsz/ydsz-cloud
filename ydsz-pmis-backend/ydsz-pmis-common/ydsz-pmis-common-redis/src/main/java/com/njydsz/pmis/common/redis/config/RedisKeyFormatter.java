package com.njydsz.pmis.common.redis.config;

/**
 * Redis Key 格式化工具类
 *
 * <p>统一管理 Redis Key 前缀拼接逻辑，各 Ops 类可委托此类完成 Key 格式化，
 * 避免在各 Ops 类中重复实现 {@code formatKey} 方法。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class RedisKeyFormatter {

    private RedisKeyFormatter() {
    }

    /**
     * 格式化 Key（添加前缀）
     *
     * @param key    业务 Key
     * @param prefix Key 前缀（可为 null 或空）
     * @return 格式化后的 Key（前缀:业务Key 或 业务Key）
     */
    public static String format(String key, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }

    /**
     * 格式化 Key（从 RedisProperties 获取前缀）
     *
     * @param key             业务 Key
     * @param redisProperties Redis 配置属性
     * @return 格式化后的 Key
     */
    public static String format(String key, RedisProperties redisProperties) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        return format(key, prefix);
    }
}
