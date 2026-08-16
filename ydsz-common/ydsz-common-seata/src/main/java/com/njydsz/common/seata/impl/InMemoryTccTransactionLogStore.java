package com.njydsz.common.seata.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.seata.api.TccBranchStatus;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;

/**
 * 内存版 TCC 事务日志存储
 *
 * <p>采用双结构优化：
 *
 * <ul>
 *   <li>{@link ConcurrentHashMap} - 主存储，支持 O(1) 的 key 查询
 *   <li>{@link ConcurrentLinkedDeque} - 时间有序索引，支持 O(k) 的过期清理（k = 过期数量）
 * </ul>
 *
 * <p><b>P2-5 修复</b>：此前的 {@code cleanupFinalStateLogs} 需要在整个 Map 上扫描（O(n)）， 在大量日志时导致 GC 压力和清理延迟。
 * 现通过双结构保证：
 *
 * <ul>
 *   <li>过期扫描只需从 Deque 头部弹出过期记录
 *   <li>查询和保存维持 O(1) 时间复杂度
 * </ul>
 *
 * <p>适用于单机、开发/测试环境。生产环境应使用 {@code RedisTccTransactionLogStore} 或 {@code DbTccTransactionLogStore}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class InMemoryTccTransactionLogStore implements TccTransactionLogStore {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryTccTransactionLogStore.class);

  /** 最大条目数，超过后清理过期日志 */
  private static final int MAX_ENTRIES = 10000;

  /** 终态日志保留时长（小时） */
  private static final long RETENTION_HOURS = 1;

  /** 主存储：key = xid:branchId, value = TccTransactionLog */
  private final ConcurrentHashMap<String, TccTransactionLog> store = new ConcurrentHashMap<>();

  /**
   * 时间有序索引：按 finishedAt 升序排列的终态日志引用
   *
   * <p>仅存储终态日志的 key，用于快速清理过期条目，避免全表扫描。
   */
  private final ConcurrentLinkedDeque<TimedKey> finalStateIndex = new ConcurrentLinkedDeque<>();

  /** 当前日志数量（原子计数器，避免 ConcurrentHashMap.size() 的 O(n) 开销） */
  private final AtomicLong sizeCounter = new AtomicLong(0);

  /** 带时间戳的键引用，用于过期索引 */
  private static class TimedKey {
    final String key;
    final LocalDateTime finishedAt;

    TimedKey(String key, LocalDateTime finishedAt) {
      this.key = key;
      this.finishedAt = finishedAt;
    }
  }

  /**
   * 生成存储键
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @return 存储键（格式：xid:branchId）
   */
  private static String key(String xid, String branchId) {
    return xid + ":" + branchId;
  }

  /**
   * 保存事务日志（Try 前调用）
   *
   * @param txLog 事务日志
   */
  @Override
  public void save(TccTransactionLog txLog) {
    if (sizeCounter.get() >= MAX_ENTRIES) {
      cleanupExpiredLogs();
    }
    String k = key(txLog.getXid(), txLog.getBranchId());
    store.put(k, txLog);
    sizeCounter.incrementAndGet();
  }

  /**
   * 更新分支事务状态
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @param status 新状态
   */
  @Override
  public void updateStatus(String xid, String branchId, TccBranchStatus status) {
    TccTransactionLog logEntry = store.get(key(xid, branchId));
    if (logEntry != null) {
      logEntry.setStatus(status);
      if (status.isFinal()) {
        LocalDateTime now = LocalDateTime.now();
        logEntry.setFinishedAt(now);
        // 加入终态索引（有序插入，过期时从头部弹出）
        finalStateIndex.addLast(new TimedKey(key(xid, branchId), now));
      }
    }
  }

  /**
   * 根据 XID 和分支 ID 查询事务日志
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   * @return 事务日志（Optional）
   */
  @Override
  public Optional<TccTransactionLog> findByXidAndBranchId(String xid, String branchId) {
    return Optional.ofNullable(store.get(key(xid, branchId)));
  }

  /**
   * 查询超时未完成的分支事务（用于恢复扫描）
   *
   * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
   * @return 超时分支列表
   */
  @Override
  public List<TccTransactionLog> findTimeoutPending(LocalDateTime threshold) {
    return store.values().stream()
        .filter(log -> log.getStatus() == TccBranchStatus.TRIED)
        .filter(
            log -> log.getTryCompletedAt() != null && log.getTryCompletedAt().isBefore(threshold))
        .collect(Collectors.toList());
  }

  /**
   * 分页查询超时未完成的分支事务（返回前 limit 条）
   *
   * @param threshold 超时阈值
   * @param limit 单次返回最大记录数
   * @return 超时分支列表
   */
  @Override
  public List<TccTransactionLog> findTimeoutPendingPaged(LocalDateTime threshold, int limit) {
    return store.values().stream()
        .filter(log -> log.getStatus() == TccBranchStatus.TRIED)
        .filter(
            log -> log.getTryCompletedAt() != null && log.getTryCompletedAt().isBefore(threshold))
        .limit(limit)
        .collect(Collectors.toList());
  }

  /**
   * 查询超时未完成的分支事务数量（高效计数，不加载完整日志）
   *
   * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
   * @return 超时未完成的分支事务数量
   */
  @Override
  public long countTimeoutPending(LocalDateTime threshold) {
    return store.values().stream()
        .filter(log -> log.getStatus() == TccBranchStatus.TRIED)
        .filter(
            log -> log.getTryCompletedAt() != null && log.getTryCompletedAt().isBefore(threshold))
        .count();
  }

  /**
   * 删除事务日志
   *
   * @param xid 全局事务 ID
   * @param branchId 分支事务 ID
   */
  @Override
  public void delete(String xid, String branchId) {
    String k = key(xid, branchId);
    TccTransactionLog removed = store.remove(k);
    if (removed != null) {
      sizeCounter.decrementAndGet();
      // 注意：Deque 中的条留待惰性清理（从头部弹出时发现 key 不存在则跳过）
    }
  }

  /**
   * 清理过期的事务日志（超过保留时间的终态日志）
   *
   * <p>仅扫描终态索引的头部，时间复杂度 O(k)（k = 过期条数）， 相比全表扫描 O(n) 大幅提升。
   */
  public void cleanupExpiredLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
    TimedKey head;
    int removed = 0;
    while ((head = finalStateIndex.peekFirst()) != null) {
      if (head.finishedAt.isAfter(cutoff)) {
        break; // 后面的都不会过期了（Deque 按 finishedAt 有序）
      }
      // 尝试从 Deque 移除（CAS 操作）
      if (finalStateIndex.pollFirst() != null) {
        // 从主存储移除（如果还在）
        if (store.remove(head.key) != null) {
          sizeCounter.decrementAndGet();
          removed++;
        }
      }
    }
    if (removed > 0) {
      LOG.debug("Cleaned up {} expired final-state TCC logs", removed);
    }
  }
}
