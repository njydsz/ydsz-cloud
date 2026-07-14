package com.njydsz.pmis.common.redis.service.ops;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.redis.config.RedisProperties;
import com.njydsz.pmis.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.pmis.common.util.collection.CollectionUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Hash 操作服务
 *
 * <p>提供 Hash 数据结构的完整操作接口，包括：
 * <ul>
 *   <li>字段读写（单个/批量）</li>
 *   <li>字段删除与存在性检查</li>
 *   <li>字段数量统计</li>
 *   <li>数值递增（整数/浮点数）</li>
 *   <li>字段名/值集合获取</li>
 * </ul>
 *
 * 集成 {@link RedisMetricsCollector} 进行操作指标采集，与 {@link RedisStringOps} 保持一致。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHashOps {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final RedisMetricsCollector metricsCollector;

    // ============================ Hash 操作 =============================

    /**
     * 格式化 Key，添加统一前缀
     */
    private String formatKey(String key) {
        if (key == null) {
            return null;
        }
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }

    /**
     * 获取 Hash 中的单个字段
     *
     * @param key   键
     * @param item  字段名
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 字段值
     */
    public <T> T hGet(String key, String item, Class<T> clazz) {
        if (key == null || item == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hGet", () -> {
                        Object value = redisTemplate.opsForHash().get(formattedKey, item);
                        return value != null ? clazz.cast(value) : null;
                    })
                    : clazz.cast(redisTemplate.opsForHash().get(formattedKey, item));
        } catch (Exception e) {
            recordError("hGet", e);
            log.error("【Redis】HGET 操作失败 | key={} | item={} | error={}", key, item, e);
            return null;
        }
    }

    /**
     * 获取 Hash 的所有字段和值
     *
     * @param key   键
     * @param clazz 值类型
     * @param <T>   值类型
     * @return Hash 的 Map 表示
     */
    public <T> Map<String, T> hGetAll(String key, Class<T> clazz) {
        if (key == null) {
            return Collections.emptyMap();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Map<String, T>> action = () -> {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(formattedKey);
                if (CollectionUtils.isEmpty(entries)) {
                    return Collections.emptyMap();
                }
                return entries.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> String.valueOf(e.getKey()),
                                e -> clazz.cast(e.getValue())
                        ));
            };
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hGetAll", action)
                    : action.get();
        } catch (Exception e) {
            recordError("hGetAll", e);
            log.error("【Redis】HGETALL 操作失败 | key={} | error={}", key, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量获取 Hash 中的字段
     *
     * @param key   键
     * @param items 字段名集合
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 值列表
     */
    public <T> List<T> hMGet(String key, Collection<Object> items, Class<T> clazz) {
        if (key == null || CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<List<T>> action = () -> {
                List<Object> result = redisTemplate.opsForHash().multiGet(formattedKey, new ArrayList<>(items));
                return result.stream().map(clazz::cast).collect(Collectors.toList());
            };
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hMGet", action)
                    : action.get();
        } catch (Exception e) {
            recordError("hMGet", e);
            log.error("【Redis】HMGET 操作失败 | key={} | items={} | error={}", key, items, e);
            return Collections.emptyList();
        }
    }

    /**
     * 设置 Hash 中的单个字段
     *
     * @param key   键
     * @param item  字段名
     * @param value 字段值
     * @return true-设置成功
     */
    public boolean hSet(String key, String item, Object value) {
        if (key == null || item == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            Runnable action = () -> redisTemplate.opsForHash().put(formattedKey, item, value);
            if (metricsCollector != null) {
                metricsCollector.recordOperation("hSet", action);
            } else {
                action.run();
            }
            return true;
        } catch (Exception e) {
            recordError("hSet", e);
            log.error("【Redis】HSET 操作失败 | key={} | item={} | error={}", key, item, e);
            return false;
        }
    }

    /**
     * 设置 Hash 中的单个字段（仅当字段不存在时）
     *
     * @param key   键
     * @param item  字段名
     * @param value 字段值
     * @return true-设置成功（字段原本不存在）
     */
    public boolean hSetIfAbsent(String key, String item, Object value) {
        if (key == null || item == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hSetIfAbsent", () -> Boolean.TRUE.equals(redisTemplate.opsForHash().putIfAbsent(formattedKey, item, value)))
                    : Boolean.TRUE.equals(redisTemplate.opsForHash().putIfAbsent(formattedKey, item, value));
        } catch (Exception e) {
            recordError("hSetIfAbsent", e);
            log.error("【Redis】HSETNX 操作失败 | key={} | item={} | error={}", key, item, e);
            return false;
        }
    }

    /**
     * 批量设置 Hash
     *
     * @param key 键
     * @param map 字段值映射
     * @return true-设置成功
     */
    public boolean hMSet(String key, Map<String, ?> map) {
        if (key == null || CollectionUtils.isEmpty(map)) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            Runnable action = () -> redisTemplate.opsForHash().putAll(formattedKey, map);
            if (metricsCollector != null) {
                metricsCollector.recordOperation("hMSet", action);
            } else {
                action.run();
            }
            return true;
        } catch (Exception e) {
            recordError("hMSet", e);
            log.error("【Redis】HMSET 操作失败 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 删除 Hash 中的字段
     *
     * @param key   键
     * @param items 字段名数组
     * @return 删除的字段数量
     */
    public long hDel(String key, Object... items) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hDel", () -> {
                        Long result = redisTemplate.opsForHash().delete(formattedKey, items);
                        return result != null ? result : 0L;
                    })
                    : Optional.ofNullable(redisTemplate.opsForHash().delete(formattedKey, items)).orElse(0L);
        } catch (Exception e) {
            recordError("hDel", e);
            log.error("【Redis】HDEL 操作失败 | key={} | items={} | error={}", key, Arrays.toString(items), e);
            return 0;
        }
    }

    /**
     * 检查 Hash 中字段是否存在
     *
     * @param key  键
     * @param item 字段名
     * @return true-存在
     */
    public boolean hHasKey(String key, String item) {
        if (key == null || item == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hHasKey", () -> Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(formattedKey, item)))
                    : Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(formattedKey, item));
        } catch (Exception e) {
            recordError("hHasKey", e);
            log.error("【Redis】HEXISTS 操作失败 | key={} | item={} | error={}", key, item, e);
            return false;
        }
    }

    /**
     * 获取 Hash 的字段数量
     *
     * @param key 键
     * @return 字段数量
     */
    public long hSize(String key) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hSize", () -> {
                        Long size = redisTemplate.opsForHash().size(formattedKey);
                        return size != null ? size : 0L;
                    })
                    : Optional.ofNullable(redisTemplate.opsForHash().size(formattedKey)).orElse(0L);
        } catch (Exception e) {
            recordError("hSize", e);
            log.error("【Redis】HLEN 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * Hash 递增
     *
     * @param key   键
     * @param item  字段名
     * @param delta 增量
     * @return 递增后的值
     */
    public long hIncr(String key, String item, long delta) {
        if (key == null || item == null) {
            throw new IllegalArgumentException("键和字段名不能为空");
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hIncr", () -> {
                        Long result = redisTemplate.opsForHash().increment(formattedKey, item, delta);
                        return result != null ? result : 0L;
                    })
                    : Optional.ofNullable(redisTemplate.opsForHash().increment(formattedKey, item, delta)).orElse(0L);
        } catch (Exception e) {
            recordError("hIncr", e);
            log.error("【Redis】HINCRBY 操作失败 | key={} | item={} | delta={} | error={}", key, item, delta, e);
            return 0;
        }
    }

    /**
     * Hash 递增（浮点数）
     *
     * @param key   键
     * @param item  字段名
     * @param delta 增量
     * @return 递增后的值
     */
    public double hIncrByFloat(String key, String item, double delta) {
        if (key == null || item == null) {
            throw new IllegalArgumentException("键和字段名不能为空");
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hIncrByFloat", () -> {
                        Double result = redisTemplate.opsForHash().increment(formattedKey, item, delta);
                        return result != null ? result : 0.0;
                    })
                    : Optional.ofNullable(redisTemplate.opsForHash().increment(formattedKey, item, delta)).orElse(0.0);
        } catch (Exception e) {
            recordError("hIncrByFloat", e);
            log.error("【Redis】HINCRBYFLOAT 操作失败 | key={} | item={} | delta={} | error={}", key, item, delta, e);
            return 0.0;
        }
    }

    /**
     * 获取 Hash 的所有字段名
     *
     * @param key 键
     * @return 字段名集合
     */
    public Set<Object> hKeys(String key) {
        if (key == null) {
            return Collections.emptySet();
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hKeys", () -> {
                        Set<Object> keys = redisTemplate.opsForHash().keys(formattedKey);
                        return keys != null ? keys : Collections.emptySet();
                    })
                    : Optional.ofNullable(redisTemplate.opsForHash().keys(formattedKey)).orElse(Collections.emptySet());
        } catch (Exception e) {
            recordError("hKeys", e);
            log.error("【Redis】HKEYS 操作失败 | key={} | error={}", key, e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取 Hash 的所有值
     *
     * @param key   键
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 值列表
     */
    public <T> List<T> hValues(String key, Class<T> clazz) {
        if (key == null) {
            return Collections.emptyList();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<List<T>> action = () -> {
                List<Object> values = redisTemplate.opsForHash().values(formattedKey);
                return values.stream().map(clazz::cast).collect(Collectors.toList());
            };
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hValues", action)
                    : action.get();
        } catch (Exception e) {
            recordError("hValues", e);
            log.error("【Redis】HVALUES 操作失败 | key={} | error={}", key, e);
            return Collections.emptyList();
        }
    }

    /**
     * 记录指标错误
     */
    private void recordError(String operationType, Throwable e) {
        if (metricsCollector != null) {
            metricsCollector.recordError(operationType, e);
        }
    }
}
