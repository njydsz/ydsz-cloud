package com.njydsz.pmis.common.seata.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.pmis.common.seata.api.TccBranchStatus;
import com.njydsz.pmis.common.seata.api.TccTransactionLog;
import com.njydsz.pmis.common.seata.api.TccTransactionLogStore;
import com.njydsz.pmis.common.seata.config.SeataProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * TCC 事务恢复扫描器
 *
 * <p>定时扫描超时未完成的 TCC 分支事务，重新执行 Confirm 或 Cancel。
 *
 * <p><b>P0-11</b>：解决 JVM 崩溃后 Confirm/Cancel 未执行的问题。
 * <p><b>P0-12</b>：失败重试通过恢复扫描器周期性重试。
 *
 * <p>扫描逻辑：
 * <ol>
 *   <li>查询所有 {@code TRIED} 状态且超时的分支事务</li>
 *   <li>根据全局事务状态决定执行 Confirm 还是 Cancel（当前实现中，超时未 Confirm 默认触发 Cancel）</li>
 *   <li>更新重试计数，超过最大重试次数则标记为终态</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class TccTransactionRecoveryScanner {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionRecoveryScanner.class);

    private final TccTransactionLogStore logStore;
    private final SeataProperties properties;
    private final TccRecoveryHandler recoveryHandler;

    /**
     * @param logStore       事务日志存储
     * @param properties     配置
     * @param recoveryHandler 恢复处理回调（由 TccTransactionManager 注册）
     */
    public TccTransactionRecoveryScanner(TccTransactionLogStore logStore,
                                          SeataProperties properties,
                                          TccRecoveryHandler recoveryHandler) {
        this.logStore = logStore;
        this.properties = properties;
        this.recoveryHandler = recoveryHandler;
    }

    /**
     * 定时扫描超时事务
     *
     * <p>扫描间隔由 {@code pmis.seata.recovery-scan-interval-ms} 控制（默认 10s）
     */
    @Scheduled(fixedDelayString = "${pmis.seata.recovery-scan-interval-ms:10000}")
    public void scan() {
        LocalDateTime threshold = LocalDateTime.now().minusNanos(
                properties.getRecoveryTimeoutThresholdMs() * 1_000_000);
        List<TccTransactionLog> pending = logStore.findTimeoutPending(threshold);
        if (pending.isEmpty()) {
            return;
        }
        log.info("TCC recovery scan found {} pending transactions", pending.size());
        for (TccTransactionLog txLog : pending) {
            try {
                recover(txLog);
            } catch (Exception e) {
                log.error("TCC recovery failed for xid={}, branch={}", txLog.getXid(), txLog.getBranchId(), e);
            }
        }
    }

    /**
     * 恢复单个超时事务
     *
     * <p>策略：超时未 Confirm 的事务视为失败，触发 Cancel（资源释放优先）
     */
    private void recover(TccTransactionLog txLog) {
        if (txLog.getRetryCount() >= properties.getTccRetryCount()) {
            log.warn("TCC transaction exhausted retries, marking as cancelled: xid={}, branch={}, retries={}",
                    txLog.getXid(), txLog.getBranchId(), txLog.getRetryCount());
            logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), TccBranchStatus.CANCELLED);
            return;
        }

        txLog.incrementRetryCount();
        log.info("TCC recovery retry #{} for xid={}, branch={}",
                txLog.getRetryCount(), txLog.getXid(), txLog.getBranchId());

        try {
            recoveryHandler.recoverCancel(txLog);
            logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), TccBranchStatus.CANCELLED);
        } catch (Exception e) {
            log.error("TCC recovery Cancel failed: xid={}, branch={}, retry={}",
                    txLog.getXid(), txLog.getBranchId(), txLog.getRetryCount(), e);
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
        void recoverCancel(TccTransactionLog txLog) throws Exception;
    }
}
