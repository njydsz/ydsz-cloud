package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 基于 {@link RedisStringOps} 的 {@link CacheProvider} 默认实现
 *
 * <p>作为注解缓存切面（{@code @YdszCacheable}、{@code @YdszCacheEvict}、{@code @YdszCachePut}）
 * 与底层 Redis 操作组件之间的适配桥梁，使切面仅依赖 {@link CacheProvider} 接口，符合接口隔离原则。
 *
 * <p>如需替换为多级缓存（Caffeine + Redis）或自定义缓存策略，只需提供自定义 {@link CacheProvider} 实现并标注
 * {@code @Primary} 即可，无需修改切面代码。
 *
 * <p><b>已废弃：</b>自 v26.09.21 起随 {@link CacheProvider} 接口一并废弃。多级缓存需求请直接使用
 * {@code YdszCache} 作为 L1 + Redis 操作组件作为 L2 自行组合。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 自 v26.09.21 起废弃，请使用 {@link com.njydsz.common.cache.YdszCache} 替代
 */
@Deprecated(since = "26.09.21", forRemoval = true)
@SuppressWarnings({"deprecation", "removal"})
// YDIZ-WARN-001 允许保留：实现已标记 forRemoval 的 CacheProvider 接口，自身也一并 @Deprecated(forRemoval=true)，等待迁移完成后整体删除
@RequiredArgsConstructor
public class RedisStringOpsCacheProvider implements CacheProvider {

  private final RedisStringOps redisStringOps;

  @Override
  public Object get(String key) {
    return redisStringOps.get(key);
  }

  @Override
  public <T> T get(String key, Class<T> clazz) {
    return redisStringOps.get(key, clazz);
  }

  @Override
  public boolean set(String key, Object value) {
    return redisStringOps.set(key, value);
  }

  @Override
  public boolean set(String key, Object value, long ttl) {
    return redisStringOps.set(key, value, Duration.ofSeconds(ttl));
  }

  @Override
  public boolean delete(String key) {
    redisStringOps.del(key);
    return true;
  }

  @Override
  public void delete(List<String> keys) {
    redisStringOps.del(keys);
  }

  @Override
  public <T> T executeScript(String script, List<String> keys, Class<T> returnType, Object... args) {
    return redisStringOps.executeScript(script, returnType, keys, args);
  }

  @Override
  public <T> T getOrCompute(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
    return redisStringOps.getOrCompute(key, expire, supplier, clazz);
  }
}
