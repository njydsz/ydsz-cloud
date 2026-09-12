package com.njydsz.system.web.controller;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import com.njydsz.common.lock.admin.DistributedLockAdmin;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import com.njydsz.common.lock.strategy.LockStrategy;

/**
 * 分布式锁运维管理端点（ydsz-system-web）
 *
 * <p>通过 Spring Boot Actuator 暴露锁运维操作，复用系统模块已有的管理端口（management port）隔离能力。
 * 端点 ID 为 {@code lock}，所有操作路径前缀 {@code /actuator/lock}，
 * 与 {@code ConfigRegistryEndpoint} 等运维端点同级。
 *
 * <p>替代原来位于 ydzs-common-lock（L4）中的 {@code LockAdminController}（{@code @RestController}），
 * 符合云顶编码规范 §22.2 层级定位：L4 基础数据层不持有 Web 层组件，
 * Controller 属于业务主应用模块（*-web）的职责。
 *
 * <p><b>改造说明（26.09.08）：</b>所有 Redis 操作收敛到 {@link DistributedLockAdmin}，
 * Controller 不再直接注入 {@code StringRedisTemplate}，避免 Controller 层绕过 common-lock 模块直接操作 Redis。
 *
 * <h3>端点 URL 示例</h3>
 * <ul>
 *   <li>{@code GET    /actuator/lock}               — 锁指标快照（acquire/release/competition/timeout/watchdog 等）
 *   <li>{@code GET    /actuator/lock/status/{key}}   — 指定锁状态（exists / ttlMs）
 *   <li>{@code GET    /actuator/lock/active}         — 活跃锁分页列表（pattern/page/size）
 *   <li>{@code GET    /actuator/lock/search}         — 按模式搜索锁 key
 *   <li>{@code GET    /actuator/lock/summary}        — 锁统计摘要（按类别分组 + 指标）
 *   <li>{@code GET    /actuator/lock/watchdog}       — 活跃续期任务数
 *   <li>{@code DELETE /actuator/lock/{key}}          — 强制释放指定锁（死锁恢复）
 *   <li>{@code POST   /actuator/lock/batch-force-unlock} — 批量强制释放锁
 * </ul>
 *
 * <p><b>安全注意：</b>force-unlock / batch-force-unlock 为高危操作，
 * 建议通过 management.port 隔离 + 网关 ACL 控制，仅内网运维访问。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see LockMetrics
 * @see LockWatchDog
 * @see DistributedLockAdmin
 */
@Slf4j
@Endpoint(id = "lock")
public class LockAdminController {

  private static final String LOCK_KEY_PREFIX = "lock:";

  /** 默认分页大小 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 最大分页大小（防止单次查询数据量过大） */
  private static final int MAX_PAGE_SIZE = 100;

  /** 单次 SCAN 批次大小 */
  private static final int SCAN_BATCH_SIZE = 50;

  /** 摘要扫描上限（避免大库阻塞） */
  private static final int SUMMARY_SCAN_LIMIT = 200;

  /** HashMap/ArrayList 初始容量（YDIZ-COLL-001，同时避免 Checkstyle MagicNumber 告警） */
  private static final int COLLECTION_INIT_CAPACITY = 16;

  /** 单次批量释放上限 */
  private static final int BATCH_FORCE_UNLOCK_LIMIT = 50;

  private final LockMetrics lockMetrics;
  private final LockWatchDog lockWatchDog;

  /** 分布式锁运维管理实例（由 common-lock 模块提供，收敛所有 Redis 操作） */
  private final DistributedLockAdmin lockAdmin;

  public LockAdminController(
      LockMetrics lockMetrics,
      LockWatchDog lockWatchDog,
      LockStrategy lockStrategy) {
    this.lockMetrics = lockMetrics;
    this.lockWatchDog = lockWatchDog;
    this.lockAdmin = lockStrategy.getLockAdmin();
  }

  /**
   * 获取锁运行时指标快照
   *
   * @return 包含获取/释放/竞争/超时/续期等指标的 Map
   */
  @ReadOperation
  public Map<String, Object> metrics() {
    Map<String, Object> result = new HashMap<>(COLLECTION_INIT_CAPACITY);
    result.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
    result.put("acquireFailCount", lockMetrics.getAcquireFailCount());
    result.put("releaseCount", lockMetrics.getReleaseCount());
    result.put(
        "averageWaitTimeMs",
        lockMetrics
            .getAverageWaitTimeMillis()
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString());
    result.put(
        "averageHoldTimeMs",
        lockMetrics
            .getAverageHoldTimeMillis()
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString());
    result.put("competitionCount", lockMetrics.getCompetitionCount());
    result.put("activeLocks", lockMetrics.getActiveLocks());
    result.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
    result.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
    result.put("idempotentHitCount", lockMetrics.getIdempotentHitCount());
    result.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
    log.info("[ydsz-lock] [admin] 查询锁指标 active={}", lockMetrics.getActiveLocks());
    return result;
  }

