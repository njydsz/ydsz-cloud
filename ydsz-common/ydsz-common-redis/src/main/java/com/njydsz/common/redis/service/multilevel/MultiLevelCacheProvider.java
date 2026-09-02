package com.njydsz.common.redis.service.multilevel;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.service.CacheProvider;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.string.StringUtils;

/**
 * 多级缓存提供者（L1 YdszCache 本地缓存 + L2 Redis 远程缓存）
 *
 * <p>实现 {@link CacheProvider} 接口，提供两级缓存架构：
 *
 * <ul>
 *   <li><b>L1</b>：YdszCache 本地缓存（TINYLFU 算法），命中率极高、零网络开销，适合热点数据
 *   <li><b>L2</b>：Redis 远程缓存，跨实例共享，适合全局数据
 * </ul>
 *
 * <p><b>读取流程：</b> L1 → L2 → Supplier（回源）
 *
 * <ul>
 *   <li>L1 命中：直接返回（微秒级）
 *   <li>L1 未命中、L2 命中：回填 L1 后返回（毫秒级）
 *   <li>L1+L2 均未命中：调用 supplier 获取数据，写入 L2 和 L1 后返回
 * </ul>
 *
 * <p><b>写入流程：</b> 写入 L2 → 失效 L1（非写入 L1），保证多实例间数据一致性。
 *
 * <p><b>删除流程：</b> 删除 L2 → 失效 L1。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   redis:
 *     multilevel:
 *       enabled: true
 *       l1-max-size: 1000
 *       l1-ttl-seconds: 60
 * }</pre>
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>L1 TTL 应显著小于 L2 TTL，保证数据新鲜度
 *   <li>本组件使用 ydzs-common-cache 统一本地缓存框架
 *   <li>不适用于写多读少或数据实时性要求极高的场景
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CacheProvider
 */
@Slf4j
public class MultiLevelCacheProvider implements CacheProvider {

  /** 多级缓存名称前缀（用于日志与监控区分） */
  private static final String CACHE_NAME = "multilevel";

  /** 空值占位符（与 RedisStringOps 中的保持一致） */
  private static final String NULL_PLACEHOLDER = "__NULL__";

  /** L1 YdszCache 本地缓存实例 */
  private final Cache<String, Object> l1Cache;

  /** L2 Redis 操作组件 */
  private final RedisStringOps redisStringOps;

  /** Redis 配置属性 */
  private final RedisProperties redisProperties;

  /**
   * 构造多级缓存提供者
   *
   * @param redisStringOps L2 Redis 操作组件，不可为 null
   * @param redisProperties Redis 配置属性，不可为 null
   * @param l1MaxSize L1 缓存最大容量
   * @param l1TtlSeconds L1 缓存过期时间（秒），建议小于 L2 TTL
   */
  public MultiLevelCacheProvider(
      RedisStringOps redisStringOps,
      RedisProperties redisProperties,
      long l1MaxSize,
      long l1TtlSeconds) {
    this.redisStringOps = Objects.requireNonNull(redisStringOps, "RedisStringOps 必须不为 null");
    this.redisProperties = Objects.requireNonNull(redisProperties, "RedisProperties 必须不为 null");
    this.l1Cache =
        YdszCache.<String, Object>newBuilder()
            .name(CACHE_NAME)
            .maximumSize(Math.max(1, l1MaxSize))
            .expireAfterWrite(l1TtlSeconds, TimeUnit.SECONDS)
            .removalListener(
                (key, value, cause) ->
                    log.trace("[{}] L1 缓存移除 - key={}, cause={}", CACHE_NAME, key, cause))
            .build();
  }

