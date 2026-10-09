package com.njydsz.system.web.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.admin.DistributedLockAdmin;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.system.domain.query.LockPageQuery;
import com.njydsz.system.domain.vo.LockStatsVO;
import com.njydsz.system.domain.vo.LockVO;

/**
 * 分布式锁管理 REST 控制器（ydsz-system-web）。
 *
 * <p>为前端 ydsz-micro 的 system-web 子应用提供标准 REST API，复用运维端口已有的 Actuator 端点能力
 * （基于 Redis SCAN + WatchDog 状态），提供锁分页列表、统计快照、强制释放等能力。
 *
 * <p>与 {@link LockAdminController}（Actuator 端点）的关系：功能对齐，但暴露为 {@code @RestController}，
 * 走网关统一鉴权（非 management port 隔离），前端可直接调用。
 *
 * <h3>端点 URL</h3>
 * <ul>
 *   <li>{@code GET    /system/lock/page}      — 活跃锁分页列表（支持 lockKey/owner 筛选）
 *   <li>{@code GET    /system/lock/stats}     — 锁统计快照（活跃数/超时数/持有者分布）
 *   <li>{@code DELETE /system/lock/{lockKey}}  — 强制释放指定锁（死锁恢复）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.09
 * @see LockAdminController
 */
@Slf4j
@ApiVersion("26.10.09")
@RestController
@RequestMapping("/system/lock")
public class LockAdminRestController {

  /** 默认分页大小 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 最大分页大小（防止单次查询数据量过大） */
  private static final int MAX_PAGE_SIZE = 100;

  /** 单次 SCAN 批次大小 */
  private static final int SCAN_BATCH_SIZE = 50;

  /** 锁 key 前缀 */
  private static final String LOCK_KEY_PREFIX = "lock:";

  /** 单次批量释放上限 */
  private static final int BATCH_FORCE_UNLOCK_LIMIT = 50;

  private final DistributedLockAdmin lockAdmin;
  private final LockMetrics lockMetrics;

  public LockAdminRestController(LockStrategy lockStrategy, LockMetrics lockMetrics) {
    this.lockAdmin = lockStrategy.getLockAdmin();
    this.lockMetrics = lockMetrics;
  }

  /**
   * 获取活跃锁分页列表。
   *
   * <p>基于 Redis SCAN 渐进式遍历，支持 lockKey 模糊匹配和 owner 筛选。
   *
   * @param query 分页 + 筛选参数
   * @return 分页锁列表
   */
  @GetMapping("/page")
  public YdszResponse<LockVO[]> page(LockPageQuery query) {
    int pageNum = query.getPageNum() != null ? query.getPageNum() : 1;
    int pageSize = query.getPageSize() != null ? query.getPageSize() : DEFAULT_PAGE_SIZE;
    pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    int skip = (Math.max(pageNum, 1) - 1) * pageSize;

    String pattern = (query.getLockKey() != null && !query.getLockKey().isEmpty())
        ? "lock:*" + query.getLockKey() + "*"
        : "lock:*";

    List<LockVO> lockList = new ArrayList<>();
    for (int batch = 0;
        batch < MAX_PAGE_SIZE * 2 && lockList.size() < pageSize;
        batch++) {
      List<String> batchKeys = lockAdmin.scanKeys(pattern, SCAN_BATCH_SIZE);
      if (batchKeys.isEmpty()) {
        break;
      }
      for (String redisKey : batchKeys) {
        if (skip > 0) {
          skip--;
          continue;
        }
        if (lockList.size() >= pageSize) {
          break;
        }
        lockList.add(buildLockVO(redisKey));
      }
    }

    log.info("[LockAdminRest] 查询锁分页 page={} size={} count={}",
        pageNum, pageSize, lockList.size());
    return PageResponse.success(
        (long) lockMetrics.getActiveLocks(),
        (long) pageNum,
        (long) pageSize,
        lockList.toArray(new LockVO[0]));
  }

  /**
   * 获取锁统计快照（活跃数/超时数/持有者分布）。
   *
   * @return 锁统计信息
   */
  @GetMapping("/stats")
  public YdszResponse<LockStatsVO> stats() {
    LockStatsVO vo = new LockStatsVO();
    vo.setActiveLockCount(lockMetrics.getActiveLocks());
    vo.setExpiredCount((int) lockMetrics.getLockTimeoutCount());
    // 基于持有者前缀做简单统计（按冒号前第一段分类）
    Map<String, Integer> ownerDist = new HashMap<>();
    List<String> keys = lockAdmin.scanKeys("lock:*", SCAN_BATCH_SIZE * 2);
    for (String key : keys) {
      String category = extractCategory(key);
      ownerDist.merge(category, 1, Integer::sum);
      if (ownerDist.size() >= 10) {
        break;
      }
    }
    vo.setOwnerDistribution(ownerDist);
    log.info("[LockAdminRest] 查询锁统计 active={}", vo.getActiveLockCount());
    return YdszResponse.success(vo);
  }

  /**
   * 强制释放指定锁（死锁恢复）。
   *
   * @param lockKey 锁 key（不含 "lock:" 前缀）
   * @return 是否释放成功
   */
  @DeleteMapping("/{lockKey}")
  public YdszResponse<Boolean> release(@PathVariable String lockKey) {
    String fullKey = LOCK_KEY_PREFIX + lockKey;
    boolean released = lockAdmin.forceUnlock(fullKey);
    log.warn("[LockAdminRest] 强制释放锁 key={} success={}", fullKey, released);
    return YdszResponse.success(released);
  }

  // ---------------------------------------------------------------------------
  // 私有方法
  // ---------------------------------------------------------------------------

  /**
   * 从 Redis key 构建前端 VO
   */
  private LockVO buildLockVO(String redisKey) {
    LockVO vo = new LockVO();
    vo.setLockKey(redisKey.replace(LOCK_KEY_PREFIX, ""));
    vo.setRemainingTtlMs(lockAdmin.getTtl(redisKey));
    vo.setOwner(extractCategory(redisKey));
    return vo;
  }

  /**
   * 提取锁键的类别前缀（冒号前的第一段）
   */
  private String extractCategory(String redisKey) {
    int colonIndex = redisKey.indexOf(':');
    return colonIndex > 0 ? redisKey.substring(0, colonIndex) : redisKey;
  }
}