  /**
   * 获取活跃锁分页列表（基于 Redis SCAN 渐进式遍历）
   *
   * @param pattern 搜索模式（默认 "lock:*"）
   * @param page    页码（从 0 开始，默认 0）
   * @param size    每页条数（默认 20，最大 100）
   * @return 分页结果（locks / page / size / scanned / hasMore / timestamp）
   */
  @ReadOperation
  public Map<String, Object> activeLocks(
      @Selector String pattern,
      @Selector int page,
      @Selector int size) {
    String effectivePattern = (pattern == null || pattern.isEmpty()) ? "lock:*" : pattern;
    int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int skip = Math.max(page, 0) * normalizedSize;

    List<Map<String, Object>> lockList = new ArrayList<>(COLLECTION_INIT_CAPACITY);
    int scanned = 0;

    for (int batch = 0;
        batch < MAX_PAGE_SIZE * 2 && lockList.size() < normalizedSize;
        batch++) {
      List<String> batchKeys = lockAdmin.scanKeys(effectivePattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String redisKey : batchKeys) {
        scanned++;
        if (scanned <= skip) {
          continue;
        }
        if (lockList.size() >= normalizedSize) {
          break;
        }
        lockList.add(buildLockInfo(redisKey));
      }
    }

    Map<String, Object> result = new HashMap<>(COLLECTION_INIT_CAPACITY);
    result.put("locks", lockList);
    result.put("page", page);
    result.put("size", normalizedSize);
    result.put("scanned", scanned);
    result.put("hasMore", lockList.size() >= normalizedSize);
    result.put("timestamp", System.currentTimeMillis());

    log.info(
        "[ydsz-lock] [admin] 查询活跃锁 page={} size={} count={}",
        page,
        normalizedSize,
        lockList.size());
    return result;
  }

  /**
   * 查询指定锁的状态
   *
   * @param key 锁 key（不含前缀 "lock:"）
   * @return 锁状态（exists / ttlMs）
   */
  @ReadOperation
  public Map<String, Object> lockStatus(@Selector String key) {
    String fullKey = LOCK_KEY_PREFIX + key;
    Map<String, Object> status = new HashMap<>(COLLECTION_INIT_CAPACITY);
    status.put("key", fullKey);
    boolean exists = lockAdmin.exists(fullKey);
    status.put("exists", exists);
    if (exists) {
      long ttl = lockAdmin.getTtl(fullKey);
      if (ttl >= 0) {
        status.put("ttlMs", ttl);
      }
    }
    log.debug("[ydsz-lock] [admin] 查询锁状态 key={} exists={}", fullKey, exists);
    return status;
  }

  /**
   * 强制释放指定锁（死锁恢复）
   *
   * <p><b>警告：</b>强制释放可能导致多节点并发访问同一资源，
   * 请在确认锁持有者已宕机或业务已安全的情况下使用。
   *
   * @param key 锁 key（不含前缀）
   * @return 操作结果（released）
   */
  @DeleteOperation
  public Map<String, Object> forceUnlock(@Selector String key) {
    String fullKey = LOCK_KEY_PREFIX + key;
    Map<String, Object> result = new HashMap<>(COLLECTION_INIT_CAPACITY);
    result.put("key", fullKey);
    boolean released = lockAdmin.forceUnlock(fullKey);
    result.put("released", released);
    log.warn("[ydsz-lock] [admin] 强制释放锁 key={} success={}", fullKey, released);
    return result;
  }

  /**
   * 按模式搜索锁 key
   *
   * @param pattern 搜索模式（默认 "lock:*"，使用 Redis KEYS 语法）
   * @return 匹配的锁 key 集合
   */
  @ReadOperation
  public Set<String> searchKeys(@Selector String pattern) {
    String effectivePattern = (pattern == null || pattern.isEmpty()) ? "lock:*" : pattern;
    Set<String> keys = lockAdmin.searchKeys(effectivePattern);
    log.debug(
        "[ydsz-lock] [admin] 搜索锁 key pattern={} count={}",
        effectivePattern,
        keys == null ? 0 : keys.size());
    return keys == null ? Collections.emptySet() : keys;
  }

