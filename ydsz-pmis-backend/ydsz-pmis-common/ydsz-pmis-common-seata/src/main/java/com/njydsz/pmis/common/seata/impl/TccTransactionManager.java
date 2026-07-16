package com.njydsz.pmis.common.seata.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.seata.api.TccAction;
import com.njydsz.pmis.common.seata.api.TccBranchStatus;
import com.njydsz.pmis.common.seata.api.TccContext;
import com.njydsz.pmis.common.seata.api.TccTransactionLog;
import com.njydsz.pmis.common.seata.api.TccTransactionLogStore;
import com.njydsz.pmis.common.seata.api.TransactionType;
import com.njydsz.pmis.common.seata.config.SeataProperties;

/**
 * TCC 事务管理器
 *
 * <p>实现 Try-Confirm-Cancel 模式，集成事务日志解决三大经典问题。
 *
 * <p><b>P0-4 修复</b>：集成 {@link TccTransactionLogStore}，在 Try/Confirm/Cancel 前检查分支状态：
 * <ul>
 *   <li><b>悬挂保护</b>：Try 前检查状态，若已 CANCELLED 则跳过 Try</li>
 *   <li><b>空回滚保护</b>：Cancel 前检查状态，若为 INIT/TRYING 则跳过 Cancel</li>
 *   <li><b>幂等保护</b>：Confirm/Cancel 前检查是否已为终态，是则跳过</li>
 * </ul>
 *
 * <p><b>P0-12 修复</b>：Confirm/Cancel 失败时按配置重试
 * ({@code pmis.seata.tcc-retry-count} / {@code pmis.seata.tcc-retry-interval-ms})。
 *
 * <p>注意：此实现为本地 TCC 协调器，适用于单服务内的多资源操作。
 * 跨服务的 TCC 需要配合 Seata TCC 模式使用。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class TccTransactionManager extends AbstractTransactionManager
        implements TccTransactionRecoveryScanner.TccRecoveryHandler {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionManager.class);

    private final TccTransactionLogStore logStore;
    private final SeataProperties properties;

    /**
     * 无日志存储模式（向后兼容）
     */
    public TccTransactionManager() {
        this.logStore = null;
        this.properties = null;
    }

    /**
     * 带日志存储模式（推荐）
     *
     * @param logStore   事务日志存储
     * @param properties 配置
     */
    public TccTransactionManager(TccTransactionLogStore logStore, SeataProperties properties) {
        this.logStore = logStore;
        this.properties = properties;
    }

    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        String xid = beginXid(transactionName);
        log.debug("TCC transaction started: name={}, xid={}, type={}", transactionName, xid, type);
        try {
            T result = action.call();
            log.debug("TCC transaction completed: name={}, xid={}, type={}", transactionName, xid, type);
            return result;
        } catch (Exception e) {
            log.error("TCC transaction failed: name={}, xid={}, type={}", transactionName, xid, type, e);
            throw e;
        } finally {
            endXid();
        }
    }

    @Override
    public <T> T executeWithCompensation(String transactionName,
                                          Callable<T> action,
                                          Runnable compensation) throws Exception {
        String xid = beginXid(transactionName);
        log.debug("TCC+SAGA transaction started: name={}, xid={}", transactionName, xid);
        try {
            T result = action.call();
            log.debug("TCC+SAGA transaction completed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("TCC+SAGA transaction failed, executing compensation: name={}, xid={}", transactionName, xid, e);
            runCompensation(transactionName, xid, compensation);
            throw e;
        } finally {
            endXid();
        }
    }

    /**
     * 执行 TCC 事务
     *
     * <p>完整执行 Try → Confirm 流程，Try 或 Confirm 失败时执行 Cancel。
     * Try 前进行悬挂检查，Confirm/Cancel 前进行幂等检查。
     *
     * @param transactionName 事务名称
     * @param tccAction       TCC 动作
     * @param <T>             返回值类型
     * @return Try 阶段的返回值
     * @throws Exception 事务异常
     */
    public <T> T executeTcc(String transactionName, TccAction<T> tccAction) throws Exception {
        String xid = beginXid(transactionName);
        String branchId = generateBranchId();
        TccContext context = new TccContext(xid, branchId);

        TccTransactionLog txLog = new TccTransactionLog(xid, branchId, transactionName);
        if (logStore != null) {
            logStore.save(txLog);
        }

        log.info("TCC Try phase: name={}, xid={}, branch={}", transactionName, xid, branchId);
        T result;
        try {
            if (logStore != null && isSuspended(xid, branchId)) {
                log.warn("TCC Try skipped (suspension): already cancelled: xid={}, branch={}", xid, branchId);
                return null;
            }
            updateStatus(txLog, TccBranchStatus.TRYING);
            txLog.setTryStartedAt(LocalDateTime.now());

            result = tccAction.tryAction(context);

            txLog.setTryCompletedAt(LocalDateTime.now());
            updateStatus(txLog, TccBranchStatus.TRIED);
        } catch (Exception e) {
            log.error("TCC Try failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            executeCancelWithGuard(transactionName, xid, branchId, txLog, tccAction, context);
            throw e;
        } finally {
            endXid();
        }

        log.info("TCC Confirm phase: name={}, xid={}, branch={}", transactionName, xid, branchId);
        try {
            executeConfirmWithRetry(transactionName, xid, branchId, txLog, tccAction, context);
            log.info("TCC transaction completed: name={}, xid={}", transactionName, xid);
        } catch (Exception e) {
            log.error("TCC Confirm failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            executeCancelWithRetry(transactionName, xid, branchId, txLog, tccAction, context);
            throw e;
        }

        return result;
    }

    // ============= P0-4: 三大问题检查 =============

    /**
     * 悬挂检查：分支是否已被 Cancel
     */
    private boolean isSuspended(String xid, String branchId) {
        if (logStore == null) {
            return false;
        }
        Optional<TccTransactionLog> existing = logStore.findByXidAndBranchId(xid, branchId);
        return existing.isPresent() && existing.get().getStatus() == TccBranchStatus.CANCELLED;
    }

    /**
     * 执行 Cancel（带空回滚保护 + 幂等检查）
     */
    private <T> void executeCancelWithGuard(String transactionName, String xid, String branchId,
                                            TccTransactionLog txLog,
                                            TccAction<T> tccAction, TccContext context) {
        if (logStore != null) {
            Optional<TccTransactionLog> existing = logStore.findByXidAndBranchId(xid, branchId);
            if (existing.isPresent()) {
                TccBranchStatus currentStatus = existing.get().getStatus();
                if (currentStatus.isFinal()) {
                    log.info("TCC Cancel skipped (idempotent): already final: xid={}, branch={}, status={}",
                            xid, branchId, currentStatus);
                    return;
                }
                if (currentStatus == TccBranchStatus.INIT || currentStatus == TccBranchStatus.TRYING) {
                    log.warn("TCC Cancel skipped (empty rollback): Try not completed: xid={}, branch={}, status={}",
                            xid, branchId, currentStatus);
                    updateStatus(txLog, TccBranchStatus.CANCELLED);
                    return;
                }
            }
        }
        runTccCancelQuiet(transactionName, xid, tccAction, context);
        updateStatus(txLog, TccBranchStatus.CANCELLED);
    }

    // ============= P0-12: Confirm/Cancel 重试 =============

    /**
     * 执行 Confirm（带幂等检查 + 重试）
     */
    private <T> void executeConfirmWithRetry(String transactionName, String xid, String branchId,
                                             TccTransactionLog txLog,
                                             TccAction<T> tccAction, TccContext context) throws Exception {
        if (logStore != null) {
            Optional<TccTransactionLog> existing = logStore.findByXidAndBranchId(xid, branchId);
            if (existing.isPresent() && existing.get().getStatus() == TccBranchStatus.CONFIRMED) {
                log.info("TCC Confirm skipped (idempotent): already confirmed: xid={}, branch={}", xid, branchId);
                return;
            }
        }

        updateStatus(txLog, TccBranchStatus.CONFIRMING);
        int maxRetries = properties != null ? properties.getTccRetryCount() : 0;
        long intervalMs = properties != null ? properties.getTccRetryIntervalMs() : 1000;

        Exception lastException = new IllegalStateException(
                "TCC Confirm exhausted retries without a specific exception");
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                tccAction.confirmAction(context);
                updateStatus(txLog, TccBranchStatus.CONFIRMED);
                return;
            } catch (Exception e) {
                lastException = e;
                txLog.setLastError(e.getMessage());
                log.warn("TCC Confirm attempt {} failed: xid={}, branch={}", attempt + 1, xid, branchId, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(intervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        updateStatus(txLog, TccBranchStatus.TRIED);
        throw lastException;
    }

    /**
     * 执行 Cancel（带幂等检查 + 重试）
     */
    private <T> void executeCancelWithRetry(String transactionName, String xid, String branchId,
                                            TccTransactionLog txLog,
                                            TccAction<T> tccAction, TccContext context) throws Exception {
        if (logStore != null) {
            Optional<TccTransactionLog> existing = logStore.findByXidAndBranchId(xid, branchId);
            if (existing.isPresent()) {
                TccBranchStatus currentStatus = existing.get().getStatus();
                if (currentStatus == TccBranchStatus.CANCELLED) {
                    log.info("TCC Cancel skipped (idempotent): already cancelled: xid={}, branch={}", xid, branchId);
                    return;
                }
                if (currentStatus == TccBranchStatus.INIT || currentStatus == TccBranchStatus.TRYING) {
                    log.warn("TCC Cancel skipped (empty rollback): xid={}, branch={}", xid, branchId);
                    updateStatus(txLog, TccBranchStatus.CANCELLED);
                    return;
                }
            }
        }

        updateStatus(txLog, TccBranchStatus.CANCELLING);
        int maxRetries = properties != null ? properties.getTccRetryCount() : 0;
        long intervalMs = properties != null ? properties.getTccRetryIntervalMs() : 1000;

        Exception lastException = new IllegalStateException(
                "TCC Cancel exhausted retries without a specific exception");
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                tccAction.cancelAction(context);
                updateStatus(txLog, TccBranchStatus.CANCELLED);
                return;
            } catch (Exception e) {
                lastException = e;
                txLog.setLastError(e.getMessage());
                log.warn("TCC Cancel attempt {} failed: xid={}, branch={}", attempt + 1, xid, branchId, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(intervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        throw lastException;
    }

    // ============= 恢复回调（P0-11） =============

    @Override
    public void recoverCancel(TccTransactionLog txLog) throws Exception {
        log.info("TCC recovery Cancel: xid={}, branch={}", txLog.getXid(), txLog.getBranchId());
        if (logStore != null) {
            logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), TccBranchStatus.CANCELLING);
        }
    }

    // ============= 辅助方法 =============

    private void updateStatus(TccTransactionLog txLog, TccBranchStatus status) {
        if (logStore != null) {
            logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), status);
        }
        txLog.setStatus(status);
    }

    private <T> void runTccCancelQuiet(String transactionName, String xid,
                                       TccAction<T> tccAction, TccContext context) {
        try {
            tccAction.cancelAction(context);
            log.info("TCC Cancel completed: name={}, xid={}", transactionName, xid);
        } catch (Exception ce) {
            log.error("TCC Cancel failed: name={}, xid={}", transactionName, xid, ce);
        }
    }

    private void runCompensation(String transactionName, String xid, Runnable compensation) {
        if (compensation == null) {
            return;
        }
        try {
            compensation.run();
            log.info("Compensation completed: name={}, xid={}", transactionName, xid);
        } catch (Exception ce) {
            log.error("Compensation failed: name={}, xid={}", transactionName, xid, ce);
        }
    }

    @Override
    public TransactionType getCurrentType() {
        return TransactionType.TCC;
    }
}
