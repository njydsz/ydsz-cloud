package com.njydsz.pmis.common.redis.service.ops;

/**
 * Pipeline 操作接口
 *
 * <p>提供 Pipeline 模式下简化版的 Redis 操作方法，避免直接处理底层字节序列化。
 * 适用于需要自定义复杂 Pipeline 操作的场景。
 *
 * <p><b>注意：</b>所有操作都在 Pipeline 上下文中执行，不会立即返回结果。
 * 结果将在 Pipeline 执行完毕后统一返回。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface RedisPipelineOps {

    /**
     * 设置字符串值
     *
     * @param key   键
     * @param value 值
     */
    void setString(String key, Object value);

    /**
     * 设置字符串值并指定过期时间（秒）
     *
     * @param key         键
     * @param value       值
     * @param expireSeconds 过期时间（秒）
     */
    void setString(String key, Object value, long expireSeconds);

    /**
     * 获取字符串值
     *
     * @param key 键
     */
    void getString(String key);

    /**
     * 删除键
     *
     * @param key 键
     */
    void delete(String key);

    /**
     * 检查键是否存在
     *
     * @param key 键
     */
    void exists(String key);

    /**
     * 设置 Hash 字段值
     *
     * @param key   Redis 键
     * @param field 字段名
     * @param value 字段值
     */
    void hashPut(String key, String field, Object value);

    /**
     * 获取 Hash 字段值
     *
     * @param key   Redis 键
     * @param field 字段名
     */
    void hashGet(String key, String field);

    /**
     * 删除 Hash 字段
     *
     * @param key    Redis 键
     * @param fields 字段名列表
     */
    void hashDelete(String key, Object... fields);

    /**
     * List 右侧追加元素
     *
     * @param key   Redis 键
     * @param value 元素值
     */
    void listRightPush(String key, Object value);

    /**
     * List 左侧追加元素
     *
     * @param key   Redis 键
     * @param value 元素值
     */
    void listLeftPush(String key, Object value);

    /**
     * 获取 List 范围元素
     *
     * @param key   Redis 键
     * @param start 起始索引
     * @param end   结束索引
     */
    void listRange(String key, long start, long end);

    /**
     * Set 添加元素
     *
     * @param key    Redis 键
     * @param values 元素值列表
     */
    void setAdd(String key, Object... values);

    /**
     * Set 获取所有成员
     *
     * @param key Redis 键
     */
    void setMembers(String key);

    /**
     * 设置键的过期时间
     *
     * @param key         键
     * @param expireSeconds 过期时间（秒）
     */
    void expire(String key, long expireSeconds);

    /**
     * 递增（整数）
     *
     * @param key    键
     * @param delta  增量
     */
    void incrBy(String key, long delta);

    /**
     * 递增（浮点数）
     *
     * @param key    键
     * @param delta  增量
     */
    void incrByFloat(String key, double delta);
}