  /**
   * 获取锁统计摘要（按类别分组 + 指标快照 + WatchDog 任务数）
   *
   * <p>扫描活跃锁并按冒号前第一段分类计数，辅助运维快速定位热点锁类型。
   * 扫描上限为 {@value #SUMMARY_SCAN_LIMIT} 个 key，避免对大集群造成阻塞。
   *
   * @param pattern 搜索模式（默认 "lock:*"）
   * @return 锁统计摘要
   */
  @ReadOperation
  public Map<String, Object> summary(@Selector String pattern) {
    String effectivePattern = (pattern == null || pattern.isEmpty()) ? "lock:*" : pattern;
    Map<String, Integer> categoryCount = new HashMap<>(COLLECTION_INIT_CAPACITY);
    int totalActive = 0;

    for (int batch = 0;
        batch < SUMMARY_SCAN_LIMIT / SCAN_BATCH_SIZE && totalActive < SUMMARY_SCAN_LIMIT;
        batch++) {
      List<String> batchKeys = lockAdmin.scanKeys(effectivePattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String redisKey : batchKeys) {
        totalActive++;
        String category = extractCategory(redisKey);
        categoryCount.merge(category, 1, Integer::sum);
        if (totalActive >= SUMMARY_SCAN_LIMIT) {
          break;
        }
      }
    }

    Map<String, Object> result = new HashMap<>(COLLECTION_INIT_CAPACITY);
    result.put("totalActive", totalActive);
    result.put("categoryDistribution", categoryCount);
    result.put("metrics", buildMetricsSnapshot());
    result.put("watchdogActiveTasks", lockWatchDog.getActiveTaskCount());
    result.put("timestamp", System.currentTimeMillis());

    log.info(
        "[ydsz-lock] [admin] 查询锁摘要 totalActive={} categories={}",
        totalActive,
        categoryCount.size());
    return result;
  }

  /**
   * 获取活跃看门狗续期任务数
   *
   * @return activeRenewalTasks 与 timestamp
   */
  @ReadOperation
  public Map<String, Object> activeWatchdogTasks() {
    Map<String, Object> result = new HashMap<>(COLLECTION_INIT_CAPACITY);
    result.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
    result.put("timestamp", System.currentTimeMillis());
    return result;
  }

  /**
   * 批量强制释放锁（紧急死锁恢复）
   *
   * <p><b>警告：</b>批量释放可能导致多节点并发访问同一资源，
   * 请谨慎操作，确认所有锁持有者已安全后执行。
   * 单次上限 {@value #BATCH_FORCE_UNLOCK_LIMIT} 个 key。
   *
   * @param keys 锁 key 列表（不含前缀 "lock:"）
   * @return 批量操作结果（totalRequested / successCount / failCount / failedKeys）
   */
  @WriteOperation
  public Map<String, Object> batchForceUnlock(List<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return buildErrorMap("参数错误：keys 不能为空");
    }

    if (keys.size() > BATCH_FORCE_UNLOCK_LIMIT) {
      return buildErrorMap("单次批量释放数量超限，最多支持 " + BATCH_FORCE_UNLOCK_LIMIT + " 个锁");
    }

    int successCount = 0;
    int failCount = 0;
    List<String> failedKeys = new ArrayList<>(COLLECTION_INIT_CAPACITY);

    for (String key : keys) {
      String fullKey = LOCK_KEY_PREFIX + key;
      try {
        boolean released = lockAdmin.forceUnlock(fullKey);
        if (released) {
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

    Map<String, Object> result = new HashMap<>(COLLECTION_INIT_CAPACITY);
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

  // ---------------------------------------------------------------------------
  // 私有方法
  // ---------------------------------------------------------------------------

  /**
   * 构建错误响应 Map
   *
   * @param message 错误消息
   * @return 包含 error key 的 Map
   */
  private static Map<String, Object> buildErrorMap(String message) {
    Map<String, Object> errorMap = new HashMap<>(2);
    errorMap.put("error", message);
    return errorMap;
  }

  /**
   * 构建锁信息 Map（TTL、看门狗状态、续期次数、锁类型）
   */
  private Map<String, Object> buildLockInfo(String redisKey) {
    Map<String, Object> info = new HashMap<>(COLLECTION_INIT_CAPACITY);
    info.put("key", redisKey);
    try {
      long ttl = lockAdmin.getTtl(redisKey);
      info.put("ttlMs", ttl >= 0 ? ttl : -1);
      info.put("watched", lockWatchDog.isWatching(redisKey));
      LockWatchDog.WatchTask task = lockWatchDog.getActiveTasksSnapshot().get(redisKey);
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
   */
  private String extractCategory(String redisKey) {
    int colonIndex = redisKey.indexOf(':');
    return colonIndex > 0 ? redisKey.substring(0, colonIndex) : redisKey;
  }

  /**
   * 构建指标快照子 Map
   */
  private Map<String, Object> buildMetricsSnapshot() {
    Map<String, Object> snapshot = new HashMap<>(COLLECTION_INIT_CAPACITY);
    snapshot.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
    snapshot.put("acquireFailCount", lockMetrics.getAcquireFailCount());
    snapshot.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
    snapshot.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
    snapshot.put("activeLocks", lockMetrics.getActiveLocks());
    return snapshot;
  }
}

