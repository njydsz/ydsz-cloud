package com.njydsz.common.seata.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.seata.api.TccBranchStatus;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;
import com.njydsz.common.seata.config.SeataProperties;

/**
 * TCC 事务恢复扫描器
 *
 * <p>定时扫描超时未完成的 TCC 分支事务，重新执行 Cancel。
 *
 * <p><b>P0-11</b>：解决 JVM 崩溃后 Confirm/Cancel 未执行的问题。
 *
 * <p><b>P0-12</b>：失败重试通过恢复扫描器周期性重试。
 *
 * <p><b>P1-2 修复</b>：支持分页模式，每次扫描仅处理 {@code recoveryBatchSize} 条记录， 避免一次性加载全部超时事务导致内存溢出和长时间停顿。
 *
 * <p>扫描逻辑：
 *
 * <ol>
 *   <li>查询超时未完成的 TCC 分支事务（分页）
 *   <li>根据全局事务状态决定执行 Confirm 还是 Cancel（当前实现中，超时未 Confirm 默认触发 Cancel）
 *   <li>更新重试计数，超过最大重试次数则标记为终态
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TccTransactionRecoveryScanner {

  private static final Logger LOG = LoggerFactory.getLogger(TccTransactionRecoveryScanner.class);

  /** 每毫秒对应的纳秒数（1 ms = 1,000,000 ns）。 */
  private static final long NANOS_PER_MS = 1_000_000L;

  private final TccTransactionLogStore logStore;
  private final SeataProperties properties;
  private final TccRecoveryHandler recoveryHandler;

  /**
   * 构造 TCC 事务恢复扫描器
   *
   * @param logStore 事务日志存储
   * @param properties 配置
   * @param recoveryHandler 恢复处理回调（由 TccTransactionManager 注册）
   */
  public TccTransactionRecoveryScanner(
      TccTransactionLogStore logStore,
      SeataProperties properties,
      TccRecoveryHandler recoveryHandler) {
    this.logStore = logStore;
    this.properties = properties;
    this.recoveryHandler = recoveryHandler;
  }

  /**
   * 定时扫描超时事务
   *
   * <p>扫描间隔由 {@code ydsz.seata.recovery-scan-interval-ms} 控制（默认 10s）。 通过 {@link
   * DistributedScheduled} 保证多节点部署时仅一个节点执行恢复，避免重复 Cancel。
   *
   * <p>当 {@code recovery-paged-mode=true} 时，每次扫描仅处理 {@code recoveryBatchSize} 条记录，
   * 下次扫描继续处理剩余记录，渐进式完成全部超时事务的恢复。
   */
  @Scheduled(fixedDelayString = "${ydsz.seata.recovery-scan-interval-ms:10000}")
  @DistributedScheduled(lockKey = "seata:tcc-recovery-scan", leaseTime = 60)
  public void scan() {
    LocalDateTime threshold =
        LocalDateTime.now().minusNanos(properties.getRecoveryTimeoutThresholdMs() * NANOS_PER_MS);

    List<TccTransactionLog> pending;
    if (properties.isRecoveryPagedMode()) {
      // 分页模式：每次仅处理 recoveryBatchSize 条
      pending = logStore.findTimeoutPendingPaged(threshold, properties.getRecoveryBatchSize());
    } else {
      // 兼容模式：全量查询（不推荐生产环境使用）
      pending = logStore.findTimeoutPending(threshold);
    }

    if (pending.isEmpty()) {
      return;
    }

    LOG.info(
        "TCC recovery scan found {} pending transactions (paged={}, batchSize={})",
        pending.size(),
        properties.isRecoveryPagedMode(),
        properties.getRecoveryBatchSize());

    int successCount = 0;
    int failCount = 0;
    for (TccTransactionLog txLog : pending) {
      try {
        recover(txLog);
        successCount++;
      } catch (Exception e) {
        failCount++;
        LOG.error(
            "TCC recovery failed for xid={}, branch={}", txLog.getXid(), txLog.getBranchId(), e);
      }
    }

    if (failCount > 0) {
      LOG.warn("TCC recovery scan completed: success={}, fail={}", successCount, failCount);
    } else {
      LOG.info("TCC recovery scan completed: success={}", successCount);
    }
  }

  /**
   * 恢复单个超时事务
   *
   * <p>策略：超时未 Confirm 的事务视为失败，触发 Cancel（资源释放优先）
   *
   * @param txLog 超时事务日志
   */
  private void recover(TccTransactionLog txLog) {
    if (txLog.getRetryCount() >= properties.getTccRetryCount()) {
      LOG.warn(
          "TCC transaction exhausted retries, marking as cancelled: xid={}, branch={}, retries={}",
          txLog.getXid(),
          txLog.getBranchId(),
          txLog.getRetryCount());
      logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), TccBranchStatus.CANCELLED);
      return;
    }

    txLog.incrementRetryCount();
    LOG.info(
        "TCC recovery retry #{} for xid={}, branch={}",
        txLog.getRetryCount(),
        txLog.getXid(),
        txLog.getBranchId());

    try {
      recoveryHandler.recoverCancel(txLog);
      logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), TccBranchStatus.CANCELLED);
    } catch (Exception e) {
      LOG.error(
          "TCC recovery Cancel failed: xid={}, branch={}, retry={}",
          txLog.getXid(),
          txLog.getBranchId(),
          txLog.getRetryCount(),
          e);
      txLog.setLastError(e.getMessage());
    }
  }

  /**
   * 恢复处理回调接口
   *
   * <p>由 {@link TccTransactionManager} 实现，提供实际执行 Confirm/Cancel 的能力。
   */
  public interface TccRecoveryHandler {
    /**
     * 恢复时执行 Cancel
     *
     * @param txLog 超时事务日志
     * @throws Exception Cancel 执行异常
     */
    // CHECKSTYLE.OFF: IllegalThrows - 接口方法统一声明 Exception，调用方已 catch 处理
    void recoverCancel(TccTransactionLog txLog) throws Exception;
  }
}
