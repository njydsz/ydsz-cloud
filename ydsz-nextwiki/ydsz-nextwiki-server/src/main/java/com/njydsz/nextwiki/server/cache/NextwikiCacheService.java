package com.njydsz.nextwiki.server.cache;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.server.metrics.NextwikiMetrics;

/**
 * NextWiki 缓存服务
 *
 * <p>封装文件详情、目录列表、配额用量的 Redis 缓存读写与失效逻辑。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>文件详情：key={@code nw:file:{nodeId}}，TTL 10 分钟（±10% 随机偏移）
 *   <li>目录列表：key={@code nw:children:{parentId}}，TTL 5 分钟（±10% 随机偏移）
 *   <li>配额用量：key={@code nw:quota:{scopeType}:{scopeId}}，TTL 3 分钟（±10% 随机偏移）
 * </ul>
 *
 * <p><b>缓存失效：</b>
 *
 * <ul>
 *   <li>文件创建/更新/删除 → 失效文件详情 + 父目录列表
 *   <li>配额变更（上传/删除/恢复） → 失效对应用户配额
 * </ul>
 *
 * <p><b>防穿透/雪崩：（P1-4 增强）</b>
 *
 * <ul>
 *   <li>空值不缓存（文件/配额不存在时直接返回，避免缓存污染）
 *   <li>TTL 固定 + 随机偏移（避免集中过期）
 *   <li>互斥锁防穿透：缓存未命中时通过分布式锁控制单线程回查 DB，防止缓存击穿
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NextwikiCacheService {

  /** 文件详情缓存前缀 */
  private static final String KEY_FILE = "nw:file:";

  /** 目录子节点列表缓存前缀 */
  private static final String KEY_CHILDREN = "nw:children:";

  /** 配额用量缓存前缀 */
  private static final String KEY_QUOTA = "nw:quota:";

  /** 缓存互斥锁前缀（防穿透） */
  private static final String KEY_LOCK = "nw:lock:";

  /** 文件详情缓存 TTL（秒） */
  private static final long TTL_FILE = 600;

  /** 目录列表缓存 TTL（秒） */
  private static final long TTL_CHILDREN = 300;

  /** 配额用量缓存 TTL（秒） */
  private static final long TTL_QUOTA = 180;

  /** 互斥锁等待超时（毫秒） */
  private static final long LOCK_WAIT_MS = 3000;

  /** 互斥锁持有超时（秒） */
  private static final long LOCK_LEASE_S = 10;

  /** 缓存击穿保护重试等待时间（毫秒） */
  private static final long RETRY_WAIT_MILLIS = 50;

  /** TTL 抖动幅度分母（基础 TTL 的 1/10） */
  private static final long TTL_JITTER_FRACTION = 10;

  /** TTL 抖动回落分母（基础 TTL 的 1/20） */
  private static final long TTL_JITTER_REDUCE_FRACTION = 20;

  private final RedisStringOps redisStringOps;
  private final NextwikiMetrics nextwikiMetrics;

  // ==================== 文件详情缓存 ====================

  /**
   * 获取文件详情（优先缓存）。
   *
   * <p>缓存未命中时通过 {@code loader} 从数据库加载并回填缓存。
   *
   * @param nodeId 文件节点 ID
   * @param loader 数据库加载函数
   * @return 文件节点 VO；不存在返回 {@code Optional.empty()}
   */
  public Optional<FileNodeVO> getFile(String nodeId, Supplier<Optional<FileNodeVO>> loader) {
    String key = KEY_FILE + nodeId;
    String lockKey = KEY_LOCK + "file:" + nodeId;
    String metricName = "file";

    // 尝试从缓存读取
    Optional<FileNodeVO> cached = getFromCache(key, FileNodeVO.class);
    if (cached != null) {
      recordCacheHit(metricName);
      return cached;
    }
    recordCacheMiss(metricName);

    // 互斥锁防穿透
    return getOrLoadWithLock(key, lockKey, TTL_FILE, metricName, loader);
  }

  /**
   * 失效文件详情缓存。
   *
   * @param nodeId 文件节点 ID
   */
  public void evictFile(String nodeId) {
    try {
      redisStringOps.del(KEY_FILE + nodeId);
      log.debug("[NextwikiCacheService] 文件详情缓存失效: nodeId={}", nodeId);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 文件详情缓存失效异常: nodeId={}, err={}", nodeId, e.getMessage(), e);
    }
  }

  // ==================== 目录列表缓存 ====================

  /**
   * 获取目录子节点列表（优先缓存）。
   *
   * <p>缓存未命中时通过 {@code loader} 从数据库加载并回填缓存。
   *
   * @param parentId 父节点 ID
   * @param loader 数据库加载函数
   * @return 子节点 VO 列表
   */
  public List<FileNodeVO> getChildren(String parentId, Supplier<List<FileNodeVO>> loader) {
    String key = KEY_CHILDREN + parentId;
    String lockKey = KEY_LOCK + "children:" + parentId;
    String metricName = "children";

    // 尝试从缓存读取（使用 JSON 序列化）
    try {
      String json = redisStringOps.get(key, String.class);
      if (json != null && !json.isEmpty()) {
        List<FileNodeVO> cached = YdszJson.fromJson(json, List.class, FileNodeVO.class);
        if (cached != null && !cached.isEmpty()) {
          recordCacheHit(metricName);
          return cached;
        }
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 目录列表缓存读取异常: parentId={}, err={}", parentId, e.getMessage(), e);
    }
    recordCacheMiss(metricName);

    // 互斥锁防穿透
    if (acquireLock(lockKey)) {
      try {
        // 双重检查
        String jsonCheck = redisStringOps.get(key, String.class);
        if (jsonCheck != null && !jsonCheck.isEmpty()) {
          List<FileNodeVO> doubleCheck = YdszJson.fromJson(jsonCheck, List.class, FileNodeVO.class);
          if (doubleCheck != null && !doubleCheck.isEmpty()) {
            return doubleCheck;
          }
        }

        List<FileNodeVO> result = loader.get();
        if (result != null && !result.isEmpty()) {
          putToCache(key, YdszJson.toJson(result), jitterTtl(TTL_CHILDREN));
          log.debug("[NextwikiCacheService] 目录列表缓存回填: parentId={}, size={}", parentId, result.size());
        }
        return result != null ? result : Collections.emptyList();
      } finally {
        releaseLock(lockKey);
      }
    }

    return waitForJsonListCache(key, lockKey);
  }

  /**
   * 失效目录子节点列表缓存。
   *
   * @param parentId 父节点 ID
   */
  public void evictChildren(String parentId) {
    try {
      redisStringOps.del(KEY_CHILDREN + parentId);
      log.debug("[NextwikiCacheService] 目录列表缓存失效: parentId={}", parentId);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 目录列表缓存失效异常: parentId={}, err={}", parentId, e.getMessage(), e);
    }
  }

  // ==================== 配额用量缓存 ====================

  /**
   * 获取配额用量（优先缓存）。
   *
   * <p>缓存未命中时通过 {@code loader} 从数据库加载并回填缓存。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   * @param loader 数据库加载函数
   * @return 配额 VO；不存在返回 {@code Optional.empty()}
   */
  public Optional<StorageQuotaVO> getQuota(String scopeType, String scopeId,
      Supplier<Optional<StorageQuotaVO>> loader) {
    String key = KEY_QUOTA + scopeType + ":" + scopeId;
    String lockKey = KEY_LOCK + "quota:" + scopeType + ":" + scopeId;
    String metricName = "quota";

    // 尝试从缓存读取
    Optional<StorageQuotaVO> cached = getFromCache(key, StorageQuotaVO.class);
    if (cached != null) {
      recordCacheHit(metricName);
      return cached;
    }
    recordCacheMiss(metricName);

    // 互斥锁防穿透
    return getOrLoadWithLock(key, lockKey, TTL_QUOTA, metricName, loader);
  }

  /**
   * 失效配额用量缓存。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   */
  public void evictQuota(String scopeType, String scopeId) {
    try {
      redisStringOps.del(KEY_QUOTA + scopeType + ":" + scopeId);
      log.debug("[NextwikiCacheService] 配额用量缓存失效: {}:{}", scopeType, scopeId);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 配额用量缓存失效异常: {}:{}, err={}", scopeType, scopeId, e.getMessage(), e);
    }
  }

  // ==================== 批量失效 ====================

  /**
   * 文件操作后的缓存失效（文件详情 + 父目录列表）。
   *
   * <p>在文件创建/更新/删除/移动后调用，确保缓存与数据库一致。
   *
   * @param nodeId 文件节点 ID
   * @param parentId 父节点 ID（可为 null，表示仅失效文件详情）
   */
  public void evictFileAndParent(String nodeId, String parentId) {
    evictFile(nodeId);
    if (parentId != null && !parentId.isEmpty()) {
      evictChildren(parentId);
    }
  }

  /**
   * 配额变更后的缓存失效。
   *
   * <p>在上传/删除/恢复文件导致配额变化后调用。
   *
   * @param scopeType 配额维度
   * @param scopeId 维度 ID
   */
  public void evictQuotaOnChange(String scopeType, String scopeId) {
    evictQuota(scopeType, scopeId);
  }

  // ==================== AI 摘要缓存 ====================

  /** AI 摘要缓存前缀 */
  private static final String KEY_AI_SUMMARY = "nw:ai:summary:";

  /** AI 关键词缓存前缀 */
  private static final String KEY_AI_KEYWORDS = "nw:ai:keywords:";

  /**
   * 获取 AI 摘要缓存。
   *
   * @param key 缓存键（通常为内容哈希）
   * @return 缓存的摘要文本；不存在返回 {@code null}
   */
  public String getAiSummary(String key) {
    try {
      return redisStringOps.get(KEY_AI_SUMMARY + key, String.class);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 摘要缓存读取异常: err={}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 写入 AI 摘要缓存。
   *
   * @param key 缓存键
   * @param summary 摘要文本
   * @param ttlSeconds 过期时间（秒）
   */
  public void putAiSummary(String key, String summary, int ttlSeconds) {
    try {
      redisStringOps.set(KEY_AI_SUMMARY + key, summary, jitterTtl(ttlSeconds));
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 摘要缓存写入异常: err={}", e.getMessage(), e);
    }
  }

  /**
   * 获取 AI 关键词缓存。
   *
   * @param key 缓存键（通常为内容哈希）
   * @return 缓存的关键词列表；不存在返回 {@code null}
   */
  public List<String> getAiKeywords(String key) {
    try {
      String json = redisStringOps.get(KEY_AI_KEYWORDS + key, String.class);
      if (json != null && !json.isEmpty()) {
        return YdszJson.fromJson(json, List.class, String.class);
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 关键词缓存读取异常: err={}", e.getMessage(), e);
    }
    return null;
  }

  /**
   * 写入 AI 关键词缓存。
   *
   * @param key 缓存键
   * @param keywords 关键词列表
   * @param ttlSeconds 过期时间（秒）
   */
  public void putAiKeywords(String key, List<String> keywords, int ttlSeconds) {
    try {
      redisStringOps.set(KEY_AI_KEYWORDS + key, YdszJson.toJson(keywords), jitterTtl(ttlSeconds));
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] AI 关键词缓存写入异常: err={}", e.getMessage(), e);
    }
  }

  // ==================== 通用缓存访问模板 ====================

  /**
   * 通用缓存读取方法（带互斥锁防穿透）。
   *
   * <p>封装通用的缓存访问逻辑：
   * <ol>
   *   <li>查缓存，命中直接返回</li>
   *   <li>未命中则尝试获取互斥锁</li>
   *   <li>获锁成功 → 双重检查 → DB 回查 → 回填缓存 → 释放锁</li>
   *   <li>获锁失败 → 等待后重读缓存</li>
   * </ol>
   *
   * @param key 缓存键
   * @param lockKey 锁键
   * @param ttl 缓存 TTL（秒）
   * @param metricName 指标名称
   * @param loader 数据库加载函数
   * @param <T> 返回值类型
   * @return 缓存值或加载结果
   */
  private <T> Optional<T> getOrLoadWithLock(String key, String lockKey, long ttl, String metricName,
      Supplier<Optional<T>> loader) {
    if (acquireLock(lockKey)) {
      try {
        // 双重检查：其他线程可能已回查
        Optional<T> doubleCheck = getFromCache(key, Object.class);
        if (doubleCheck != null) {
          log.debug("[NextwikiCacheService] 双重检查命中: key={}", key);
          return doubleCheck;
        }

        Optional<T> result = loader.get();
        result.ifPresent(value -> putToCache(key, value, jitterTtl(ttl)));
        return result;
      } finally {
        releaseLock(lockKey);
      }
    }

    // 未获锁，等待后重读缓存
    return waitForCache(key, lockKey);
  }

  /**
   * 从缓存读取并反序列化为指定类型。
   *
   * @param key 缓存键
   * @param clazz 目标类型
   * @param <T> 返回值类型
   * @return 缓存值；不存在或异常返回 {@code null}
   */
  // 泛型擦除：缓存读取返回 Object，向下转型为 T 在编译期无法验证，调用方保证类型一致
  @SuppressWarnings("unchecked")
  private <T> Optional<T> getFromCache(String key, Class<?> clazz) {
    try {
      Object cached = redisStringOps.get(key, clazz);
      if (cached != null) {
        log.debug("[NextwikiCacheService] 缓存命中: key={}", key);
        return Optional.of((T) cached);
      }
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 缓存读取异常: key={}, err={}", key, e.getMessage(), e);
    }
    return Optional.empty();
  }

  /**
   * 写入缓存。
   *
   * @param key 缓存键
   * @param value 缓存值
   * @param ttl 过期时间（秒）
   */
  private void putToCache(String key, Object value, long ttl) {
    try {
      redisStringOps.set(key, value, ttl);
      log.debug("[NextwikiCacheService] 缓存回填: key={}", key);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 缓存写入异常: key={}, err={}", key, e.getMessage(), e);
    }
  }

  /**
   * 等待其他线程回填缓存后重读。
   *
   * @param key 缓存键
   * @param lockKey 锁键
   * @param <T> 返回值类型
   * @return 缓存值或 {@code Optional.empty()}
   */
  // 泛型擦除：缓存读取返回 Object，向下转型为 T 在编译期无法验证，调用方保证类型一致
  @SuppressWarnings("unchecked")
  private <T> Optional<T> waitForCache(String key, String lockKey) {
    long deadline = System.currentTimeMillis() + LOCK_WAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      try {
        Object cached = redisStringOps.get(key, Object.class);
        if (cached != null) {
          return Optional.of((T) cached);
        }
        Thread.sleep(RETRY_WAIT_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.warn("[NextwikiCacheService] 等待缓存异常: lockKey={}, err={}", lockKey, e.getMessage(), e);
        break;
      }
    }
    return Optional.empty();
  }

  /**
   * 等待其他线程回填 JSON 列表缓存后重读。
   *
   * @param key 缓存键
   * @param lockKey 锁键
   * @return 子节点列表
   */
  private List<FileNodeVO> waitForJsonListCache(String key, String lockKey) {
    long deadline = System.currentTimeMillis() + LOCK_WAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      try {
        String json = redisStringOps.get(key, String.class);
        if (json != null && !json.isEmpty()) {
          List<FileNodeVO> cached = YdszJson.fromJson(json, List.class, FileNodeVO.class);
          if (cached != null && !cached.isEmpty()) {
            return cached;
          }
        }
        Thread.sleep(RETRY_WAIT_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.warn("[NextwikiCacheService] 等待目录缓存异常: lockKey={}, err={}", lockKey, e.getMessage());
        break;
      }
    }
    return Collections.emptyList();
  }

  /**
   * 记录缓存命中指标。
   *
   * @param metricName 指标名称
   */
  private void recordCacheHit(String metricName) {
    nextwikiMetrics.recordCacheHit(metricName);
  }

  /**
   * 记录缓存未命中指标。
   *
   * @param metricName 指标名称
   */
  private void recordCacheMiss(String metricName) {
    nextwikiMetrics.recordCacheMiss(metricName);
  }

  // ==================== 私有工具方法 ====================

  /**
   * 获取分布式互斥锁（防缓存穿透）。
   *
   * <p>使用 Redis SETNX + 过期时间实现互斥锁，仅一个线程能成功获锁执行 DB 回查。
   *
   * @param lockKey 锁键
   * @return {@code true} 表示获锁成功
   */
  private boolean acquireLock(String lockKey) {
    try {
      return Boolean.TRUE.equals(redisStringOps.setIfAbsent(lockKey, "1", LOCK_LEASE_S));
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 获取互斥锁异常: lockKey={}, err={}", lockKey, e.getMessage(), e);
      // 异常时放行（允许直接查 DB，避免因 Redis 故障导致服务不可用）
      return true;
    }
  }

  /**
   * 释放分布式互斥锁。
   *
   * @param lockKey 锁键
   */
  private void releaseLock(String lockKey) {
    try {
      redisStringOps.del(lockKey);
    } catch (Exception e) {
      log.warn("[NextwikiCacheService] 释放互斥锁异常: lockKey={}, err={}", lockKey, e.getMessage(), e);
    }
  }

  /**
   * TTL 随机偏移（防雪崩）。
   *
   * <p>在基础 TTL 上增加 ±10% 的随机偏移，避免同类 key 集中过期。
   *
   * @param baseTtl 基础 TTL（秒）
   * @return 偏移后的 TTL
   */
  private long jitterTtl(long baseTtl) {
    long jitter = ThreadLocalRandom.current().nextLong(baseTtl / TTL_JITTER_FRACTION);
    return baseTtl + jitter - (baseTtl / TTL_JITTER_REDUCE_FRACTION);
  }
}
