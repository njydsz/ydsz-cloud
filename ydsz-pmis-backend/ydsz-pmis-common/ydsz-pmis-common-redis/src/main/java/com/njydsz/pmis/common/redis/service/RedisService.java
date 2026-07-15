package com.njydsz.pmis.common.redis.service;

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
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.redis.config.RedisProperties;
import com.njydsz.pmis.common.redis.enums.RedisKeysEnum;
import com.njydsz.pmis.common.redis.service.ops.*;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
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

    public boolean expire(String key, long time) {
        return stringOps.expire(key, time);
    }

    public boolean expire(String key, Duration duration) {
        return stringOps.expire(key, duration);
    }

    public long getExpire(String key) {
        return stringOps.getExpire(key);
    }

    public boolean hasKey(String key) {
        return stringOps.hasKey(key);
    }

    public void del(String... keys) {
        stringOps.del(keys);
    }

    public void del(Collection<String> keys) {
        stringOps.del(keys);
    }

    public long delByPattern(String pattern) {
        return stringOps.delByPattern(pattern);
    }

    public Set<String> scan(String pattern) {
        return stringOps.scan(pattern);
    }

    public Set<String> scan(String pattern, int maxKeys) {
        return stringOps.scan(pattern, maxKeys);
    }

    public boolean rename(String oldKey, String newKey) {
        return stringOps.rename(oldKey, newKey);
    }

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

    public <T> T get(String key, Class<T> clazz) {
        return stringOps.get(key, clazz);
    }

    public boolean set(String key, Object value) {
        return stringOps.set(key, value);
    }

    public boolean set(String key, Object value, long time) {
        return stringOps.set(key, value, time);
    }

    public boolean set(String key, Object value, Duration duration) {
        return stringOps.set(key, value, duration);
    }

    public boolean setIfAbsent(String key, Object value, long expire) {
        return stringOps.setIfAbsent(key, value, expire);
    }

    public boolean setIfPresent(String key, Object value, long expire) {
        return stringOps.setIfPresent(key, value, expire);
    }

    public <T> T getOrCompute(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
        return stringOps.getOrCompute(key, expire, supplier, clazz);
    }

    public <T> T getOrCompute(RedisKeysEnum keyEnum, Object arg, long expire, Supplier<T> supplier, Class<T> clazz) {
        return stringOps.getOrCompute(keyEnum, arg, expire, supplier, clazz);
    }

    public long incr(String key, long delta) {
        return stringOps.incr(key, delta);
    }

    public long decr(String key, long delta) {
        return stringOps.decr(key, delta);
    }

    public double incrByFloat(String key, double delta) {
        return stringOps.incrByFloat(key, delta);
    }

    public List<String> mget(List<String> keys) {
        return stringOps.mget(keys);
    }

    public <T> List<T> mgetObjects(List<String> keys, Class<T> clazz) {
        return stringOps.mgetObjects(keys, clazz);
    }

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

    public void msetPartitioned(Map<String, Object> keyValueMap) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().multiSet(formatKeyMap(keyValueMap));
    }

    // ============================ Hash 操作（委托 RedisHashOps）=============================

    public <T> T hGet(String key, String item, Class<T> clazz) {
        return hashOps.hGet(key, item, clazz);
    }

    public <T> Map<String, T> hGetAll(String key, Class<T> clazz) {
        return hashOps.hGetAll(key, clazz);
    }

    public <T> List<T> hMGet(String key, Collection<Object> items, Class<T> clazz) {
        return hashOps.hMGet(key, items, clazz);
    }

    public boolean hSet(String key, String item, Object value) {
        return hashOps.hSet(key, item, value);
    }

    public boolean hSetIfAbsent(String key, String item, Object value) {
        return hashOps.hSetIfAbsent(key, item, value);
    }

    public boolean hMSet(String key, Map<String, ?> map) {
        return hashOps.hMSet(key, map);
    }

    public long hDel(String key, Object... items) {
        return hashOps.hDel(key, items);
    }

    public boolean hHasKey(String key, String item) {
        return hashOps.hHasKey(key, item);
    }

    public long hSize(String key) {
        return hashOps.hSize(key);
    }

    public long hIncr(String key, String item, long delta) {
        return hashOps.hIncr(key, item, delta);
    }

    public double hIncrByFloat(String key, String item, double delta) {
        return hashOps.hIncrByFloat(key, item, delta);
    }

    public Set<Object> hKeys(String key) {
        return hashOps.hKeys(key);
    }

    public <T> List<T> hValues(String key, Class<T> clazz) {
        return hashOps.hValues(key, clazz);
    }

    // ============================ Set 操作（委托 RedisCollectionOps）=============================

    public <T> Set<T> sMembers(String key, Class<T> clazz) {
        return collectionOps.sMembers(key, clazz);
    }

    public boolean sIsMember(String key, Object value) {
        return collectionOps.sIsMember(key, value);
    }

    public long sSize(String key) {
        return collectionOps.sSize(key);
    }

    public long sAdd(String key, Object... values) {
        return collectionOps.sAdd(key, values);
    }

    public long sRem(String key, Object... values) {
        return collectionOps.sRem(key, values);
    }

    public <T> T sRandomMember(String key, Class<T> clazz) {
        return collectionOps.sRandomMember(key, clazz);
    }

    public <T> List<T> sRandomMembers(String key, long count, Class<T> clazz) {
        return collectionOps.sRandomMembers(key, count, clazz);
    }

    public <T> Set<T> sInter(Class<T> clazz, String... keys) {
        return collectionOps.sInter(clazz, keys);
    }

    public <T> Set<T> sUnion(Class<T> clazz, String... keys) {
        return collectionOps.sUnion(clazz, keys);
    }

    public <T> Set<T> sDiff(Class<T> clazz, String... keys) {
        return collectionOps.sDiff(clazz, keys);
    }

    // ============================ List 操作（委托 RedisCollectionOps）=============================

    public <T> List<T> lRange(String key, long start, long end, Class<T> clazz) {
        return collectionOps.lRange(key, start, end, clazz);
    }

    public long lSize(String key) {
        return collectionOps.lSize(key);
    }

    public <T> T lIndex(String key, long index, Class<T> clazz) {
        return collectionOps.lIndex(key, index, clazz);
    }

    public long lPush(String key, Object value) {
        return collectionOps.lPush(key, value);
    }

    public long rPush(String key, Object value) {
        return collectionOps.rPush(key, value);
    }

    public <T> T lPop(String key, Class<T> clazz) {
        return collectionOps.lPop(key, clazz);
    }

    public <T> T rPop(String key, Class<T> clazz) {
        return collectionOps.rPop(key, clazz);
    }

    public long lRem(String key, long count, Object value) {
        return collectionOps.lRem(key, count, value);
    }

    public boolean lSet(String key, long index, Object value) {
        return collectionOps.lSet(key, index, value);
    }

    public boolean lTrim(String key, long start, long end) {
        return collectionOps.lTrim(key, start, end);
    }

    // ============================ ZSet 操作（委托 RedisCollectionOps）=============================

    public boolean zAdd(String key, Object value, double score) {
        return collectionOps.zAdd(key, value, score);
    }

    public long zAdd(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        return collectionOps.zAdd(key, tuples);
    }

    public long zSize(String key) {
        return collectionOps.zSize(key);
    }

    public long zCount(String key, double min, double max) {
        return collectionOps.zCount(key, min, max);
    }

    public <T> Set<T> zRange(String key, long start, long end, Class<T> clazz) {
        return collectionOps.zRange(key, start, end, clazz);
    }

    public <T> Set<T> zReverseRange(String key, long start, long end, Class<T> clazz) {
        return collectionOps.zReverseRange(key, start, end, clazz);
    }

    public <T> Set<T> zRangeByScore(String key, double min, double max, Class<T> clazz) {
        return collectionOps.zRangeByScore(key, min, max, clazz);
    }

    public Set<ZSetOperations.TypedTuple<Object>> zRangeByScoreWithScores(String key, double min, double max) {
        return collectionOps.zRangeByScoreWithScores(key, min, max);
    }

    public Long zRank(String key, Object value) {
        return collectionOps.zRank(key, value);
    }

    public Long zReverseRank(String key, Object value) {
        return collectionOps.zReverseRank(key, value);
    }

    public Double zScore(String key, Object value) {
        return collectionOps.zScore(key, value);
    }

    public long zRem(String key, Object... values) {
        return collectionOps.zRem(key, values);
    }

    public long zRemoveRange(String key, long start, long end) {
        return collectionOps.zRemoveRange(key, start, end);
    }

    public long zRemoveRangeByScore(String key, double min, double max) {
        return collectionOps.zRemoveRangeByScore(key, min, max);
    }

    // ============================ Bitmap 操作（委托 RedisStringOps）=============================

    public boolean setBit(String key, long offset, boolean value) {
        return stringOps.setBit(key, offset, value);
    }

    public boolean getBit(String key, long offset) {
        return stringOps.getBit(key, offset);
    }

    public long bitCount(String key) {
        return stringOps.bitCount(key);
    }

    // ============================ Geo 操作（委托 RedisGeoOps）=============================

    public boolean geoAdd(String key, Object member, double longitude, double latitude) {
        return geoOps.geoAdd(key, member, longitude, latitude);
    }

    public Distance geoDistance(String key, Object member1, Object member2, Metrics unit) {
        return geoOps.geoDistance(key, member1, member2, unit);
    }

    public Point geoPosition(String key, Object member) {
        return geoOps.geoPosition(key, member);
    }

    public GeoResults<RedisGeoCommands.GeoLocation<Object>> geoRadius(String key, double longitude, double latitude, double radius, Metrics unit) {
        return geoOps.geoRadius(key, longitude, latitude, radius, unit);
    }

    // ============================ HyperLogLog 操作（委托 RedisGeoOps）=============================

    public boolean pfAdd(String key, Object... values) {
        return geoOps.pfAdd(key, values);
    }

    public long pfCount(String... keys) {
        return geoOps.pfCount(keys);
    }

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

    // ============================ Lua 脚本操作（委托 RedisAdvancedOps）=============================

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

    public RedisStringOps stringOps() {
        return stringOps;
    }

    public RedisHashOps hashOps() {
        return hashOps;
    }

    public RedisCollectionOps collectionOps() {
        return collectionOps;
    }

    public RedisGeoOps geoOps() {
        return geoOps;
    }

    public RedisAdvancedOps advancedOps() {
        return advancedOps;
    }

    public RedisPubSubOps pubSubOps() {
        return pubSubOps;
    }

    public RedisStreamOps streamOps() {
        return streamOps;
    }

    public RedisTransactionOps transactionOps() {
        return transactionOps;
    }
}