  @Override
  public Object get(String key) {
    if (key == null) {
      return null;
    }
    // L1 查询
    Object l1Value = l1Cache.getIfPresent(key);
    if (l1Value != null) {
      log.trace("[{}] L1 命中 - key={}", CACHE_NAME, key);
      return NULL_PLACEHOLDER.equals(l1Value) ? null : l1Value;
    }

    // L2 查询
    String jsonValue = redisStringOps.get(key, String.class);
    if (jsonValue != null) {
      log.debug("[{}] L2 命中 - key={}", CACHE_NAME, key);
      Object value = deserialize(jsonValue);
      l1Cache.put(key, value == null ? NULL_PLACEHOLDER : value);
      return value;
    }

    log.debug("[{}] L1+L2 未命中 - key={}", CACHE_NAME, key);
    return null;
  }

  @Override
  public <T> T get(String key, Class<T> clazz) {
    Object value = get(key);
    if (value == null) {
      return null;
    }
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    // L2 返回的是 JSON 字符串，需要反序列化
    if (value instanceof String) {
      return YdszJson.fromJson((String) value, clazz);
    }
    return null;
  }

  @Override
  public boolean set(String key, Object value) {
    return set(key, value, redisProperties.getNullValueTtlSeconds());
  }

  @Override
  public boolean set(String key, Object value, long ttl) {
    if (key == null) {
      return false;
    }
    String jsonValue = serialize(value);
    boolean success = redisStringOps.set(key, jsonValue, ttl);
    if (success) {
      // 写入 L2 后失效 L1，下次读从 L2 获取最新值
      l1Cache.invalidate(key);
    }
    return success;
  }

  @Override
  public boolean delete(String key) {
    if (key == null) {
      return false;
    }
    try {
      redisStringOps.del(key);
      l1Cache.invalidate(key);
      return true;
    } catch (Exception e) {
      log.warn("[{}] L2 删除失败 - key={}, error={}", CACHE_NAME, key, e.getMessage());
      return false;
    }
  }

  @Override
  public void delete(List<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    try {
      redisStringOps.del(keys);
    } catch (Exception e) {
      log.warn("[{}] L2 批量删除失败 - keys={}, error={}", CACHE_NAME, keys, e.getMessage());
    }
    for (String key : keys) {
      l1Cache.invalidate(key);
    }
  }

  @Override
  public <T> T executeScript(
      String script, List<String> keys, Class<T> returnType, Object... args) {
    return redisStringOps.executeScriptWithShaCache(script, returnType, keys, args);
  }

  @Override
  public <T> T getOrCompute(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
    if (key == null || supplier == null) {
      return null;
    }

    // L1 查询
    Object l1Value = l1Cache.getIfPresent(key);
    if (l1Value != null) {
      return NULL_PLACEHOLDER.equals(l1Value) ? null : clazz.cast(l1Value);
    }

    // L2 + Supplier
    T value = redisStringOps.getOrCompute(key, expire, supplier, clazz);
    if (value != null) {
      l1Cache.put(key, value);
    }
    return value;
  }

  /**
   * 序列化值为 JSON 字符串
   *
   * @param value 原始值
   * @return JSON 字符串；值为 null 时返回 null
   */
  private String serialize(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String) {
      return (String) value;
    }
    return YdszJson.toJson(value);
  }

  /**
   * 反序列化 JSON 字符串
   *
   * @param jsonValue JSON 字符串
   * @return 反序列化后的对象；解析失败时返回原始字符串
   */
  private Object deserialize(String jsonValue) {
    if (StringUtils.isBlank(jsonValue) || NULL_PLACEHOLDER.equals(jsonValue)) {
      return null;
    }
    // 简单判断：非 JSON 格式直接返回字符串
    if (!jsonValue.startsWith("{") && !jsonValue.startsWith("[")) {
      return jsonValue;
    }
    try {
      return YdszJson.fromJson(jsonValue, Object.class);
    } catch (Exception e) {
      log.debug("[{}] JSON 反序列化失败，返回原始字符串 - value={}", CACHE_NAME, jsonValue);
      return jsonValue;
    }
  }

  /**
   * 获取 L1 缓存统计信息
   *
   * @return YdszCache 缓存统计
   */
  public CacheStats l1Stats() {
    return l1Cache.getStats();
  }

  /** 手动清空 L1 缓存 */
  public void invalidateAll() {
    l1Cache.invalidateAll();
  }
}
