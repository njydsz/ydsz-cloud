package com.njydsz.common.redis.service.ops;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.common.util.collection.CollectionUtils;

/**
 * Redis 集合操作组件（Set + List + ZSet）
 *
 * <p>按数据类型拆分而来的细粒度操作组件，职责单一，便于维护与测试。
 * 集成 {@link RedisMetricsCollector} 进行操作指标采集，与 {@link RedisStringOps} 保持一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCollectionOps {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final RedisMetricsCollector metricsCollector;

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
     * 批量格式化 Keys
     */
    private String[] formatKeys(String... keys) {
        if (keys == null) {
            return new String[0];
        }
        return Arrays.stream(keys).map(this::formatKey).toArray(String[]::new);
    }

    // ============================ Set 操作 =============================

    /**
     * 获取 Set 的所有成员
     *
     * @param key   键
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合
     */
    public <T> Set<T> sMembers(String key, Class<T> clazz) {
        if (key == null) {
            return Collections.emptySet();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> members = redisTemplate.opsForSet().members(formattedKey);
                if (members == null) {
                    return Collections.emptySet();
                }
                return members.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sMembers", op) : op.get();
        } catch (Exception e) {
            recordError("sMembers", e);
            log.error("【Redis】SMEMBERS 操作失败 | key={} | error={}", key, e);
            return Collections.emptySet();
        }
    }

    /**
     * 判断元素是否是 Set 的成员
     *
     * @param key   键
     * @param value 元素
     * @return true-是成员
     */
    public boolean sIsMember(String key, Object value) {
        if (key == null || value == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("sIsMember", () -> Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(formattedKey, value)))
                    : Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(formattedKey, value));
        } catch (Exception e) {
            recordError("sIsMember", e);
            log.error("【Redis】SISMEMBER 操作失败 | key={} | value={} | error={}", key, value, e);
            return false;
        }
    }

    /**
     * 获取 Set 的成员数量
     *
     * @param key 键
     * @return 成员数量
     */
    public long sSize(String key) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long size = redisTemplate.opsForSet().size(formattedKey);
                return size != null ? size : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sSize", op) : op.get();
        } catch (Exception e) {
            recordError("sSize", e);
            log.error("【Redis】SCARD 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 添加 Set 成员
     *
     * @param key    键
     * @param values 成员数组
     * @return 添加的成员数量
     */
    public long sAdd(String key, Object... values) {
        if (key == null || values == null || values.length == 0) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForSet().add(formattedKey, values);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sAdd", op) : op.get();
        } catch (Exception e) {
            recordError("sAdd", e);
            log.error("【Redis】SADD 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 移除 Set 成员
     *
     * @param key    键
     * @param values 成员数组
     * @return 移除的成员数量
     */
    public long sRem(String key, Object... values) {
        if (key == null || values == null || values.length == 0) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForSet().remove(formattedKey, values);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sRem", op) : op.get();
        } catch (Exception e) {
            recordError("sRem", e);
            log.error("【Redis】SREM 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 随机获取一个 Set 成员
     *
     * @param key   键
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 随机成员
     */
    public <T> T sRandomMember(String key, Class<T> clazz) {
        if (key == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<T> op = () -> {
                Object value = redisTemplate.opsForSet().randomMember(formattedKey);
                return value != null ? clazz.cast(value) : null;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sRandomMember", op) : op.get();
        } catch (Exception e) {
            recordError("sRandomMember", e);
            log.error("【Redis】SRANDMEMBER 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 随机获取多个 Set 成员
     *
     * @param key   键
     * @param count 数量
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 随机成员列表
     */
    public <T> List<T> sRandomMembers(String key, long count, Class<T> clazz) {
        if (key == null || count <= 0) {
            return Collections.emptyList();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<List<T>> op = () -> {
                List<Object> members = redisTemplate.opsForSet().randomMembers(formattedKey, count);
                if (members == null) {
                    return Collections.emptyList();
                }
                return members.stream().map(clazz::cast).collect(Collectors.toList());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sRandomMembers", op) : op.get();
        } catch (Exception e) {
            recordError("sRandomMembers", e);
            log.error("【Redis】SRANDMEMBER 操作失败 | key={} | count={} | error={}", key, count, e);
            return Collections.emptyList();
        }
    }

    /**
     * 求多个 Set 的交集
     *
     * @param clazz 成员类型
     * @param keys  键数组
     * @param <T>   成员类型
     * @return 交集集合
     */
    public <T> Set<T> sInter(Class<T> clazz, String... keys) {
        if (keys == null || keys.length == 0) {
            return Collections.emptySet();
        }
        String[] formattedKeys = formatKeys(keys);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> result = redisTemplate.opsForSet().intersect(formattedKeys[0], Arrays.asList(formattedKeys).subList(1, formattedKeys.length));
                if (result == null) {
                    return Collections.emptySet();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sInter", op) : op.get();
        } catch (Exception e) {
            recordError("sInter", e);
            log.error("【Redis】SINTER 操作失败 | keys={} | error={}", Arrays.toString(keys), e);
            return Collections.emptySet();
        }
    }

    /**
     * 求多个 Set 的并集
     *
     * @param clazz 成员类型
     * @param keys  键数组
     * @param <T>   成员类型
     * @return 并集集合
     */
    public <T> Set<T> sUnion(Class<T> clazz, String... keys) {
        if (keys == null || keys.length == 0) {
            return Collections.emptySet();
        }
        String[] formattedKeys = formatKeys(keys);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> result = redisTemplate.opsForSet().union(formattedKeys[0], Arrays.asList(formattedKeys).subList(1, formattedKeys.length));
                if (result == null) {
                    return Collections.emptySet();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sUnion", op) : op.get();
        } catch (Exception e) {
            recordError("sUnion", e);
            log.error("【Redis】SUNION 操作失败 | keys={} | error={}", Arrays.toString(keys), e);
            return Collections.emptySet();
        }
    }

    /**
     * 求多个 Set 的差集
     *
     * @param clazz 成员类型
     * @param keys  键数组
     * @param <T>   成员类型
     * @return 差集集合
     */
    public <T> Set<T> sDiff(Class<T> clazz, String... keys) {
        if (keys == null || keys.length == 0) {
            return Collections.emptySet();
        }
        String[] formattedKeys = formatKeys(keys);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> result = redisTemplate.opsForSet().difference(formattedKeys[0], Arrays.asList(formattedKeys).subList(1, formattedKeys.length));
                if (result == null) {
                    return Collections.emptySet();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("sDiff", op) : op.get();
        } catch (Exception e) {
            recordError("sDiff", e);
            log.error("【Redis】SDIFF 操作失败 | keys={} | error={}", Arrays.toString(keys), e);
            return Collections.emptySet();
        }
    }

    // ============================ List 操作 =============================

    /**
     * 获取 List 的范围元素
     *
     * @param key   键
     * @param start 起始索引
     * @param end   结束索引（-1 表示到末尾）
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 元素列表
     */
    public <T> List<T> lRange(String key, long start, long end, Class<T> clazz) {
        if (key == null) {
            return Collections.emptyList();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<List<T>> op = () -> {
                List<Object> result = redisTemplate.opsForList().range(formattedKey, start, end);
                if (result == null) {
                    return Collections.emptyList();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toList());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("lRange", op) : op.get();
        } catch (Exception e) {
            recordError("lRange", e);
            log.error("【Redis】LRANGE 操作失败 | key={} | start={} | end={} | error={}", key, start, end, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取 List 长度
     *
     * @param key 键
     * @return 长度
     */
    public long lSize(String key) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long size = redisTemplate.opsForList().size(formattedKey);
                return size != null ? size : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("lSize", op) : op.get();
        } catch (Exception e) {
            recordError("lSize", e);
            log.error("【Redis】LLEN 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 获取 List 指定索引的元素
     *
     * @param key   键
     * @param index 索引
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 元素
     */
    public <T> T lIndex(String key, long index, Class<T> clazz) {
        if (key == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<T> op = () -> {
                Object value = redisTemplate.opsForList().index(formattedKey, index);
                return value != null ? clazz.cast(value) : null;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("lIndex", op) : op.get();
        } catch (Exception e) {
            recordError("lIndex", e);
            log.error("【Redis】LINDEX 操作失败 | key={} | index={} | error={}", key, index, e);
            return null;
        }
    }

    /**
     * 从左侧推入元素
     *
     * @param key   键
     * @param value 元素
     * @return 推送后的 List 长度
     */
    public long lPush(String key, Object value) {
        if (key == null || value == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long size = redisTemplate.opsForList().leftPush(formattedKey, value);
                return size != null ? size : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("lPush", op) : op.get();
        } catch (Exception e) {
            recordError("lPush", e);
            log.error("【Redis】LPUSH 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 从右侧推入元素
     *
     * @param key   键
     * @param value 元素
     * @return 推送后的 List 长度
     */
    public long rPush(String key, Object value) {
        if (key == null || value == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long size = redisTemplate.opsForList().rightPush(formattedKey, value);
                return size != null ? size : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("rPush", op) : op.get();
        } catch (Exception e) {
            recordError("rPush", e);
            log.error("【Redis】RPUSH 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 从左侧弹出元素
     *
     * @param key   键
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 弹出的元素
     */
    public <T> T lPop(String key, Class<T> clazz) {
        if (key == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<T> op = () -> {
                Object value = redisTemplate.opsForList().leftPop(formattedKey);
                return value != null ? clazz.cast(value) : null;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("lPop", op) : op.get();
        } catch (Exception e) {
            recordError("lPop", e);
            log.error("【Redis】LPOP 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 从右侧弹出元素
     *
     * @param key   键
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 弹出的元素
     */
    public <T> T rPop(String key, Class<T> clazz) {
        if (key == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<T> op = () -> {
                Object value = redisTemplate.opsForList().rightPop(formattedKey);
                return value != null ? clazz.cast(value) : null;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("rPop", op) : op.get();
        } catch (Exception e) {
            recordError("rPop", e);
            log.error("【Redis】RPOP 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 移除 List 中指定数量的元素
     *
     * @param key   键
     * @param count 数量（正数从左侧，负数从右侧）
     * @param value 元素值
     * @return 移除的数量
     */
    public long lRem(String key, long count, Object value) {
        if (key == null || value == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long removed = redisTemplate.opsForList().remove(formattedKey, count, value);
                return removed != null ? removed : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("lRem", op) : op.get();
        } catch (Exception e) {
            recordError("lRem", e);
            log.error("【Redis】LREM 操作失败 | key={} | count={} | error={}", key, count, e);
            return 0;
        }
    }

    /**
     * 设置 List 指定索引的元素
     *
     * @param key   键
     * @param index 索引
     * @param value 元素值
     * @return true-设置成功
     */
    public boolean lSet(String key, long index, Object value) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            if (metricsCollector != null) {
                metricsCollector.recordOperation("lSet", () -> redisTemplate.opsForList().set(formattedKey, index, value));
            } else {
                redisTemplate.opsForList().set(formattedKey, index, value);
            }
            return true;
        } catch (Exception e) {
            recordError("lSet", e);
            log.error("【Redis】LSET 操作失败 | key={} | index={} | error={}", key, index, e);
            return false;
        }
    }

    /**
     * 裁剪 List
     *
     * @param key   键
     * @param start 起始索引
     * @param end   结束索引
     * @return true-裁剪成功
     */
    public boolean lTrim(String key, long start, long end) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            if (metricsCollector != null) {
                metricsCollector.recordOperation("lTrim", () -> redisTemplate.opsForList().trim(formattedKey, start, end));
            } else {
                redisTemplate.opsForList().trim(formattedKey, start, end);
            }
            return true;
        } catch (Exception e) {
            recordError("lTrim", e);
            log.error("【Redis】LTRIM 操作失败 | key={} | start={} | end={} | error={}", key, start, end, e);
            return false;
        }
    }

    // ============================ ZSet 操作 =============================

    /**
     * 添加 ZSet 成员
     *
     * @param key   键
     * @param value 成员
     * @param score 分数
     * @return true-添加成功
     */
    public boolean zAdd(String key, Object value, double score) {
        if (key == null || value == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("zAdd", () -> Boolean.TRUE.equals(redisTemplate.opsForZSet().add(formattedKey, value, score)))
                    : Boolean.TRUE.equals(redisTemplate.opsForZSet().add(formattedKey, value, score));
        } catch (Exception e) {
            recordError("zAdd", e);
            log.error("【Redis】ZADD 操作失败 | key={} | score={} | error={}", key, score, e);
            return false;
        }
    }

    /**
     * 批量添加 ZSet 成员
     *
     * @param key    键
     * @param tuples 成员-分数对集合
     * @return 添加的数量
     */
    public long zAdd(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        if (key == null || CollectionUtils.isEmpty(tuples)) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForZSet().add(formattedKey, tuples);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zAdd", op) : op.get();
        } catch (Exception e) {
            recordError("zAdd", e);
            log.error("【Redis】ZADD 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 获取 ZSet 的成员数量
     *
     * @param key 键
     * @return 成员数量
     */
    public long zSize(String key) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long size = redisTemplate.opsForZSet().size(formattedKey);
                return size != null ? size : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zSize", op) : op.get();
        } catch (Exception e) {
            recordError("zSize", e);
            log.error("【Redis】ZCARD 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 获取 ZSet 指定分数范围的成员数量
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 成员数量
     */
    public long zCount(String key, double min, double max) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForZSet().count(formattedKey, min, max);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zCount", op) : op.get();
        } catch (Exception e) {
            recordError("zCount", e);
            log.error("【Redis】ZCOUNT 操作失败 | key={} | min={} | max={} | error={}", key, min, max, e);
            return 0;
        }
    }

    /**
     * 获取 ZSet 指定排名范围的成员（升序）
     *
     * @param key   键
     * @param start 起始索引
     * @param end   结束索引
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合
     */
    public <T> Set<T> zRange(String key, long start, long end, Class<T> clazz) {
        if (key == null) {
            return Collections.emptySet();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> result = redisTemplate.opsForZSet().range(formattedKey, start, end);
                if (result == null) {
                    return Collections.emptySet();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zRange", op) : op.get();
        } catch (Exception e) {
            recordError("zRange", e);
            log.error("【Redis】ZRANGE 操作失败 | key={} | start={} | end={} | error={}", key, start, end, e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取 ZSet 指定排名范围的成员（降序）
     *
     * @param key   键
     * @param start 起始索引
     * @param end   结束索引
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合
     */
    public <T> Set<T> zReverseRange(String key, long start, long end, Class<T> clazz) {
        if (key == null) {
            return Collections.emptySet();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> result = redisTemplate.opsForZSet().reverseRange(formattedKey, start, end);
                if (result == null) {
                    return Collections.emptySet();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zReverseRange", op) : op.get();
        } catch (Exception e) {
            recordError("zReverseRange", e);
            log.error("【Redis】ZREVRANGE 操作失败 | key={} | start={} | end={} | error={}", key, start, end, e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取 ZSet 指定分数范围的成员（升序）
     *
     * @param key   键
     * @param min   最小分数
     * @param max   最大分数
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合
     */
    public <T> Set<T> zRangeByScore(String key, double min, double max, Class<T> clazz) {
        if (key == null) {
            return Collections.emptySet();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Set<T>> op = () -> {
                Set<Object> result = redisTemplate.opsForZSet().rangeByScore(formattedKey, min, max);
                if (result == null) {
                    return Collections.emptySet();
                }
                return result.stream().map(clazz::cast).collect(Collectors.toSet());
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zRangeByScore", op) : op.get();
        } catch (Exception e) {
            recordError("zRangeByScore", e);
            log.error("【Redis】ZRANGEBYSCORE 操作失败 | key={} | min={} | max={} | error={}", key, min, max, e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取 ZSet 指定分数范围的成员（带分数）
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 成员-分数对集合
     */
    public Set<ZSetOperations.TypedTuple<Object>> zRangeByScoreWithScores(String key, double min, double max) {
        if (key == null) {
            return Collections.emptySet();
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Set<ZSetOperations.TypedTuple<Object>>> op = () -> {
                Set<ZSetOperations.TypedTuple<Object>> result = redisTemplate.opsForZSet().rangeByScoreWithScores(formattedKey, min, max);
                return result != null ? result : Collections.emptySet();
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zRangeByScoreWithScores", op) : op.get();
        } catch (Exception e) {
            recordError("zRangeByScoreWithScores", e);
            log.error("【Redis】ZRANGEBYSCORE WITHSCORES 操作失败 | key={} | min={} | max={} | error={}", key, min, max, e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取成员的排名（升序）
     *
     * @param key   键
     * @param value 成员
     * @return 排名（0 开始），不存在返回 null
     */
    public Long zRank(String key, Object value) {
        if (key == null || value == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("zRank", () -> redisTemplate.opsForZSet().rank(formattedKey, value))
                    : redisTemplate.opsForZSet().rank(formattedKey, value);
        } catch (Exception e) {
            recordError("zRank", e);
            log.error("【Redis】ZRANK 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 获取成员的排名（降序）
     *
     * @param key   键
     * @param value 成员
     * @return 排名（0 开始），不存在返回 null
     */
    public Long zReverseRank(String key, Object value) {
        if (key == null || value == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("zReverseRank", () -> redisTemplate.opsForZSet().reverseRank(formattedKey, value))
                    : redisTemplate.opsForZSet().reverseRank(formattedKey, value);
        } catch (Exception e) {
            recordError("zReverseRank", e);
            log.error("【Redis】ZREVRANK 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 获取成员的分数
     *
     * @param key   键
     * @param value 成员
     * @return 分数，不存在返回 null
     */
    public Double zScore(String key, Object value) {
        if (key == null || value == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("zScore", () -> redisTemplate.opsForZSet().score(formattedKey, value))
                    : redisTemplate.opsForZSet().score(formattedKey, value);
        } catch (Exception e) {
            recordError("zScore", e);
            log.error("【Redis】ZSCORE 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 移除 ZSet 成员
     *
     * @param key    键
     * @param values 成员数组
     * @return 移除的数量
     */
    public long zRem(String key, Object... values) {
        if (key == null || values == null || values.length == 0) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForZSet().remove(formattedKey, values);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zRem", op) : op.get();
        } catch (Exception e) {
            recordError("zRem", e);
            log.error("【Redis】ZREM 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    /**
     * 移除指定排名范围的成员
     *
     * @param key   键
     * @param start 起始排名
     * @param end   结束排名
     * @return 移除的数量
     */
    public long zRemoveRange(String key, long start, long end) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForZSet().removeRange(formattedKey, start, end);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zRemoveRange", op) : op.get();
        } catch (Exception e) {
            recordError("zRemoveRange", e);
            log.error("【Redis】ZREMRANGEBYRANK 操作失败 | key={} | start={} | end={} | error={}", key, start, end, e);
            return 0;
        }
    }

    /**
     * 移除指定分数范围的成员
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 移除的数量
     */
    public long zRemoveRangeByScore(String key, double min, double max) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            Supplier<Long> op = () -> {
                Long count = redisTemplate.opsForZSet().removeRangeByScore(formattedKey, min, max);
                return count != null ? count : 0;
            };
            return metricsCollector != null ? metricsCollector.recordOperation("zRemoveRangeByScore", op) : op.get();
        } catch (Exception e) {
            recordError("zRemoveRangeByScore", e);
            log.error("【Redis】ZREMRANGEBYSCORE 操作失败 | key={} | min={} | max={} | error={}", key, min, max, e);
            return 0;
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
