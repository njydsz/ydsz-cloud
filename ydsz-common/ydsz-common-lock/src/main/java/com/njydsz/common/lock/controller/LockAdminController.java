package com.njydsz.common.lock.controller;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import com.njydsz.common.lock.scheduler.LockWatchDog.WatchTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分布式锁运维管理控制器
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
 * <p><b>安全注意：</b>本控制器应仅内网访问或通过网关配置访问控制， 避免外部调用 {@code force-unlock} 导致数据不一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/lock")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnBean(StringRedisTemplate.class)
@Tag(name = "锁运维管理", description = "分布式锁运行时管理与死锁恢复")
public class LockAdminController {

  private static final String LOCK_KEY_PREFIX = "lock:";

  /** 默认分页大小 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 最大分页大小（防止单次查询数据量过大） */
  private static final int MAX_PAGE_SIZE = 100;

  /** 默认扫描批次大小 */
  private static final int SCAN_BATCH_SIZE = 50;

  private final StringRedisTemplate redisTemplate;
  private final LockMetrics lockMetrics;
  private final LockWatchDog lockWatchDog;

  public LockAdminController(
      StringRedisTemplate redisTemplate, LockMetrics lockMetrics, LockWatchDog lockWatchDog) {
    this.redisTemplate = redisTemplate;
    this.lockMetrics = lockMetrics;
    this.lockWatchDog = lockWatchDog;
  }

  /**
   * 获取锁运行时指标快照
   *
   * @return 包含各项指标的 Map
   */
  @GetMapping("/metrics")
  @Operation(summary = "获取锁指标", description = "获取当前锁子系统的运行时指标快照")
  public BaseResponse<Map<String, Object>> metrics() {
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
    metrics.put("acquireFailCount", lockMetrics.getAcquireFailCount());
    metrics.put("releaseCount", lockMetrics.getReleaseCount());
    metrics.put("averageWaitTimeMs", String.format("%.2f", lockMetrics.getAverageWaitTimeMillis()));
    metrics.put("averageHoldTimeMs", String.format("%.2f", lockMetrics.getAverageHoldTimeMillis()));
    metrics.put("competitionCount", lockMetrics.getCompetitionCount());
    metrics.put("activeLocks", lockMetrics.getActiveLocks());
    metrics.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
    metrics.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
    metrics.put("idempotentHitCount", lockMetrics.getIdempotentHitCount());
    metrics.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
    log.info("[ydsz-lock] [admin] 查询锁指标 active={}", lockMetrics.getActiveLocks());
    return BaseResponse.success(metrics);
  }

