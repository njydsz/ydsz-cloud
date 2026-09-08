package com.njydsz.common.base.actuator;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import com.njydsz.common.lock.scheduler.LockWatchDog.WatchTask;

/**
 * 分布式锁运维管理端点
 *
 * <p>基于 Spring Boot Actuator 的运维端点，替代原来位于 ydzs-common-lock（L4）中的
 * {@code LockAdminController}（{@code @RestController}），符合云顶编码规范 §22.2 层级定位：
 * L4 基础数据层不持有 Web 层组件，运维操作统一通过 Actuator（management port）暴露。
 *
 * <p>提供锁的运行时管理能力：
 *
 * <ul>
 *   <li>查询当前锁指标（获取成功率、平均耗时、竞争次数等）
 *   <li>查看当前活跃的续期任务与活跃锁分页列表
 *   <li>强制执行锁释放（用于死锁恢复，支持单条与批量）
 *   <li>查询指定 key 的锁状态
 *   <li>锁统计摘要（按类型分组）
 * </ul>
 *
 * <p><b>安全注意：</b>本端点仅内网访问或通过网关配置访问控制，
 * 避免外部调用 force-unlock 导致数据不一致。建议使用 management.port
 * 将运维端点隔离在独立端口。
 *
 * <p><b>端点 URL 示例：</b>
 *
 * <ul>
 *   <li>{@code GET /actuator/lock} — 获取锁运行时指标快照
 *   <li>{@code GET /actuator/lock/status/{key}} — 查询指定锁状态
 *   <li>{@code GET /actuator/lock/active} — 活跃锁分页列表（含 pattern/page/size 参数）
 *   <li>{@code GET /actuator/lock/search} — 按模式搜索锁 key
 *   <li>{@code GET /actuator/lock/summary} — 锁统计摘要
 *   <li>{@code GET /actuator/lock/watchdog} — 活跃续期任务数
 *   <li>{@code DELETE /actuator/lock/{key}} — 强制释放指定锁（死锁恢复）
 *   <li>{@code POST /actuator/lock/batch-force-unlock} — 批量强制释放锁
 * </ul>
 *
 * <p><b>依赖条件：</b>仅当 {@link LockMetrics} 和 {@link LockWatchDog} Bean 存在（即 classpath
 * 包含 ydzz-common-lock 且启用了锁能力）且 Actuator 在 classpath 时装配。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see LockMetrics
 * @see LockWatchDog
 */
