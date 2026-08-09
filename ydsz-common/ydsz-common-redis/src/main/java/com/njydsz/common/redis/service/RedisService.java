package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.enums.RedisKeysEnum;
import com.njydsz.common.redis.service.ops.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 服务门面类
 *
 * <p>作为统一入口聚合所有 Redis 操作，内部委托给按数据类型拆分的子组件：
 * <ul>
 *   <li>{@link RedisStringOps} - 通用操作 + String + Bitmap</li>
 *   <li>{@link RedisHashOps} - Hash 操作</li>
 *   <li>{@link RedisCollectionOps} - Set + List + ZSet 操作</li>
 *   <li>{@link RedisGeoOps} - Geo + HyperLogLog 操作</li>
 *   <li>{@link RedisAdvancedOps} - Pipeline + Lua 脚本操作</li>
 * </ul>
 *
 * <p><b>向后兼容：</b>所有 public 方法签名保持不变，现有调用方无需修改。
 * 新代码建议直接注入子组件以获得更清晰的依赖关系。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 通过门面类（向后兼容）
 * redisService.set("key", "value");
 * String value = redisService.get("key", String.class);
 *
 * // 直接注入子组件（推荐新代码使用）
 * RedisStringOps stringOps;  // 注入
 * stringOps.set("key", "value");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisService implements BatchRedisOperations {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;
    private final RedisStringOps stringOps;
    private final RedisHashOps hashOps;
    private final RedisCollectionOps collectionOps;
    private final RedisGeoOps geoOps;
    private final RedisAdvancedOps advancedOps;
    private final RedisPubSubOps pubSubOps;
    private final RedisStreamOps streamOps;
    private final RedisTransactionOps transactionOps;

    public RedisService(RedisTemplate<String, Object> redisTemplate,
                        RedisProperties redisProperties,
                        RedisStringOps stringOps,
                        RedisHashOps hashOps,
                        RedisCollectionOps collectionOps,
                        RedisGeoOps geoOps,
                        RedisAdvancedOps advancedOps,
                        RedisPubSubOps pubSubOps,
                        RedisStreamOps streamOps,
                        RedisTransactionOps transactionOps) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = redisProperties != null ? (redisProperties.getKeyPrefix() != null ? redisProperties.getKeyPrefix() : "") : "";
        this.stringOps = stringOps;
        this.hashOps = hashOps;
        this.collectionOps = collectionOps;
        this.geoOps = geoOps;
        this.advancedOps = advancedOps;
        this.pubSubOps = pubSubOps;
        this.streamOps = streamOps;
        this.transactionOps = transactionOps;
    }

    /**
     * 获取 Spring Data Redis 的 ValueOperations（向后兼容）。
     *
     * <p>用于需要调用 {@code setIfAbsent}/{@code decrement} 等高级 String 操作的场景。
     * 注意：返回的操作对象不经过 RedisService 的 keyPrefix 二次拼接。
     */
    public ValueOperations<String, Object> opsForValue() {
        return redisTemplate.opsForValue();
    }

    /**
     * 获取 Spring Data Redis 的 HashOperations（向后兼容）。
     *
     * <p>用于需要直接调用 {@code putAll}/{@code entries} 等 Hash 操作的场景。
     * 注意：返回的操作对象不经过 RedisService 的 keyPrefix 二次拼接。
     */
    public HashOperations<String, Object, Object> opsForHash() {
        return redisTemplate.opsForHash();
    }

    /**
     * 格式化 Key，添加统一前缀
     */
    private String formatKey(String key) {
        if (key == null || keyPrefix.isEmpty()) {
            return key;
        }
        return keyPrefix + ":" + key;
    }

    /**
     * 批量格式化 Keys
     */
    private List<String> formatKeys(List<String> keys) {
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream().map(this::formatKey).collect(Collectors.toList());
    }

    /**
     * 批量格式化 Keys（Collection 版本）
     */
    private List<String> formatKeys(Collection<String> keys) {
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream().map(this::formatKey).collect(Collectors.toList());
    }

    /**
     * 格式化 Map Keys
     */
    private Map<String, Object> formatKeyMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(formatKey(entry.getKey()), entry.getValue());
        }
        return result;
    }

    // ============================ 通用操作（委托 RedisStringOps）=============================

    /**
     * 设置键的过期时间（秒）。
     *
     * <p>key 会自动拼接全局前缀后下发；键不存在或 Redis 异常时返回 false，不抛异常。
     *
     * @param key  缓存键（调用方无需含前缀）
     * @param time 过期秒数，<=0 时直接返回 false
     * @return true-设置成功
     */
    public boolean expire(String key, long time) {
        return stringOps.expire(key, time);
    }

    /**
     * 设置键的过期时间（Duration）。
     *
     * @param key      缓存键（调用方无需含前缀）
     * @param duration 过期时长，为 null/零/负时直接返回 false
     * @return true-设置成功
     */
    public boolean expire(String key, Duration duration) {
        return stringOps.expire(key, duration);
    }

    /**
     * 查询键剩余过期时间（秒）。
     *
     * @param key 缓存键（调用方无需含前缀）
     * @return 剩余秒数，-1 表示永久有效，-2 表示键不存在或查询失败
     */
    public long getExpire(String key) {
        return stringOps.getExpire(key);
    }

    /**
     * 判断键是否存在（自动拼接前缀）。
     *
     * @param key 缓存键（调用方无需含前缀）
     * @return true-存在；key 为 null 或 Redis 异常时降级返回 false
     */
    public boolean hasKey(String key) {
        return stringOps.hasKey(key);
    }

    /**
     * 删除一个或多个键（自动拼接前缀）。
     *
     * <p>空参数直接返回；Redis 异常时仅记录日志，不抛异常。
     *
     * @param keys 待删除的键（调用方无需含前缀）
     */
    public void del(String... keys) {
        stringOps.del(keys);
    }

    /**
     * 删除集合中的键（自动拼接前缀）。
     *
     * <p>集合为空直接返回；Redis 异常时仅记录日志，不抛异常。
     *
     * @param keys 待删除的键集合（调用方无需含前缀）
     */
    public void del(Collection<String> keys) {
        stringOps.del(keys);
    }

    /**
     * 按模式批量删除键（基于 SCAN，避免 KEYS 阻塞服务端）。
     *
     * @param pattern 匹配模式，如 {@code user:*}（调用方无需含前缀，内部自动拼接）
     * @return 实际删除的键数量；pattern 为空或异常时返回 0
     */
    public long delByPattern(String pattern) {
        return stringOps.delByPattern(pattern);
    }

    /**
     * SCAN 遍历匹配键，默认上限 10000 防止大数据量 OOM（基于 SCAN，非阻塞）。
     *
     * @param pattern 匹配模式（调用方无需含前缀）
     * @return 匹配的键集合（已去除前缀），不存在或异常时返回空集
     */
    public Set<String> scan(String pattern) {
        return stringOps.scan(pattern);
    }

    /**
     * SCAN 遍历匹配键，自定义返回上限防止 OOM。
     *
     * @param pattern 匹配模式（调用方无需含前缀）
     * @param maxKeys 最大返回键数量
     * @return 匹配的键集合（已去除前缀），不存在或异常时返回空集
     */
    public Set<String> scan(String pattern, int maxKeys) {
        return stringOps.scan(pattern, maxKeys);
    }

    /**
     * 重命名键（自动拼接前缀）。
     *
     * @param oldKey 原键（调用方无需含前缀）
     * @param newKey 新键（调用方无需含前缀）
     * @return true-重命名成功；参数为 null 或异常时返回 false
     */
    public boolean rename(String oldKey, String newKey) {
        return stringOps.rename(oldKey, newKey);
    }

    /**
     * 仅当新键不存在时重命名（原子操作）。
     *
     * @param oldKey 原键（调用方无需含前缀）
     * @param newKey 新键（调用方无需含前缀）
     * @return true-重命名成功；原键不存在或新键已存在时返回 false
     */
    public boolean renameIfAbsent(String oldKey, String newKey) {
        return stringOps.renameIfAbsent(oldKey, newKey);
    }

    // ============================ String 操作（委托 RedisStringOps）=============================

    /**
     * 获取值
     *
     * @param key 键
     * @return 值，不存在时返回 null。如需类型安全转换，请使用 {@link #get(String, Class)}
     */
    public Object get(String key) {
        return stringOps.get(key);
    }

    /**
     * 获取并做类型安全转换。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 目标类型
     * @param <T>   值类型
     * @return 转换后的值；键不存在、类型不符或异常时返回 null
     */
    public <T> T get(String key, Class<T> clazz) {
        return stringOps.get(key, clazz);
    }

    /**
     * 写入值（不带过期时间）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 值，为 null 时不会写入
     * @return true-写入成功；key 为 null 或异常时返回 false
     */
    public boolean set(String key, Object value) {
        return stringOps.set(key, value);
    }

    /**
     * 写入值并设置过期时间（自动叠加随机偏移防缓存雪崩）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 值
     * @param time  过期秒数
     * @return true-写入成功；key 为 null 或异常时返回 false
     */
    public boolean set(String key, Object value, long time) {
        return stringOps.set(key, value, time);
    }

    /**
     * 写入值并设置过期时间（Duration，自动叠加随机偏移防缓存雪崩）。
     *
     * @param key      缓存键（无需含前缀）
     * @param value    值
     * @param duration 过期时长
     * @return true-写入成功；key 为 null 或异常时返回 false
     */
    public boolean set(String key, Object value, Duration duration) {
        return stringOps.set(key, value, duration);
    }

    /**
     * 仅当键不存在时写入（SETNX 语义，带过期）。
     *
     * <p>常用于分布式锁或幂等写入；过期时间自动叠加随机偏移。
     *
     * @param key    缓存键（无需含前缀）
     * @param value  值
     * @param expire 过期秒数
     * @return true-写入成功（键原本不存在）
     */
    public boolean setIfAbsent(String key, Object value, long expire) {
        return stringOps.setIfAbsent(key, value, expire);
    }

    /**
     * 仅当键存在时写入（SETXX 语义，带过期）。
     *
     * @param key    缓存键（无需含前缀）
     * @param value  值
     * @param expire 过期秒数
     * @return true-写入成功（键原本存在）
     */
    public boolean setIfPresent(String key, Object value, long expire) {
        return stringOps.setIfPresent(key, value, expire);
    }

    /**
     * 缓存穿透保护：命中则直接返回，未命中加分布式锁回源并回填。
     *
     * <p>通过 Redis 分布式锁（UUID 校验持有者，Lua 原子释放）保证并发仅一个线程回源；
     * 回源结果为 null 时写入短期空值占位防穿透。未抢到锁的线程自旋等待（上限 3s）后降级本地回源。
     *
     * @param key      缓存键（无需含前缀）
     * @param expire   回填过期秒数
     * @param supplier 数据提供函数（回源），可能为 null
     * @param clazz    值类型
     * @param <T>      值类型
     * @return 缓存值；key 为 null 时返回 null
     */
    public <T> T getOrCompute(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
        return stringOps.getOrCompute(key, expire, supplier, clazz);
    }

    /**
     * 缓存穿透保护（枚举键版本），等价于 {@link #getOrCompute(String, long, Supplier, Class)}。
     *
     * @param keyEnum  键枚举
     * @param arg      枚举键参数
     * @param expire   回填过期秒数
     * @param supplier 数据提供函数
     * @param clazz    值类型
     * @param <T>      值类型
     * @return 缓存值
     */
    public <T> T getOrCompute(RedisKeysEnum keyEnum, Object arg, long expire, Supplier<T> supplier, Class<T> clazz) {
        return stringOps.getOrCompute(keyEnum, arg, expire, supplier, clazz);
    }

    /**
     * 原子递增（INCRBY）。
     *
     * @param key   缓存键（无需含前缀）
     * @param delta 增量，必须 > 0，否则抛 {@link IllegalArgumentException}
     * @return 递增后的值；异常时返回 0
     */
    public long incr(String key, long delta) {
        return stringOps.incr(key, delta);
    }

    /**
     * 原子递减（等价于 INCRBY 负数）。
     *
     * @param key   缓存键（无需含前缀）
     * @param delta 减量，必须 > 0，否则抛 {@link IllegalArgumentException}
     * @return 递减后的值；异常时返回 0
     */
    public long decr(String key, long delta) {
        return stringOps.decr(key, delta);
    }

    /**
     * 原子浮点递增（INCRBYFLOAT）。
     *
     * @param key   缓存键（无需含前缀）
     * @param delta 浮点增量
     * @return 递增后的值；key 为 null 时抛 {@link IllegalArgumentException}，异常时返回 0.0
     */
    public double incrByFloat(String key, double delta) {
        return stringOps.incrByFloat(key, delta);
    }

    /**
     * 批量获取值（底层 MGET，返回 String 列表）。
     *
     * @param keys 键集合（无需含前缀）
     * @return 值列表，与 keys 顺序一致，缺失位置为 null；参数为空或异常时返回空列表
     */
    public List<String> mget(List<String> keys) {
        return stringOps.mget(keys);
    }

    /**
     * 批量获取值并统一类型转换（MGET）。
     *
     * @param keys  键集合（无需含前缀）
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 值列表，与 keys 顺序一致，缺失位置为 null；参数为空或异常时返回空列表
     */
    public <T> List<T> mgetObjects(List<String> keys, Class<T> clazz) {
        return stringOps.mgetObjects(keys, clazz);
    }

    /**
     * 批量获取值并以 Map 返回（MGET + 本地组装为 key->value）。
     *
     * <p>与 {@link #mget} 不同，返回结构为 Map 便于按原 key 取用；空参数返回空 Map。
     *
     * @param keys 键集合（无需含前缀）
     * @return 键到值的映射，缺失键不出现在 Map 中
     */
    public Map<String, Object> mgetPartitioned(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> formattedKeys = formatKeys(keys);
        List<Object> values = redisTemplate.opsForValue().multiGet(formattedKeys);
        Map<String, Object> result = new LinkedHashMap<>();
        if (values != null) {
            for (int i = 0; i < keys.size(); i++) {
                result.put(keys.get(i), values.get(i));
            }
        }
        return result;
    }

    /**
     * 批量写入键值对（MSET，自动拼接前缀）。
     *
     * <p>单条命令完成批量写入，非 Pipeline、非事务；空 Map 直接返回。
     *
     * @param keyValueMap 键值对（key 无需含前缀）
     */
    public void msetPartitioned(Map<String, Object> keyValueMap) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().multiSet(formatKeyMap(keyValueMap));
    }

    // ============================ Hash 操作（委托 RedisHashOps）=============================

    /**
     * 获取 Hash 指定字段值并做类型转换。
     *
     * @param key   缓存键（无需含前缀）
     * @param item  字段名
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 字段值；字段或键不存在、转换失败时返回 null
     */
    public <T> T hGet(String key, String item, Class<T> clazz) {
        return hashOps.hGet(key, item, clazz);
    }

    /**
     * 获取 Hash 全部字段（Map 形式，值类型转换）。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 字段->值映射；键不存在时返回空 Map
     */
    public <T> Map<String, T> hGetAll(String key, Class<T> clazz) {
        return hashOps.hGetAll(key, clazz);
    }

    /**
     * 批量获取 Hash 多个字段值。
     *
     * @param key   缓存键（无需含前缀）
     * @param items 字段名集合
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 字段值列表，与 items 顺序一致，缺失字段位置为 null
     */
    public <T> List<T> hMGet(String key, Collection<Object> items, Class<T> clazz) {
        return hashOps.hMGet(key, items, clazz);
    }

    /**
     * 写入 Hash 单个字段。
     *
     * @param key   缓存键（无需含前缀）
     * @param item  字段名
     * @param value 值
     * @return true-写入成功；参数为空或异常时返回 false
     */
    public boolean hSet(String key, String item, Object value) {
        return hashOps.hSet(key, item, value);
    }

    /**
     * 仅当字段不存在时写入（HSETNX 语义）。
     *
     * @param key   缓存键（无需含前缀）
     * @param item  字段名
     * @param value 值
     * @return true-写入成功（字段原本不存在）
     */
    public boolean hSetIfAbsent(String key, String item, Object value) {
        return hashOps.hSetIfAbsent(key, item, value);
    }

    /**
     * 批量写入 Hash 多个字段（HMSET）。
     *
     * @param key 缓存键（无需含前缀）
     * @param map 字段->值映射
     * @return true-写入成功；参数为空或异常时返回 false
     */
    public boolean hMSet(String key, Map<String, ?> map) {
        return hashOps.hMSet(key, map);
    }

    /**
     * 删除 Hash 一个或多个字段。
     *
     * @param key   缓存键（无需含前缀）
     * @param items 待删字段
     * @return 实际删除的字段数
     */
    public long hDel(String key, Object... items) {
        return hashOps.hDel(key, items);
    }

    /**
     * 判断 Hash 是否包含指定字段（HEXISTS）。
     *
     * @param key  缓存键（无需含前缀）
     * @param item 字段名
     * @return true-包含；异常时返回 false
     */
    public boolean hHasKey(String key, String item) {
        return hashOps.hHasKey(key, item);
    }

    /**
     * 获取 Hash 字段数量（HLEN）。
     *
     * @param key 缓存键（无需含前缀）
     * @return 字段数；异常时返回 0
     */
    public long hSize(String key) {
        return hashOps.hSize(key);
    }

    /**
     * Hash 字段原子递增（HINCRBY）。
     *
     * @param key   缓存键（无需含前缀）
     * @param item  字段名
     * @param delta 增量，必须 > 0，否则抛 {@link IllegalArgumentException}
     * @return 递增后的值；异常时返回 0
     */
    public long hIncr(String key, String item, long delta) {
        return hashOps.hIncr(key, item, delta);
    }

    /**
     * Hash 字段原子浮点递增（HINCRBYFLOAT）。
     *
     * @param key   缓存键（无需含前缀）
     * @param item  字段名
     * @param delta 浮点增量
     * @return 递增后的值；异常时返回 0.0
     */
    public double hIncrByFloat(String key, String item, double delta) {
        return hashOps.hIncrByFloat(key, item, delta);
    }

    /**
     * 获取 Hash 全部字段名（HKEYS）。
     *
     * @param key 缓存键（无需含前缀）
     * @return 字段名集合；键不存在时返回空集
     */
    public Set<Object> hKeys(String key) {
        return hashOps.hKeys(key);
    }

    /**
     * 获取 Hash 全部字段值并类型转换（HVALS）。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 值列表；键不存在时返回空列表
     */
    public <T> List<T> hValues(String key, Class<T> clazz) {
        return hashOps.hValues(key, clazz);
    }

    // ============================ Set 操作（委托 RedisCollectionOps）=============================

    /**
     * 获取 Set 全部成员并类型转换（SMEMBERS）。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合；键不存在时返回空集
     */
    public <T> Set<T> sMembers(String key, Class<T> clazz) {
        return collectionOps.sMembers(key, clazz);
    }

    /**
     * 判断成员是否在 Set 中（SISMEMBER）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 成员
     * @return true-存在；异常时返回 false
     */
    public boolean sIsMember(String key, Object value) {
        return collectionOps.sIsMember(key, value);
    }

    /**
     * 获取 Set 成员数量（SCARD）。
     *
     * @param key 缓存键（无需含前缀）
     * @return 成员数；异常时返回 0
     */
    public long sSize(String key) {
        return collectionOps.sSize(key);
    }

    /**
     * 向 Set 添加一个或多个成员（SADD）。
     *
     * @param key    缓存键（无需含前缀）
     * @param values 成员
     * @return 实际新增的成员数（已存在的不会重复计数）
     */
    public long sAdd(String key, Object... values) {
        return collectionOps.sAdd(key, values);
    }

    /**
     * 从 Set 移除一个或多个成员（SREM）。
     *
     * @param key    缓存键（无需含前缀）
     * @param values 成员
     * @return 实际移除的成员数
     */
    public long sRem(String key, Object... values) {
        return collectionOps.sRem(key, values);
    }

    /**
     * 随机返回一个 Set 成员（SRANDMEMBER）。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 随机成员；Set 为空或异常时返回 null
     */
    public <T> T sRandomMember(String key, Class<T> clazz) {
        return collectionOps.sRandomMember(key, clazz);
    }

    /**
     * 随机返回多个 Set 成员（可重复，SRANDMEMBER count）。
     *
     * @param key   缓存键（无需含前缀）
     * @param count 返回数量
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员列表；Set 为空或异常时返回空列表
     */
    public <T> List<T> sRandomMembers(String key, long count, Class<T> clazz) {
        return collectionOps.sRandomMembers(key, count, clazz);
    }

    /**
     * 多个 Set 交集（SINTER）。
     *
     * @param clazz 成员类型
     * @param keys  缓存键（无需含前缀），至少一个
     * @param <T>   成员类型
     * @return 交集成员集合；任一键不存在时返回空集
     */
    public <T> Set<T> sInter(Class<T> clazz, String... keys) {
        return collectionOps.sInter(clazz, keys);
    }

    /**
     * 多个 Set 并集（SUNION）。
     *
     * @param clazz 成员类型
     * @param keys  缓存键（无需含前缀）
     * @param <T>   成员类型
     * @return 并集成员集合
     */
    public <T> Set<T> sUnion(Class<T> clazz, String... keys) {
        return collectionOps.sUnion(clazz, keys);
    }

    /**
     * 多个 Set 差集（SDIFF，第一个键减去其余键）。
     *
     * @param clazz 成员类型
     * @param keys  缓存键（无需含前缀），第一个为被减集
     * @param <T>   成员类型
     * @return 差集成员集合
     */
    public <T> Set<T> sDiff(Class<T> clazz, String... keys) {
        return collectionOps.sDiff(clazz, keys);
    }

    // ============================ List 操作（委托 RedisCollectionOps）=============================

    /**
     * 获取 List 区间元素（LRANGE，下标可为负表示从尾部计数）。
     *
     * @param key   缓存键（无需含前缀）
     * @param start 起始下标
     * @param end   结束下标
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 元素列表；键不存在时返回空列表
     */
    public <T> List<T> lRange(String key, long start, long end, Class<T> clazz) {
        return collectionOps.lRange(key, start, end, clazz);
    }

    /**
     * 获取 List 长度（LLEN）。
     *
     * @param key 缓存键（无需含前缀）
     * @return 长度；异常时返回 0
     */
    public long lSize(String key) {
        return collectionOps.lSize(key);
    }

    /**
     * 获取 List 指定下标元素（LINDEX）。
     *
     * @param key   缓存键（无需含前缀）
     * @param index 下标
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 元素；下标越界或异常时返回 null
     */
    public <T> T lIndex(String key, long index, Class<T> clazz) {
        return collectionOps.lIndex(key, index, clazz);
    }

    /**
     * 从 List 头部插入元素（LPUSH）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 元素
     * @return 插入后 List 长度
     */
    public long lPush(String key, Object value) {
        return collectionOps.lPush(key, value);
    }

    /**
     * 从 List 尾部插入元素（RPUSH）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 元素
     * @return 插入后 List 长度
     */
    public long rPush(String key, Object value) {
        return collectionOps.rPush(key, value);
    }

    /**
     * 从 List 头部弹出元素（LPOP）。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 弹出的元素；List 为空或异常时返回 null
     */
    public <T> T lPop(String key, Class<T> clazz) {
        return collectionOps.lPop(key, clazz);
    }

    /**
     * 从 List 尾部弹出元素（RPOP）。
     *
     * @param key   缓存键（无需含前缀）
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 弹出的元素；List 为空或异常时返回 null
     */
    public <T> T rPop(String key, Class<T> clazz) {
        return collectionOps.rPop(key, clazz);
    }

    /**
     * 移除 List 中与值相等的元素（LREM）。
     *
     * @param key   缓存键（无需含前缀）
     * @param count 移除数量：>0 从头部、<0 从尾部、=0 全部匹配项
     * @param value 值
     * @return 实际移除的元素数
     */
    public long lRem(String key, long count, Object value) {
        return collectionOps.lRem(key, count, value);
    }

    /**
     * 设置 List 指定下标元素（LSET，下标须已存在）。
     *
     * @param key   缓存键（无需含前缀）
     * @param index 下标
     * @param value 元素
     * @return true-设置成功；越界或异常时返回 false
     */
    public boolean lSet(String key, long index, Object value) {
        return collectionOps.lSet(key, index, value);
    }

    /**
     * 修剪 List 仅保留区间元素（LTRIM，可用于实现定长队列）。
     *
     * @param key   缓存键（无需含前缀）
     * @param start 起始下标
     * @param end   结束下标
     * @return true-成功；异常时返回 false
     */
    public boolean lTrim(String key, long start, long end) {
        return collectionOps.lTrim(key, start, end);
    }

    // ============================ ZSet 操作（委托 RedisCollectionOps）=============================

    /**
     * 向 ZSet 添加成员（ZADD）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 成员
     * @param score 分数（排序依据）
     * @return true-添加成功；异常时返回 false
     */
    public boolean zAdd(String key, Object value, double score) {
        return collectionOps.zAdd(key, value, score);
    }

    /**
     * 向 ZSet 批量添加成员（ZADD 多元组）。
     *
     * @param key   缓存键（无需含前缀）
     * @param tuples 成员-分数元组集合
     * @return 实际新增的成员数
     */
    public long zAdd(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        return collectionOps.zAdd(key, tuples);
    }

    /**
     * 获取 ZSet 成员数量（ZCARD）。
     *
     * @param key 缓存键（无需含前缀）
     * @return 成员数；异常时返回 0
     */
    public long zSize(String key) {
        return collectionOps.zSize(key);
    }

    /**
     * 统计分数区间内的成员数（ZCOUNT，闭区间）。
     *
     * @param key 缓存键（无需含前缀）
     * @param min 最小分数（含）
     * @param max 最大分数（含）
     * @return 成员数
     */
    public long zCount(String key, double min, double max) {
        return collectionOps.zCount(key, min, max);
    }

    /**
     * 按排名升序获取区间成员（ZRANGE，下标可为负）。
     *
     * @param key   缓存键（无需含前缀）
     * @param start 起始排名
     * @param end   结束排名
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合（按分数升序）；键不存在时返回空集
     */
    public <T> Set<T> zRange(String key, long start, long end, Class<T> clazz) {
        return collectionOps.zRange(key, start, end, clazz);
    }

    /**
     * 按排名降序获取区间成员（ZREVRANGE）。
     *
     * @param key   缓存键（无需含前缀）
     * @param start 起始排名
     * @param end   结束排名
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合（按分数降序）
     */
    public <T> Set<T> zReverseRange(String key, long start, long end, Class<T> clazz) {
        return collectionOps.zReverseRange(key, start, end, clazz);
    }

    /**
     * 按分数升序获取区间成员（ZRANGEBYSCORE，闭区间）。
     *
     * @param key   缓存键（无需含前缀）
     * @param min   最小分数（含）
     * @param max   最大分数（含）
     * @param clazz 成员类型
     * @param <T>   成员类型
     * @return 成员集合（按分数升序）
     */
    public <T> Set<T> zRangeByScore(String key, double min, double max, Class<T> clazz) {
        return collectionOps.zRangeByScore(key, min, max, clazz);
    }

    /**
     * 按分数升序获取区间成员及其分数（ZRANGEBYSCORE WITHSCORES）。
     *
     * @param key 缓存键（无需含前缀）
     * @param min 最小分数（含）
     * @param max 最大分数（含）
     * @return 成员-分数元组集合；键不存在时返回空集
     */
    public Set<ZSetOperations.TypedTuple<Object>> zRangeByScoreWithScores(String key, double min, double max) {
        return collectionOps.zRangeByScoreWithScores(key, min, max);
    }

    /**
     * 获取成员升序排名（ZRANK，从 0 开始，分数最低者排第 0）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 成员
     * @return 排名；成员不存在时返回 null
     */
    public Long zRank(String key, Object value) {
        return collectionOps.zRank(key, value);
    }

    /**
     * 获取成员降序排名（ZREVRANK，从 0 开始，分数最高者排第 0）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 成员
     * @return 排名；成员不存在时返回 null
     */
    public Long zReverseRank(String key, Object value) {
        return collectionOps.zReverseRank(key, value);
    }

    /**
     * 获取成员分数（ZSCORE）。
     *
     * @param key   缓存键（无需含前缀）
     * @param value 成员
     * @return 分数；成员不存在时返回 null
     */
    public Double zScore(String key, Object value) {
        return collectionOps.zScore(key, value);
    }

    /**
     * 从 ZSet 移除一个或多个成员（ZREM）。
     *
     * @param key    缓存键（无需含前缀）
     * @param values 成员
     * @return 实际移除的成员数
     */
    public long zRem(String key, Object... values) {
        return collectionOps.zRem(key, values);
    }

    /**
     * 按排名区间移除成员（ZREMRANGEBYRANK）。
     *
     * @param key   缓存键（无需含前缀）
     * @param start 起始排名
     * @param end   结束排名
     * @return 实际移除的成员数
     */
    public long zRemoveRange(String key, long start, long end) {
        return collectionOps.zRemoveRange(key, start, end);
    }

    /**
     * 按分数区间移除成员（ZREMRANGEBYSCORE，闭区间）。
     *
     * @param key 缓存键（无需含前缀）
     * @param min 最小分数（含）
     * @param max 最大分数（含）
     * @return 实际移除的成员数
     */
    public long zRemoveRangeByScore(String key, double min, double max) {
        return collectionOps.zRemoveRangeByScore(key, min, max);
    }

    // ============================ Bitmap 操作（委托 RedisStringOps）=============================

    /**
     * 设置位图指定位（SETBIT）。
     *
     * @param key    缓存键（无需含前缀）
     * @param offset 位偏移量
     * @param value  true=1，false=0
     * @return true-成功；异常时返回 false
     */
    public boolean setBit(String key, long offset, boolean value) {
        return stringOps.setBit(key, offset, value);
    }

    /**
     * 获取位图指定位（GETBIT）。
     *
     * @param key    缓存键（无需含前缀）
     * @param offset 位偏移量
     * @return 位值；异常时返回 false
     */
    public boolean getBit(String key, long offset) {
        return stringOps.getBit(key, offset);
    }

    /**
     * 统计位图中值为 1 的位数（BITCOUNT）。
     *
     * @param key 缓存键（无需含前缀）
     * @return 1 的位数；异常时返回 0
     */
    public long bitCount(String key) {
        return stringOps.bitCount(key);
    }

    // ============================ Geo 操作（委托 RedisGeoOps）=============================

    /**
     * 添加地理坐标成员（GEOADD）。
     *
     * @param key       缓存键（无需含前缀）
     * @param member    成员
     * @param longitude 经度
     * @param latitude  纬度
     * @return true-添加成功；异常时返回 false
     */
    public boolean geoAdd(String key, Object member, double longitude, double latitude) {
        return geoOps.geoAdd(key, member, longitude, latitude);
    }

    /**
     * 计算两个成员间的距离（GEODIST）。
     *
     * @param key     缓存键（无需含前缀）
     * @param member1 成员1
     * @param member2 成员2
     * @param unit    距离单位（M/KM/MI/FT）
     * @return 距离；任一成员不存在或异常时返回 null
     */
    public Distance geoDistance(String key, Object member1, Object member2, Metrics unit) {
        return geoOps.geoDistance(key, member1, member2, unit);
    }

    /**
     * 获取成员坐标（GEOPOS）。
     *
     * @param key    缓存键（无需含前缀）
     * @param member 成员
     * @return 坐标点；成员不存在或异常时返回 null
     */
    public Point geoPosition(String key, Object member) {
        return geoOps.geoPosition(key, member);
    }

    /**
     * 按中心点半径搜索成员（GEORADIUS）。
     *
     * @param key       缓存键（无需含前缀）
     * @param longitude 中心点经度
     * @param latitude  中心点纬度
     * @param radius    半径
     * @param unit      距离单位
     * @return 半径内成员及距离结果；异常时返回 null
     */
    public GeoResults<RedisGeoCommands.GeoLocation<Object>> geoRadius(String key, double longitude, double latitude, double radius, Metrics unit) {
        return geoOps.geoRadius(key, longitude, latitude, radius, unit);
    }

    // ============================ HyperLogLog 操作（委托 RedisGeoOps）=============================

    /**
     * 添加元素到 HyperLogLog（PFADD）。
     *
     * @param key    缓存键（无需含前缀）
     * @param values 元素
     * @return true-基数可能变化；异常时返回 false
     */
    public boolean pfAdd(String key, Object... values) {
        return geoOps.pfAdd(key, values);
    }

    /**
     * 获取 HyperLogLog 近似基数（PFCOUNT，支持多键合并估算）。
     *
     * @param keys 缓存键（无需含前缀）
     * @return 近似去重计数；异常时返回 0
     */
    public long pfCount(String... keys) {
        return geoOps.pfCount(keys);
    }

    /**
     * 合并多个 HyperLogLog 到目标（PFMERGE）。
     *
     * @param destination 目标键（无需含前缀）
     * @param sources     源键集合（无需含前缀）
     * @return true-合并成功；异常时返回 false
     */
    public boolean pfMerge(String destination, String... sources) {
        return geoOps.pfMerge(destination, sources);
    }

    // ============================ Pipeline 操作（委托 RedisAdvancedOps）=============================

    /**
     * 执行 Pipeline 批量操作
     *
     * <p>将多个 Redis 命令打包后一次性发送到服务器，减少网络往返次数，
     * 显著提升批量操作的性能。适用于非事务性的高吞吐场景。
     *
     * <p><b>注意：</b>Pipeline 不保证原子性，如果需要原子性请使用事务。
     *
     * @param action 批量操作函数，接收 RedisCallback 参数
     * @return 操作结果列表
     * @see #executePipelined(SessionCallback, Class)
     * @see #executePipelined(Consumer)
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * List<Object> results = redisService.executePipelined(connection -> {
     *     byte[] rawKey = redisTemplate.getStringSerializer().serialize("key");
     *     byte[] rawValue = redisTemplate.getValueSerializer().serialize("value");
     *     connection.stringCommands().set(rawKey, rawValue);
     *     connection.stringCommands().get(rawKey);
     *     return null;
     * });
     * }</pre>
     */
    public List<Object> executePipelined(RedisCallback<?> action) {
        return advancedOps.executePipelined(action);
    }

    /**
     * 执行 Pipeline 批量操作（带类型安全的 SessionCallback）
     *
     * <p>使用 SessionCallback 可以在 Pipeline 中执行有状态的操作，
     * 返回结果会自动转换为目标类型。
     *
     * @param action 批量操作函数，接收 SessionCallback 参数
     * @param clazz  结果元素类型
     * @param <T>    结果类型
     * @return 操作结果列表
     * @see #executePipelined(RedisCallback)
     * @see #executePipelined(Consumer)
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * List<String> results = redisService.executePipelined(
     *     session -> {
     *         redisTemplate.opsForValue().set("key1", "value1");
     *         redisTemplate.opsForValue().get("key1");
     *         return null;
     *     },
     *     String.class
     * );
     * }</pre>
     */
    public <T> List<T> executePipelined(SessionCallback<T> action, Class<T> clazz) {
        return advancedOps.executePipelined(action, clazz);
    }

    /**
     * 执行 Pipeline 批量操作（简化 Consumer 版本）
     *
     * <p>最简单的 Pipeline 使用方式，直接传入对 RedisTemplate 的操作即可。
     * 适用于大多数批量操作场景。
     *
     * @param operations 批量操作函数，接收 RedisTemplate 参数
     * @param <T>        结果类型
     * @return 操作结果列表
     * @see #executePipelined(RedisCallback)
     * @see #executePipelined(SessionCallback, Class)
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * List<Object> results = redisService.executePipelinedWithConsumer(template -> {
     *     template.opsForValue().set("key1", "value1");
     *     template.opsForValue().set("key2", "value2");
     *     template.opsForHash().put("hashKey", "field1", "fieldValue1");
     * });
     * }</pre>
     */
    public <T> List<T> executePipelinedWithConsumer(Consumer<RedisTemplate<String, Object>> operations) {
        return advancedOps.executePipelinedWithConsumer(operations);
    }

    /**
     * 执行 Pipeline 批量操作（使用便捷包装器）
     *
     * <p>提供对 Pipeline 的便捷访问，通过 RedisPipelineOps 包装器简化常用操作。
     * 适用于需要细粒度控制 Pipeline 操作但又不想处理底层字节序列化的场景。
     *
     * <p><b>注意：</b>Pipeline 不保证原子性，如果需要原子性请使用事务。
     *
     * @param actions 批量操作函数，接收 RedisPipelineOps 参数
     * @see #executePipelined(RedisCallback)
     * @see #executePipelined(SessionCallback, Class)
     * @see #executePipelined(Consumer)
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * redisService.pipelineOps(pipeline -> {
     *     pipeline.setString("user:1:name", "张三");
     *     pipeline.setString("user:1:age", "25");
     *     pipeline.hashPut("user:1:profile", "email", "zhangsan@example.com");
     *     pipeline.listRightPush("user:1:logs", "login");
     * });
     * }</pre>
     */
    public void pipelineOps(Consumer<RedisPipelineOps> actions) {
        if (actions == null) {
            return;
        }
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                actions.accept(new RedisPipelineOpsImpl(redisTemplate, connection));
                return null;
            });
        } catch (Exception e) {
            log.error("【Redis】Pipeline 执行失败 | error={}", e.getMessage());
        }
    }

    /**
     * Pipeline 批量设置键值对
     *
     * <p>使用 Pipeline 模式批量执行 SET 命令，减少网络往返次数，
     * 相比多次单独调用 set 方法有显著的性能提升。
     * 适用于批量缓存预热、批量数据写入等场景。
     *
     * <p><b>注意：</b>此方法不保证原子性。如果需要原子性和过期时间，请使用 {@link BatchRedisOperations#msetWithExpire}
     *
     * @param map 键值对映射，key 为 Redis 键，value 为要存储的值
     * @see #pipelineGet
     * @see #pipelineDelete
     * @see BatchRedisOperations#msetWithExpire
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * // 批量设置用户缓存
     * Map<String, Object> userCache = new HashMap<>();
     * userCache.put("user:1001", new UserInfo(1001L, "张三"));
     * userCache.put("user:1002", new UserInfo(1002L, "李四"));
     * userCache.put("user:1003", new UserInfo(1003L, "王五"));
     * redisService.pipelineSet(userCache);
     *
     * // 批量设置配置项
     * Map<String, Object> configs = Map.of(
     *     "config:site:name", "我的网站",
     *     "config:site:title", "网站标题",
     *     "config:site:desc", "网站描述"
     * );
     * redisService.pipelineSet(configs);
     * }</pre>
     */
    public void pipelineSet(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        String formattedKey = formatKey(entry.getKey());
                        byte[] rawKey = redisTemplate.getStringSerializer().serialize(formattedKey);
                        byte[] rawValue = ((RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(entry.getValue());
                        if (rawKey != null && rawValue != null) {
                            connection.stringCommands().set(rawKey, rawValue);
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("【Redis】Pipeline 批量 SET 失败 | mapSize={} | error={}", map.size(), e.getMessage());
        }
    }

    /**
     * Pipeline 批量获取值
     *
     * <p>使用 Pipeline 模式批量执行 GET 命令，减少网络往返次数，
     * 相比多次单独调用 get 方法有显著的性能提升。
     * 适用于批量读取缓存、批量数据查询等场景。
     *
     * <p><b>返回值说明：</b>返回的列表顺序与输入的 keys 顺序一致，
     * 不存在的键对应位置返回 null。
     *
     * <p><b>注意：</b>此方法不进行类型转换，如需类型安全的转换，请使用 {@link #mgetObjects}
     *
     * @param keys 要获取的键集合
     * @return 值列表，顺序与 keys 一致，不存在的键对应位置为 null
     * @see #pipelineSet
     * @see #pipelineDelete
     * @see #mgetObjects
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * // 批量获取用户信息
     * List<String> keys = Arrays.asList("user:1001", "user:1002", "user:1003");
     * List<Object> values = redisService.pipelineGet(keys);
     *
     * // 处理结果
     * for (int i = 0; i < keys.size(); i++) {
     *     String key = keys.get(i);
     *     Object value = values.get(i);
     *     if (value != null) {
     *         log.info("{} = {}", key, value);
     *     } else {
     *         log.info("{} 不存在", key);
     *     }
     * }
     *
     * // 批量获取并转换类型
     * List<UserInfo> users = redisService.mgetObjects(keys, UserInfo.class);
     * }</pre>
     */
    public List<Object> pipelineGet(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String key : keys) {
                    if (key != null) {
                        String formattedKey = formatKey(key);
                        byte[] rawKey = redisTemplate.getStringSerializer().serialize(formattedKey);
                        if (rawKey != null) {
                            connection.stringCommands().get(rawKey);
                        }
                    }
                }
                return null;
            });
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.error("【Redis】Pipeline 批量 GET 失败 | keyCount={} | error={}", keys.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Pipeline 批量删除键
     *
     * <p>使用 Pipeline 模式批量执行 DEL 命令，减少网络往返次数，
     * 相比多次单独调用 del 方法有显著的性能提升。
     * 适用于批量清除缓存、批量删除过期数据等场景。
     *
     * <p><b>返回值说明：</b>返回的列表包含每个 DEL 命令的结果（0 或 1），
     * 表示对应键是否被成功删除。
     *
     * @param keys 要删除的键集合
     * @return 每个键的删除结果列表（Boolean），顺序与 keys 一致
     * @see #pipelineSet
     * @see #pipelineGet
     * @see #del
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * // 批量删除用户缓存
     * List<String> keys = Arrays.asList("user:1001", "user:1002", "user:1003");
     * List<Object> results = redisService.pipelineDelete(keys);
     *
     * // 统计删除结果
     * long deletedCount = results.stream().filter(r -> Boolean.TRUE.equals(r)).count();
     * log.info("成功删除 {} 个键", deletedCount);
     *
     * // 批量删除特定模式的键（结合 scan）
     * Set<String> keysToDelete = redisService.scan("temp:*");
     * redisService.pipelineDelete(keysToDelete);
     * }</pre>
     */
    public List<Object> pipelineDelete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String key : keys) {
                    if (key != null) {
                        String formattedKey = formatKey(key);
                        byte[] rawKey = redisTemplate.getStringSerializer().serialize(formattedKey);
                        if (rawKey != null) {
                            connection.keyCommands().del(new byte[][]{rawKey});
                        }
                    }
                }
                return null;
            });
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.error("【Redis】Pipeline 批量 DELETE 失败 | keyCount={} | error={}", keys.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================ 底层 execute 操作 =============================

    /**
     * 执行底层 Redis 回调操作。
     *
     * <p>用于需要直接操作 {@link org.springframework.data.redis.connection.RedisConnection}
     * 的高级场景，返回结果由调用方自行处理。
     *
     * @param action Redis 回调
     * @param <T>    返回类型
     * @return 回调执行结果
     */
    public <T> T execute(RedisCallback<T> action) {
        return redisTemplate.execute(action);
    }

    /**
     * 执行 Redis Lua 脚本。
     *
     * @param script Lua 脚本对象
     * @param keys   Redis Key 列表（会自动添加统一前缀）
     * @param args   脚本参数
     * @param <T>    返回类型
     * @return 脚本执行结果
     */
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, formatKeys(keys), args);
    }

    // ============================ Lua 脚本操作（委托 RedisAdvancedOps）=============================

    /**
     * 执行 Lua 脚本（委托 RedisAdvancedOps，keys 自动拼接前缀）。
     *
     * @param script     Lua 脚本内容
     * @param keys       Redis 键列表（无需含前缀，内部拼接）
     * @param returnType 返回值类型
     * @param args       脚本参数
     * @param <T>        返回值类型
     * @return 脚本执行结果；脚本为 null 或异常时返回 null
     */
    public <T> T executeScript(String script, List<String> keys, Class<T> returnType, Object... args) {
        return advancedOps.executeScript(script, keys, returnType, args);
    }

    // ============================ Key 操作 =============================

    /**
     * 删除指定 Key
     *
     * @param key Redis Key
     * @return true-删除成功，false-Key 不存在或删除失败
     */
    public boolean delete(String key) {
        if (key == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.error("【Redis】DELETE 操作失败 | key={} | error={}", key, e.getMessage());
            return false;
        }
    }

    // ============================ BatchRedisOperations 接口实现 =============================

    @Override
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    // ============================ 子组件访问器（新代码推荐使用）=============================

    /**
     * 获取字符串（String）操作子组件。
     *
     * <p>相比本类上的同名委托方法，直接使用子组件可获得更聚焦的 API 与更好的可测试性，
     * <b>新代码推荐优先使用子组件</b>。返回的实例由构造器注入，全局单例且线程安全。
     *
     * @return 字符串操作子组件，永不为 {@code null}
     */
    public RedisStringOps stringOps() {
        return stringOps;
    }

    /**
     * 获取哈希（Hash）操作子组件。
     *
     * @return 哈希操作子组件，永不为 {@code null}
     * @see #stringOps()
     */
    public RedisHashOps hashOps() {
        return hashOps;
    }

    /**
     * 获取集合类（List / Set / ZSet）操作子组件。
     *
     * @return 集合操作子组件，永不为 {@code null}
     * @see #stringOps()
     */
    public RedisCollectionOps collectionOps() {
        return collectionOps;
    }

    /**
     * 获取地理位置（GEO）操作子组件。
     *
     * @return 地理位置操作子组件，永不为 {@code null}
     * @see #stringOps()
     */
    public RedisGeoOps geoOps() {
        return geoOps;
    }

    /**
     * 获取高级操作子组件（Lua 脚本、HyperLogLog、Bitmap 等）。
     *
     * @return 高级操作子组件，永不为 {@code null}
     * @see #stringOps()
     */
    public RedisAdvancedOps advancedOps() {
        return advancedOps;
    }

    /**
     * 获取发布订阅（Pub/Sub）操作子组件。
     *
     * <p>注意：Redis 原生 Pub/Sub <b>不保证消息可达</b>，订阅方离线期间的消息会丢失。
     * 对可靠性有要求的场景请改用 {@link #streamOps()}。
     *
     * @return 发布订阅操作子组件，永不为 {@code null}
     */
    public RedisPubSubOps pubSubOps() {
        return pubSubOps;
    }

    /**
     * 获取 Stream 操作子组件。
     *
     * <p>相比 {@link #pubSubOps()}，Stream 支持消息持久化、消费组与 ACK 确认，
     * 适合需要"至少一次"投递语义的场景。
     *
     * @return Stream 操作子组件，永不为 {@code null}
     */
    public RedisStreamOps streamOps() {
        return streamOps;
    }

    /**
     * 获取事务（MULTI/EXEC）操作子组件。
     *
     * <p>Redis 事务仅保证命令<b>顺序执行且不被打断</b>，
     * 不具备关系型数据库的回滚能力：单条命令失败不会撤销已执行的命令。
     * 需要原子性复合逻辑时，优先考虑 {@link #advancedOps()} 中的 Lua 脚本。
     *
     * @return 事务操作子组件，永不为 {@code null}
     */
    public RedisTransactionOps transactionOps() {
        return transactionOps;
    }
}
