package com.njydsz.cronjob.server.core.cache;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.json.YdszJson;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * Job 多级缓存管理器（P3-2 多级缓存架构）。
 *
 * <p>实现 L1（本地 TINYLFU）+ L2（Redis）二级缓存，用于热点 Job 配置数据加速读取。
 *
 * <h3>缓存层级</h3>
 *
 * <ul>
 *   <li><b>L1 本地缓存</b>：{@code Cache<String, JobVO>}，window-TinyLFU 淘汰策略， 最多 2000 条，写入后 10 分钟过期。适合单节点高频读取。
 *   <li><b>L2 Redis 缓存</b>：Redis String，key 前缀 {@code cronjob:cache:job:}， TTL 30 分钟。跨节点共享，防止集群场景下全节点缓存穿透。
 * </ul>
 *
 * <h3>读写策略</h3>
 *
 * <ul>
 *   <li><b>读取</b>：L1 → L2 → DB（逐级回源），每级命中后填充上层
 *   <li><b>写入</b>：先写 DB，再失效 L1 + 更新 L2
 *   <li><b>删除</b>：先删 DB，再失效 L1 + 删除 L2
 * </ul>
 *
 * <h3>缓存一致性</h3>
 *
 * <p>L1 过期时间短（10min），通过主动失效保证最终一致性。
 * 集群任一节点更新 Job 时清除自身 L1 并更新 L2；其他节点 L1 过期后从 L2 读取最新值。
 *
 * <h3>防穿透</h3>
 *
 * <p>null 值也缓存（{@link CacheNullValue}），防止频繁查询不存在的数据穿透到 DB。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCacheManager {

  /** L1 本地缓存最大条目数 */
  private static final int L1_MAXIMUM_SIZE = 2000;

  /** L1 本地缓存 key 前缀 */
  private static final String L1_KEY_PREFIX = "job:";

  /** L2 Redis 缓存 key 前缀 */
  private static final String L2_KEY_PREFIX = "cronjob:cache:job:";

  /** L2 Redis 缓存 TTL（分钟） */
  private static final long L2_TTL_MINUTES = 30;

  /** null 值占位（防穿透） */
  private static final JobVO NULL_PLACEHOLDER = new JobVO();

  private final RedisTemplate<String, Object> redisTemplate;

  /** L1 本地缓存（window-TinyLFU，线程安全） */
  @SuppressWarnings("unchecked") // @SuppressWarnings 保留原因：泛型擦除，Cache<?, ?> 经 newBuilder() 构造后类型安全
  private final Cache<String, JobVO> l1Cache =
      (Cache<String, JobVO>) (Cache<?, ?>)
          YdszCache.newBuilder()
              .maximumSize(L1_MAXIMUM_SIZE)
              .expireAfterWrite(10, TimeUnit.MINUTES)
              .recordStats()
              .build();

  /**
   * 获取 Job（多级缓存读取）。
   *
   * <p>读取顺序：L1 → L2 → loader（DB 回源）。
   *
   * @param jobKey 任务 KEY
   * @param loader DB 回源函数（L1+L2 均未命中时调用）
   * @return JobVO；不存在返回 null
   */
  public JobVO get(String jobKey, JobCacheLoader loader) {
    // L1 命中
    JobVO l1Value = l1Cache.getIfPresent(L1_KEY_PREFIX + jobKey);
    if (l1Value != null) {
      log.debug("[JobCache] L1 命中: jobKey={}", jobKey);
      return isNullPlaceholder(l1Value) ? null : l1Value;
    }

    // L2 命中
    String l2Key = L2_KEY_PREFIX + jobKey;
    try {
      Object l2Obj = redisTemplate.opsForValue().get(l2Key);
      if (l2Obj instanceof String l2Str) {
        JobVO l2Value = YdszJson.fromJson(l2Str, JobVO.class);
        if (l2Value != null) {
          log.debug("[JobCache] L2 命中: jobKey={}", jobKey);
          // 回填 L1
          l1Cache.put(L1_KEY_PREFIX + jobKey, isNullPlaceholder(l2Value) ? NULL_PLACEHOLDER : l2Value);
          return isNullPlaceholder(l2Value) ? null : l2Value;
        }
      }
    } catch (Exception e) {
      log.warn("[JobCache] L2 读取异常: jobKey={} reason={}", jobKey, e.getMessage());
    }

    // DB 回源
    if (loader != null) {
      JobVO dbValue = loader.loadFromDb(jobKey);
      log.debug("[JobCache] DB 回源: jobKey={} found={}", jobKey, dbValue != null);
      // 填充 L1 + L2
      put(jobKey, dbValue);
      return dbValue;
    }

    return null;
  }

  /**
   * 放入缓存（写入 DB 后调用）。
   *
   * <p>更新 L2 + 失效 L1（L1 过期后从 L2 拉取最新值）。
   *
   * @param jobKey 任务 KEY
   * @param value JobVO（null 时缓存占位符防穿透）
   */
  public void put(String jobKey, JobVO value) {
    String l2Key = L2_KEY_PREFIX + jobKey;
    try {
      if (value == null) {
        redisTemplate.opsForValue().set(l2Key, YdszJson.toJson(NULL_PLACEHOLDER), L2_TTL_MINUTES, TimeUnit.MINUTES);
      } else {
        redisTemplate.opsForValue().set(l2Key, YdszJson.toJson(value), L2_TTL_MINUTES, TimeUnit.MINUTES);
      }
    } catch (Exception e) {
      log.warn("[JobCache] L2 写入异常: jobKey={} reason={}", jobKey, e.getMessage());
    }
    // 失效 L1（下次从 L2 读取最新值）
    l1Cache.invalidate(L1_KEY_PREFIX + jobKey);
  }

  /**
   * 失效缓存（删除/更新 Job 时调用）。
   *
   * @param jobKey 任务 KEY
   */
  public void invalidate(String jobKey) {
    l1Cache.invalidate(L1_KEY_PREFIX + jobKey);
    try {
      redisTemplate.delete(L2_KEY_PREFIX + jobKey);
    } catch (Exception e) {
      log.warn("[JobCache] L2 删除异常: jobKey={} reason={}", jobKey, e.getMessage());
    }
    log.debug("[JobCache] 缓存失效: jobKey={}", jobKey);
  }

  /**
   * 获取本地缓存统计（命中率等）。
   *
   * @return 缓存统计 JSON
   */
  public String getCacheStats() {
    return l1Cache.getStats().toString();
  }

  private boolean isNullPlaceholder(JobVO value) {
    return value == NULL_PLACEHOLDER;
  }

  /**
   * Job Cache 加载器函数式接口（DB 回源）。
   */
  @FunctionalInterface
  public interface JobCacheLoader {
    /**
     * 从 DB 加载 Job。
     *
     * @param jobKey 任务 KEY
     * @return JobVO；不存在返回 null
     */
    JobVO loadFromDb(String jobKey);
  }
}