@Slf4j
@Endpoint(id = "lock")
@ConditionalOnBean({LockMetrics.class, LockWatchDog.class})
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnProperty(prefix = "ydsz.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LockAdminEndpoint {

  private static final String LOCK_KEY_PREFIX = "lock:";

  /** 默认分页大小 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 最大分页大小（防止单次查询数据量过大） */
  private static final int MAX_PAGE_SIZE = 100;

  /** 默认扫描批次大小 */
  private static final int SCAN_BATCH_SIZE = 50;

  private final LockMetrics lockMetrics;
  private final LockWatchDog lockWatchDog;

  /** Redis 模板（可选，仅当消费方提供时才进行 Redis 操作） */
  @Autowired(required = false)
  private StringRedisTemplate redisTemplate;

  /**
   * 构造锁运维管理端点
   *
   * @param lockMetrics 锁指标收集器
   * @param lockWatchDog 锁看门狗
   */
  public LockAdminEndpoint(LockMetrics lockMetrics, LockWatchDog lockWatchDog) {
    this.lockMetrics = lockMetrics;
    this.lockWatchDog = lockWatchDog;
  }

  /**
   * 获取锁运行时指标快照
   *
   * @return 包含各项指标的 Map
   */
  @ReadOperation
  public Map<String, Object> metrics() {
    Map<String, Object> metrics = new HashMap<>(16);
    metrics.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
    metrics.put("acquireFailCount", lockMetrics.getAcquireFailCount());
    metrics.put("releaseCount", lockMetrics.getReleaseCount());
    metrics.put(
        "averageWaitTimeMs",
        lockMetrics
            .getAverageWaitTimeMillis()
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString());
    metrics.put(
        "averageHoldTimeMs",
        lockMetrics
            .getAverageHoldTimeMillis()
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString());
    metrics.put("competitionCount", lockMetrics.getCompetitionCount());
    metrics.put("activeLocks", lockMetrics.getActiveLocks());
    metrics.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
    metrics.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
    metrics.put("idempotentHitCount", lockMetrics.getIdempotentHitCount());
    metrics.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
    log.info("[ydsz-lock] [admin] 查询锁指标 active={}", lockMetrics.getActiveLocks());
    return metrics;
  }

  /**
   * 查询指定锁的状态
   *
   * @param key 锁 key（不含前缀）
   * @return 锁状态信息
   */
  @ReadOperation
  public Map<String, Object> lockStatus(@Selector String key) {
    if (redisTemplate == null) {
      return Map.of("error", "StringRedisTemplate 不可用，无法查询锁状态");
    }
    String fullKey = LOCK_KEY_PREFIX + key;
    Map<String, Object> status = new HashMap<>(16);
    status.put("key", fullKey);
    Boolean exists = redisTemplate.hasKey(fullKey);
    status.put("exists", exists != null && exists);
    if (Boolean.TRUE.equals(exists)) {
      Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.MILLISECONDS);
      status.put("ttlMs", ttl);
    }
    log.debug("[ydsz-lock] [admin] 查询锁状态 key={} exists={}", fullKey, exists);
    return status;
  }

  /**
   * 强制释放指定锁（死锁恢复用）
   *
   * <p><b>警告：</b>强制释放可能导致多节点并发访问同一资源，
   * 请在确认锁持有者已宕机或业务已安全的情况下使用。
   *
   * @param key 锁 key（不含前缀）
   * @return 操作结果
   */
  @DeleteOperation
  public Map<String, Object> forceUnlock(@Selector String key) {
    if (redisTemplate == null) {
      return Map.of("error", "StringRedisTemplate 不可用，无法强制释放锁");
    }
    String fullKey = LOCK_KEY_PREFIX + key;
    Map<String, Object> result = new HashMap<>(16);
    result.put("key", fullKey);
    Boolean deleted = redisTemplate.delete(fullKey);
    boolean success = deleted != null && deleted;
    result.put("released", success);
    // 同时停止看门狗续期任务
    lockWatchDog.cancelRenewal(fullKey);
    log.warn("[ydsz-lock] [admin] 强制释放锁 key={} success={}", fullKey, success);
    return result;
  }

  /**
   * 列出活跃续期任务
   *
   * @return 当前活跃的 WatchDog 续期任务数
   */
  @ReadOperation
  public Map<String, Object> activeWatchdogTasks() {
    Map<String, Object> info = new HashMap<>(16);
    info.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
    info.put("timestamp", System.currentTimeMillis());
    return info;
  }

  /**
   * 根据模式搜索锁 key
   *
   * @param pattern 搜索模式（如 "order:*"，使用 Redis KEYS 语法，默认 "lock:*"）
   * @return 匹配的锁 key 列表
   */
  @ReadOperation
  public Set<String> searchKeys(
      @Selector String pattern) {
    if (redisTemplate == null) {
      return Collections.emptySet();
    }
    if (pattern == null || pattern.isEmpty()) {
      pattern = "lock:*";
    }
    Set<String> keys = redisTemplate.keys(pattern);
    log.debug(
        "[ydsz-lock] [admin] 搜索锁 key pattern={} count={}",
        pattern,
        keys == null ? 0 : keys.size());
    return keys == null ? Collections.emptySet() : keys;
  }

  /**
   * 获取活跃锁分页列表（基于 SCAN 游标，避免 KEYS 阻塞 Redis）
   *
   * <p>使用 Redis SCAN 渐进式遍历，对 Redis 性能影响远低于 KEYS 命令。
   * 返回当前持有锁的详细信息：锁键、TTL、看门狗续期状态。
   *
   * @param pattern 搜索模式（默认 "lock:*"）
   * @param page 页码（从 0 开始，默认 0）
   * @param size 每页条数（默认 20，最大 100）
   * @return 活跃锁分页结果
   */
  @ReadOperation
  public Map<String, Object> activeLocks(
      @Selector String pattern,
      @Selector int page,
      @Selector int size) {
    if (redisTemplate == null) {
      return Map.of("error", "StringRedisTemplate 不可用");
    }
    if (pattern == null || pattern.isEmpty()) {
      pattern = "lock:*";
    }
    int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int skip = Math.max(page, 0) * normalizedSize;

    List<Map<String, Object>> activeLockList = new ArrayList<>(16);
    int scanned = 0;

    // 使用 SCAN 渐进式遍历，每次扫描 SCAN_BATCH_SIZE 个 key
    for (int batch = 0;
        batch < MAX_PAGE_SIZE * 2 && activeLockList.size() < normalizedSize;
        batch++) {
      List<String> batchKeys = scanKeysBatch(pattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String redisKey : batchKeys) {
        scanned++;
        if (scanned <= skip) {
          continue;
        }
        if (activeLockList.size() >= normalizedSize) {
          break;
        }
        Map<String, Object> lockInfo = buildLockInfo(redisKey);
        activeLockList.add(lockInfo);
      }
    }

    Map<String, Object> result = new HashMap<>(16);
    result.put("locks", activeLockList);
    result.put("page", page);
    result.put("size", normalizedSize);
    result.put("scanned", scanned);
    result.put("hasMore", activeLockList.size() >= normalizedSize);
    result.put("timestamp", System.currentTimeMillis());

    log.info(
        "[ydsz-lock] [admin] 查询活跃锁 page={} size={} count={}",
        page,
        normalizedSize,
        activeLockList.size());
    return result;
  }

  /**
   * 获取锁统计摘要（按锁类型/前缀分组）
   *
   * <p>快速统计活跃锁的分布情况：按锁键前缀段（冒号前的第一段）分组计数，
   * 辅助运维快速定位热点锁类型。
   *
   * @param pattern 搜索模式（默认 "lock:*"）
   * @return 锁统计摘要
   */
  @ReadOperation
  public Map<String, Object> summary(@Selector String pattern) {
    if (redisTemplate == null) {
      return Map.of("error", "StringRedisTemplate 不可用");
    }
    if (pattern == null || pattern.isEmpty()) {
      pattern = "lock:*";
    }
    Map<String, Integer> categoryCount = new HashMap<>(16);
    int totalActive = 0;

    // 仅扫描前 200 个 key 做统计摘要（避免大库阻塞）
    int limit = 200;
    for (int batch = 0; batch < 4 && totalActive < limit; batch++) {
      List<String> batchKeys = scanKeysBatch(pattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String redisKey : batchKeys) {
        totalActive++;
        String category = extractCategory(redisKey);
        categoryCount.merge(category, 1, Integer::sum);
        if (totalActive >= limit) {
          break;
        }
      }
    }

    Map<String, Object> summary = new HashMap<>(16);
    summary.put("totalActive", totalActive);
    summary.put("categoryDistribution", categoryCount);
    summary.put("metrics", buildMetricsSnapshot());
    summary.put("watchdogActiveTasks", lockWatchDog.getActiveTaskCount());
    summary.put("timestamp", System.currentTimeMillis());

    log.info(
        "[ydsz-lock] [admin] 查询锁摘要 totalActive={} categories={}",
        totalActive,
        categoryCount.size());
    return summary;
  }

  /**
   * 批量强制释放锁（紧急死锁恢复）
   *
   * <p><b>警告：</b>批量释放可能导致多节点并发访问同一资源，
   * 请谨慎操作，确认所有锁持有者已安全后执行。
   *
   * @param keys 锁 key 列表（不含前缀）
   * @return 批量操作结果
   */
  @WriteOperation
  public Map<String, Object> batchForceUnlock(List<String> keys) {
    if (redisTemplate == null) {
      return Map.of("error", "StringRedisTemplate 不可用，无法批量释放锁");
    }
    if (keys == null || keys.isEmpty()) {
      return Map.of("error", "参数错误：keys 不能为空");
    }

    // 限制单次批量操作数量
    int maxBatchSize = 50;
    if (keys.size() > maxBatchSize) {
      Map<String, Object> errorResult = new HashMap<>(16);
      errorResult.put("error", "单次批量释放数量超限，最多支持 " + maxBatchSize + " 个锁");
      return errorResult;
    }

    int successCount = 0;
    int failCount = 0;
    List<String> failedKeys = new ArrayList<>(16);

    for (String key : keys) {
      String fullKey = LOCK_KEY_PREFIX + key;
      try {
        Boolean deleted = redisTemplate.delete(fullKey);
        lockWatchDog.cancelRenewal(fullKey);
        if (deleted != null && deleted) {
          successCount++;
        } else {
          failCount++;
          failedKeys.add(key);
        }
      } catch (Exception e) {
        failCount++;
        failedKeys.add(key);
        log.warn("[ydsz-lock] [admin] 批量释放锁异常 key={} cause={}", fullKey, e.getMessage());
      }
    }

    Map<String, Object> result = new HashMap<>(16);
    result.put("totalRequested", keys.size());
    result.put("successCount", successCount);
    result.put("failCount", failCount);
    result.put("failedKeys", failedKeys);

    log.warn(
        "[ydsz-lock] [admin] 批量强制释放锁 total={} success={} fail={}",
        keys.size(),
        successCount,
        failCount);
    return result;
  }

  /**
   * 单次扫描指定数量的锁 key（使用 SCAN 命令渐进式遍历）
   *
   * @param pattern 搜索模式
   * @param batchSize 本批次的扫描数量上限
   * @return 本批次扫描到的锁 key 列表
   */
  private List<String> scanKeysBatch(String pattern, int batchSize) {
    List<String> keys = new ArrayList<>(16);
    ScanOptions options = ScanOptions.scanOptions().match(pattern).count(batchSize).build();
    try (Cursor<String> cursor = redisTemplate.scan(options)) {
      while (cursor.hasNext() && keys.size() < batchSize) {
        keys.add(cursor.next());
      }
    } catch (Exception e) {
      log.warn(
          "[ydsz-lock] [admin] SCAN 遍历异常 pattern={} cause={}", pattern, e.getMessage());
    }
    return keys;
  }

  /**
   * 构建锁信息 Map（TTL、看门狗状态）
   *
   * @param redisKey 锁键
   * @return 锁信息 Map
   */
  private Map<String, Object> buildLockInfo(String redisKey) {
    Map<String, Object> info = new HashMap<>(16);
    info.put("key", redisKey);
    try {
      Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
      info.put("ttlMs", ttl != null ? ttl : -1);
      info.put("watched", lockWatchDog.isWatching(redisKey));
      WatchTask task = lockWatchDog.getActiveTasksSnapshot().get(redisKey);
      if (task != null) {
        info.put("renewCount", task.getRenewCount());
        info.put("lockType", task.getLockType());
      }
    } catch (Exception e) {
      log.warn(
          "[ydsz-lock] [admin] 构建锁信息异常 key={} cause={}", redisKey, e.getMessage());
    }
    return info;
  }

  /**
   * 提取锁键的类别前缀（冒号前的第一段）
   *
   * @param redisKey 锁键
   * @return 类别字符串
   */
  private String extractCategory(String redisKey) {
    int colonIndex = redisKey.indexOf(':');
    return colonIndex > 0 ? redisKey.substring(0, colonIndex) : redisKey;
  }

  /**
   * 构建指标快照子 Map
   *
   * @return 指标快照
   */
  private Map<String, Object> buildMetricsSnapshot() {
    Map<String, Object> snapshot = new HashMap<>(16);
    snapshot.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
    snapshot.put("acquireFailCount", lockMetrics.getAcquireFailCount());
    snapshot.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
    snapshot.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
    snapshot.put("activeLocks", lockMetrics.getActiveLocks());
    return snapshot;
  }
}