  /**
   * 查询指定锁的状态
   *
   * @param key 锁 key（不含前缀）
   * @return 锁状态信息
   */
  @GetMapping("/status/{key}")
  @Operation(summary = "查询锁状态", description = "查询指定 key 的锁是否被持有及剩余时间")
  public BaseResponse<Map<String, Object>> lockStatus(
      @Parameter(description = "锁 key（不含 ydsz 前缀）") @PathVariable("key") String key) {
    String fullKey = LOCK_KEY_PREFIX + key;
    Map<String, Object> status = new HashMap<>();
    status.put("key", fullKey);
    Boolean exists = redisTemplate.hasKey(fullKey);
    status.put("exists", exists != null && exists);
    if (Boolean.TRUE.equals(exists)) {
      Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.MILLISECONDS);
      status.put("ttlMs", ttl);
    }
    log.debug("[ydsz-lock] [admin] 查询锁状态 key={} exists={}", fullKey, exists);
    return BaseResponse.success(status);
  }

  /**
   * 强制释放指定锁（死锁恢复用）
   *
   * <p><b>警告：</b>强制释放可能导致多节点并发访问同一资源， 请在确认锁持有者已宕机或业务已安全的情况下使用。
   *
   * @param key 锁 key（不含前缀）
   * @return 操作结果
   */
  @DeleteMapping("/force-unlock/{key}")
  @Operation(summary = "强制释放锁", description = "紧急情况下强制释放指定锁（死锁恢复，需确认原持有者已安全）")
  public BaseResponse<Map<String, Object>> forceUnlock(
      @Parameter(description = "锁 key（不含 ydsz 前缀）") @PathVariable("key") String key) {
    String fullKey = LOCK_KEY_PREFIX + key;
    Map<String, Object> result = new HashMap<>();
    result.put("key", fullKey);
    Boolean deleted = redisTemplate.delete(fullKey);
    boolean success = deleted != null && deleted;
    result.put("released", success);
    // 同时停止看门狗续期任务
    lockWatchDog.cancelRenewal(fullKey);
    log.warn("[ydsz-lock] [admin] 强制释放锁 key={} success={}", fullKey, success);
    return BaseResponse.success(result);
  }

  /**
   * 列出活跃续期任务
   *
   * @return 当前活跃的 WatchDog 续期任务数
   */
  @GetMapping("/watchdog/tasks")
  @Operation(summary = "查看活跃续期任务", description = "列出当前 WatchDog 正在续期的锁任务数")
  public BaseResponse<Map<String, Object>> activeWatchdogTasks() {
    Map<String, Object> info = new HashMap<>();
    info.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
    info.put("timestamp", System.currentTimeMillis());
    return BaseResponse.success(info);
  }

  /**
   * 根据模式搜索锁 key
   *
   * @param pattern 搜索模式（如 "order:*"，使用 Redis KEYS 语法）
   * @return 匹配的锁 key 列表
   */
  @GetMapping("/search")
  @Operation(summary = "搜索锁 key", description = "按前缀模式搜索当前持有的锁（谨慎使用，大数据量时影响 Redis 性能）")
  public BaseResponse<Set<String>> searchKeys(
      @Parameter(description = "Redis key 模式，如 'lock:order:*'")
          @RequestParam(value = "pattern", defaultValue = "lock:*")
          String pattern) {
    Set<String> keys = redisTemplate.keys(pattern);
    log.debug(
        "[ydsz-lock] [admin] 搜索锁 key pattern={} count={}", pattern, keys == null ? 0 : keys.size());
    return BaseResponse.success(keys == null ? Collections.emptySet() : keys);
  }

  /**
   * 获取活跃锁分页列表（基于 SCAN 游标，避免 KEYS 阻塞 Redis）
   *
   * <p>使用 Redis SCAN 渐进式遍历，对 Redis 性能影响远低于 KEYS 命令。 返回当前持有锁的详细信息：锁键、TTL、看门狗续期状态。
   *
   * @param pattern 搜索模式（默认 "lock:*"）
   * @param page 页码（从 0 开始，默认 0）
   * @param size 每页条数（默认 20，最大 100）
   * @return 活跃锁分页结果
   */
  @GetMapping("/active")
  @Operation(summary = "获取活跃锁分页列表", description = "基于 SCAN 获取当前持有的活跃锁分页列表，含 TTL 与续期状态")
  public BaseResponse<Map<String, Object>> activeLocks(
      @Parameter(description = "Redis key 模式")
          @RequestParam(value = "pattern", defaultValue = "lock:*")
          String pattern,
      @Parameter(description = "页码（从 0 开始）") @RequestParam(value = "page", defaultValue = "0")
          int page,
      @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "20")
          int size) {
    int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int skip = Math.max(page, 0) * normalizedSize;

    List<Map<String, Object>> activeLockList = new ArrayList<>();
    int scanned = 0;

    // 使用 SCAN 渐进式遍历，每次扫描 SCAN_BATCH_SIZE 个 key
    for (int batch = 0;
        batch < MAX_PAGE_SIZE * 2 && activeLockList.size() < normalizedSize;
        batch++) {
      List<String> batchKeys = scanKeysBatch(pattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String key : batchKeys) {
        scanned++;
        if (scanned <= skip) {
          continue;
        }
        if (activeLockList.size() >= normalizedSize) {
          break;
        }
        Map<String, Object> lockInfo = buildLockInfo(key);
        activeLockList.add(lockInfo);
      }
    }

    Map<String, Object> result = new HashMap<>();
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
    return BaseResponse.success(result);
  }

  /**
   * 获取锁统计摘要（按锁类型/前缀分组）
   *
   * <p>快速统计活跃锁的分布情况：按锁键前缀段（冒号前的第一段）分组计数， 辅助运维快速定位热点锁类型。
   *
   * @param pattern 搜索模式（默认 "lock:*"）
   * @return 锁统计摘要
   */
  @GetMapping("/summary")
  @Operation(summary = "获取锁统计摘要", description = "按锁键前缀分组统计活跃锁分布、续期任务数、指标概览")
  public BaseResponse<Map<String, Object>> summary(
      @Parameter(description = "Redis key 模式")
          @RequestParam(value = "pattern", defaultValue = "lock:*")
          String pattern) {
    Map<String, Integer> categoryCount = new HashMap<>();
    int totalActive = 0;

    // 仅扫描前 200 个 key 做统计摘要（避免大库阻塞）
    int limit = 200;
    for (int batch = 0; batch < 4 && totalActive < limit; batch++) {
      List<String> batchKeys = scanKeysBatch(pattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String key : batchKeys) {
        totalActive++;
        String category = extractCategory(key);
        categoryCount.merge(category, 1, Integer::sum);
        if (totalActive >= limit) {
          break;
        }
      }
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("totalActive", totalActive);
    summary.put("categoryDistribution", categoryCount);
    summary.put("metrics", buildMetricsSnapshot());
    summary.put("watchdogActiveTasks", lockWatchDog.getActiveTaskCount());
    summary.put("timestamp", System.currentTimeMillis());

    log.info(
        "[ydsz-lock] [admin] 查询锁摘要 totalActive={} categories={}",
        totalActive,
        categoryCount.size());
    return BaseResponse.success(summary);
  }

  /**
   * 批量强制释放锁（紧急死锁恢复）
   *
   * <p><b>警告：</b>批量释放可能导致多节点并发访问同一资源， 请谨慎操作，确认所有锁持有者已安全后执行。
   *
   * @param request 批量释放请求（包含锁 key 列表，不含前缀）
   * @return 批量操作结果
   */
  @PostMapping("/batch-force-unlock")
  @Operation(summary = "批量强制释放锁", description = "紧急情况下批量强制释放指定锁列表（死锁恢复，需确认原持有者已安全）")
  public BaseResponse<Map<String, Object>> batchForceUnlock(
      @Parameter(description = "批量释放请求", required = true) @RequestBody
          Map<String, List<String>> request) {
    List<String> keys = request.get("keys");
    if (keys == null || keys.isEmpty()) {
      return BaseResponse.error("参数错误：keys 不能为空");
    }

    // 限制单次批量操作数量
    int maxBatchSize = 50;
    if (keys.size() > maxBatchSize) {
      return BaseResponse.error("单次批量释放数量超限，最多支持 " + maxBatchSize + " 个锁");
    }

    int successCount = 0;
    int failCount = 0;
    List<String> failedKeys = new ArrayList<>();

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

    Map<String, Object> result = new HashMap<>();
    result.put("totalRequested", keys.size());
    result.put("successCount", successCount);
    result.put("failCount", failCount);
    result.put("failedKeys", failedKeys);

    log.warn(
        "[ydsz-lock] [admin] 批量强制释放锁 total={} success={} fail={}",
        keys.size(),
        successCount,
        failCount);
    return BaseResponse.success(result);
  }

  /**
   * 单次扫描指定数量的锁 key（使用 SCAN 命令渐进式遍历）
   *
   * @param pattern 搜索模式
   * @param batchSize 本批次的扫描数量上限
   * @return 本批次扫描到的锁 key 列表
   */
  private List<String> scanKeysBatch(String pattern, int batchSize) {
    List<String> keys = new ArrayList<>();
    ScanOptions options = ScanOptions.scanOptions().match(pattern).count(batchSize).build();
    try (Cursor<String> cursor = redisTemplate.scan(options)) {
      while (cursor.hasNext() && keys.size() < batchSize) {
        keys.add(cursor.next());
      }
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] SCAN 遍历异常 pattern={} cause={}", pattern, e.getMessage());
    }
    return keys;
  }

  /**
   * 构建锁信息 Map（TTL、看门狗状态）
   *
   * @param key 锁键
   * @return 锁信息 Map
   */
  private Map<String, Object> buildLockInfo(String key) {
    Map<String, Object> info = new HashMap<>();
    info.put("key", key);
    try {
      Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
      info.put("ttlMs", ttl != null ? ttl : -1);
      info.put("watched", lockWatchDog.isWatching(key));
      WatchTask task = lockWatchDog.getActiveTasksSnapshot().get(key);
      if (task != null) {
        info.put("renewCount", task.getRenewCount());
        info.put("lockType", task.getLockType());
      }
    } catch (Exception e) {
      log.warn("[ydsz-lock] [admin] 构建锁信息异常 key={} cause={}", key, e.getMessage());
    }
    return info;
  }

  /**
   * 提取锁键的类别前缀（冒号前的第一段）
   *
   * @param key 锁键
   * @return 类别字符串
   */
  private String extractCategory(String key) {
    int colonIndex = key.indexOf(':');
    return colonIndex > 0 ? key.substring(0, colonIndex) : key;
  }

  /**
   * 构建指标快照子 Map
   *
   * @return 指标快照
   */
  private Map<String, Object> buildMetricsSnapshot() {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
    snapshot.put("acquireFailCount", lockMetrics.getAcquireFailCount());
    snapshot.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
    snapshot.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
    snapshot.put("activeLocks", lockMetrics.getActiveLocks());
    return snapshot;
  }
}
