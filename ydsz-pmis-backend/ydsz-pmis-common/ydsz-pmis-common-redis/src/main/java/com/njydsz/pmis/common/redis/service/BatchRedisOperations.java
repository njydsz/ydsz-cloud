package com.njydsz.pmis.common.redis.service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.njydsz.pmis.common.redis.cluster.ClusterSlotUtil;
import com.njydsz.pmis.common.util.collection.CollectionUtils;
import com.njydsz.pmis.common.json.Json;

/**
 * 批量 Redis 操作接口
 *
 * <p>提供高效的批量操作方法，包括：
 * <ul>
 *   <li>MGET：批量获取值</li>
 *   <li>MSET：批量设置值</li>
 *   <li>MDEL：批量删除键</li>
 *   <li>批量 Hash 操作</li>
 * </ul>
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>MGET/MSET/MDEL 使用 Redis 原生批量命令，减少网络往返</li>
 *   <li>msetWithExpire/mexpire/hmexists 使用 Pipeline 模式，进一步提升性能</li>
 *   <li>过期时间添加随机偏移防止缓存雪崩</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 批量获取
 * List<String> keys = Arrays.asList("key1", "key2", "key3");
 * List<String> values = batchOps.mget(keys);
 *
 * // 批量设置
 * Map<String, Object> kvs = new HashMap<>();
 * kvs.put("key1", "value1");
 * kvs.put("key2", "value2");
 * batchOps.mset(kvs);
 *
 * // 批量删除
 * batchOps.mdel(keys);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface BatchRedisOperations {

    Logger log = LoggerFactory.getLogger(BatchRedisOperations.class);

    /**
     * 获取 RedisTemplate
     *
     * @return RedisTemplate 实例
     */
    RedisTemplate<String, Object> getRedisTemplate();

    /**
     * 批量获取值（MGET）
     *
     * <p>返回与 keys 顺序一致的值列表，缺失的值为 null。
     * 使用 Redis 原生的 MGET 命令，保证原子性和高性能。
     *
     * @param keys 键列表
     * @return 值列表（与 keys 顺序对应）
     */
    default List<?> mget(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptyList();
        }
        List<Object> values = getRedisTemplate().opsForValue().multiGet(keys);
        if (values == null) {
            return Collections.emptyList();
        }
        return values;
    }

    /**
     * 批量获取值（泛型版本）
     *
     * @param keys  键列表
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 值列表
     */
    default <T> List<T> mgetObjects(List<String> keys, Class<T> clazz) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptyList();
        }
        List<Object> values = getRedisTemplate().opsForValue().multiGet(keys);
        if (values == null) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value == null) {
                result.add(null);
            } else if (clazz.isInstance(value)) {
                result.add(clazz.cast(value));
            } else {
                    try {
                        String json = Json.toJson(value);
                        T converted = Json.toObject(json, clazz);
                        result.add(converted);
                    } catch (Exception e) {
                        log.error("mgetObjects JSON转换失败, key index: {}", result.size(), e);
                        result.add(null);
                    }
                }
        }
        return result;
    }

    /**
     * 批量设置值（MSET）
     *
     * <p>使用 Redis 原生的 MSET 命令，保证原子性和高性能。
     *
     * @param keyValues 键值对映射
     * @return true-设置成功
     */
    default boolean mset(Map<String, Object> keyValues) {
        if (CollectionUtils.isEmpty(keyValues)) {
            return false;
        }
        try {
            getRedisTemplate().opsForValue().multiSet(keyValues);
            return true;
        } catch (Exception e) {
            log.error("mset批量设置失败, keys: {}", keyValues.keySet(), e);
            return false;
        }
    }

    /**
     * 批量设置值（带过期时间）
     *
     * <p>使用 Pipeline 批量执行 SET 命令并设置过期时间，减少网络往返。
     * 过期时间自动添加随机偏移防止缓存雪崩。
     *
     * @param keyValues     键值对映射
     * @param expireSeconds 过期时间（秒）
     * @return true-设置成功
     * @throws RuntimeException 当 Pipeline 操作失败时抛出异常
     */
    default boolean msetWithExpire(Map<String, Object> keyValues, long expireSeconds) {
        if (CollectionUtils.isEmpty(keyValues) || expireSeconds <= 0) {
            return false;
        }
        if (isClusterMode()) {
            return msetWithExpireInCluster(keyValues, expireSeconds);
        }
        RedisTemplate<String, Object> template = getRedisTemplate();
        template.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
                byte[] rawKey = template.getStringSerializer().serialize(entry.getKey());
                byte[] rawValue = ((RedisSerializer<Object>) template.getValueSerializer()).serialize(entry.getValue());
                if (rawKey != null && rawValue != null) {
                    long jitteredExpire = addJitterToExpire(expireSeconds);
                    connection.stringCommands().set(rawKey, rawValue);
                    connection.keyCommands().expire(rawKey, jitteredExpire);
                }
            }
            return null;
        });
        return true;
    }

    /**
     * 集群模式下按 slot 分组执行 MSET with expire
     */
    private boolean msetWithExpireInCluster(Map<String, Object> keyValues, long expireSeconds) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(keyValues.entrySet());
        Map<Integer, List<Map.Entry<String, Object>>> slotGroups = ClusterSlotUtil.groupBySlot(
                entries, Map.Entry::getKey);
        RedisTemplate<String, Object> template = getRedisTemplate();
        for (List<Map.Entry<String, Object>> group : slotGroups.values()) {
            template.executePipelined((RedisCallback<Object>) connection -> {
                for (Map.Entry<String, Object> entry : group) {
                    byte[] rawKey = template.getStringSerializer().serialize(entry.getKey());
                    RedisSerializer valueSerializer = template.getValueSerializer();
                    byte[] rawValue = valueSerializer.serialize(entry.getValue());
                    if (rawKey != null && rawValue != null) {
                        long jitteredExpire = addJitterToExpire(expireSeconds);
                        connection.stringCommands().set(rawKey, rawValue);
                        connection.keyCommands().expire(rawKey, jitteredExpire);
                    }
                }
                return null;
            });
        }
        return true;
    }

    /**
     * 批量删除键（MDEL）
     *
     * <p>使用 Redis 原生的 DEL 命令，保证原子性。
     *
     * @param keys 键列表
     * @return 删除的键数量
     */
    default long mdel(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return 0;
        }
        Long result = getRedisTemplate().execute((RedisCallback<Long>) connection -> {
            byte[][] rawKeys = keys.stream()
                    .map(k -> getRedisTemplate().getStringSerializer().serialize(k))
                    .toArray(byte[][]::new);
            return connection.keyCommands().del(rawKeys);
        });
        return result != null ? result : 0;
    }

    /**
     * 批量删除键（数组形式）
     *
     * @param keys 键数组
     * @return 删除的键数量
     */
    default long mdel(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0;
        }
        return mdel(Arrays.asList(keys));
    }

    /**
     * 批量检查键是否存在
     *
     * <p>使用 Pipeline 批量执行 EXISTS 命令，减少网络往返。
     *
     * @param keys 键列表
     * @return 存在的键列表
     */
    default List<String> mexists(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptyList();
        }
        List<Object> results = getRedisTemplate().executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                byte[] keyBytes = getRedisTemplate().getStringSerializer().serialize(key);
                if (keyBytes != null) {
                    connection.keyCommands().exists(keyBytes);
                }
            }
            return null;
        });
        List<String> existingKeys = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            if (Boolean.TRUE.equals(results.get(i))) {
                existingKeys.add(keys.get(i));
            }
        }
        return existingKeys;
    }

    /**
     * 批量设置过期时间
     *
     * <p>使用 Pipeline 批量执行 EXPIRE 命令，减少网络往返。
     * 过期时间自动添加随机偏移防止缓存雪崩。
     *
     * @param keysExpireMap 键-过期时间映射（键 -> 过期秒数）
     * @return 成功设置的键数量
     * @throws RuntimeException 当 Pipeline 操作失败时抛出异常
     */
    default long mexpire(Map<String, Long> keysExpireMap) {
        if (CollectionUtils.isEmpty(keysExpireMap)) {
            return 0;
        }
        RedisTemplate<String, Object> template = getRedisTemplate();
        List<Object> results = template.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, Long> entry : keysExpireMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    byte[] rawKey = template.getStringSerializer().serialize(entry.getKey());
                    if (rawKey != null) {
                        long jitteredExpire = addJitterToExpire(entry.getValue());
                        connection.keyCommands().expire(rawKey, jitteredExpire);
                    }
                }
            }
            return null;
        });
        long successCount = 0;
        for (Object result : results) {
            if (Boolean.TRUE.equals(result)) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * 批量获取 Hash 字段
     *
     * @param key    Redis 键
     * @param fields 字段列表
     * @param clazz  值类型
     * @param <T>    值类型
     * @return 字段值列表
     */
    default <T> List<T> hmget(String key, Collection<Object> fields, Class<T> clazz) {
        if (key == null || CollectionUtils.isEmpty(fields)) {
            return Collections.emptyList();
        }
        List<Object> result = getRedisTemplate().opsForHash().multiGet(key, new ArrayList<>(fields));
        return result.stream().map(clazz::cast).collect(Collectors.toList());
    }

    /**
     * 批量设置 Hash 字段
     *
     * @param key   Redis 键
     * @param fieldValues 字段-值映射
     * @return true-设置成功
     */
    default boolean hmset(String key, Map<String, ?> fieldValues) {
        if (key == null || CollectionUtils.isEmpty(fieldValues)) {
            return false;
        }
        try {
            getRedisTemplate().opsForHash().putAll(key, fieldValues);
            return true;
        } catch (Exception e) {
            log.error("hmset批量设置Hash失败, key: {}, fields: {}", key, fieldValues.keySet(), e);
            return false;
        }
    }

    /**
     * 批量删除 Hash 字段
     *
     * @param key    Redis 键
     * @param fields 字段列表
     * @return 删除的字段数量
     */
    default long hmdel(String key, Object... fields) {
        if (key == null || fields == null || fields.length == 0) {
            return 0;
        }
        Long result = getRedisTemplate().opsForHash().delete(key, fields);
        return result != null ? result : 0;
    }

    /**
     * 批量判断 Hash 字段是否存在
     *
     * <p>使用 Pipeline 批量执行 HEXISTS 命令，减少网络往返。
     *
     * @param key    Redis 键
     * @param fields 字段列表
     * @return 存在的字段列表
     */
    default List<Object> hmexists(String key, Object... fields) {
        if (key == null || fields == null || fields.length == 0) {
            return Collections.emptyList();
        }
        RedisSerializer hashValueSerializer = getRedisTemplate().getHashValueSerializer();
        List<Object> results = getRedisTemplate().executePipelined((RedisCallback<Object>) connection -> {
            for (Object field : fields) {
                byte[] keyBytes = getRedisTemplate().getStringSerializer().serialize(key);
                byte[] fieldBytes = hashValueSerializer.serialize(field);
                if (keyBytes != null && fieldBytes != null) {
                    connection.hashCommands().hExists(keyBytes, fieldBytes);
                }
            }
            return null;
        });
        List<Object> existingFields = new ArrayList<>();
        for (int i = 0; i < fields.length; i++) {
            if (Boolean.TRUE.equals(results.get(i))) {
                existingFields.add(fields[i]);
            }
        }
        return existingFields;
    }

    /**
     * 为过期时间添加随机偏移，防止缓存雪崩
     *
     * @param baseSeconds 基础过期时间（秒）
     * @return 添加偏移后的过期时间
     */
    private static long addJitterToExpire(long baseSeconds) {
        if (baseSeconds <= 0) {
            return baseSeconds;
        }
        long jitter = baseSeconds / 10;
        if (jitter <= 0) {
            jitter = 1;
        }
        return baseSeconds + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
    }

    private boolean isClusterMode() {
        try {
            Boolean result = getRedisTemplate().execute((RedisCallback<Boolean>) connection ->
                    connection instanceof RedisClusterConnection);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("isClusterMode检测失败, 默认返回false", e);
            return false;
        }
    }
}
