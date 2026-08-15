package com.njydsz.common.seata.impl;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.seata.api.TccAction;
import com.njydsz.common.seata.api.TccBranchStatus;
import com.njydsz.common.seata.api.TccContext;
import com.njydsz.common.seata.api.TccTransactionLog;
import com.njydsz.common.seata.api.TccTransactionLogStore;
import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.config.SeataProperties;
import com.njydsz.common.seata.metrics.SeataMetrics;

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
 * ({@code ydsz.seata.tcc-retry-count} / {@code ydsz.seata.tcc-retry-interval-ms})。
 *
 * <p>注意：此实现为本地 TCC 协调器，适用于单服务内的多资源操作。
 * 跨服务的 TCC 需要配合 Seata TCC 模式使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TccTransactionManager extends AbstractTransactionManager
        implements TccTransactionRecoveryScanner.TccRecoveryHandler {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionManager.class);

    private final TccTransactionLogStore logStore;
    private final SeataProperties properties;
    private final ObjectProvider<TccActionRegistry> actionRegistryProvider;

    /** 当前实例的 TCC Action 缓存（同实例快速路径） */
    private final Map<String, TccAction<?>> localActionCache = new ConcurrentHashMap<>();

    /** Confirm/Cancel 异步执行器（可选，为空则使用 ForkJoinPool） */
    private Executor asyncExecutor;

    /**
     * 无日志存储模式（向后兼容）
     */
    public TccTransactionManager() {
        this.logStore = null;
        this.properties = null;
        this.actionRegistryProvider = null;
    }

    /**
     * 带日志存储模式（推荐）
     *
     * @param logStore   事务日志存储
     * @param properties 配置
     */
    public TccTransactionManager(TccTransactionLogStore logStore, SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        this(logStore, properties, metricsProvider, auditProvider, null);
    }

    /**
     * 带日志存储和注册表模式（推荐用于生产环境）
     *
     * @param logStore         事务日志存储
     * @param properties       配置
     * @param metricsProvider  指标采集提供者（可选）
     * @param auditProvider    审计日志提供者（可选）
     * @param actionRegistryProvider TCC Action 注册表提供者（可选）
     */
    public TccTransactionManager(TccTransactionLogStore logStore, SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider,
            ObjectProvider<TccActionRegistry> actionRegistryProvider) {
        super(metricsProvider, auditProvider);
        this.logStore = logStore;
        this.properties = properties;
        this.actionRegistryProvider = actionRegistryProvider;
    }

    /**
     * 设置异步执行器（P1-3 新增）
     *
     * <p>用于异步 Confirm 模式，执行 Confirm 操作的后台线程池。
     * 建议注入专用的业务线程池，避免与 RPC 线程池竞争。
     *
     * @param asyncExecutor 异步执行器
     */
    public void setAsyncExecutor(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 获取异步执行器，未设置时返回 ForkJoinPool.commonPool()
     */
    private Executor getAsyncExecutor() {
        return asyncExecutor != null ? asyncExecutor : ForkJoinPool.commonPool();
    }

    /**
     * 执行分布式事务（TCC 模式下走 executeTcc，其他模式降级为本地事务）
     *
     * @param transactionName 事务名称
     * @param type            事务类型
     * @param action          业务操作
     * @param <T>             返回值类型
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        if (type == TransactionType.TCC && action instanceof TccAction) {
            return executeTcc(transactionName, null, (TccAction<T>) action);
        }
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

    /**
     * 执行分布式事务（带补偿动作，TCC+SAGA 混合模式）
     *
     * @param transactionName 事务名称
     * @param action          正向操作
     * @param compensation    补偿操作
     * @param <T>             返回值类型
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
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
     * @param actionBeanName  TCC Action 的 Spring Bean 名称（可为 null）
     * @param tccAction       TCC 动作
     * @param <T>             返回值类型
     * @return Try 阶段的返回值
     * @throws Exception 事务异常
     */
    public <T> T executeTcc(String transactionName, String actionBeanName, TccAction<T> tccAction) throws Exception {
        String xid = beginXid(transactionName);
        String branchId = generateBranchId();
        TccContext context = new TccContext(xid, branchId);
        cacheAction(xid, tccAction);

        TccTransactionLog txLog = new TccTransactionLog(xid, branchId, transactionName);
        txLog.setActionBeanName(actionBeanName);
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

    /**
     * 执行 TCC 事务（向后兼容，无 Bean 名称版本）
     *
     * @param transactionName 事务名称
     * @param tccAction       TCC 动作
     * @param <T>             返回值类型
     * @return Try 阶段的返回值
     * @throws Exception 事务异常
     */
    public <T> T executeTcc(String transactionName, TccAction<T> tccAction) throws Exception {
        return executeTcc(transactionName, null, tccAction);
    }

    /**
     * 异步执行 TCC 事务
     *
     * <p>Try 阶段同步执行，完成后立即返回。Confirm 阶段异步执行，
     * 通过回调通知执行结果。
     *
     * <p>回调签名：{@code BiConsumer<TccContext, Throwable>}
     * - 成功时：{@code callback.accept(context, null)}
     * - 失败时：{@code callback.accept(context, error)}
     *
     * @param transactionName 事务名称
     * @param actionBeanName  TCC Action 的 Spring Bean 名称（可为 null）
     * @param tccAction       TCC 动作
     * @param callback        完成回调（可为 null）
     * @param <T>             返回值类型
     * @return Try 阶段的返回值（CompletableFuture，Try 完成后即可获取）
     */
    public <T> CompletableFuture<T> executeTccAsync(String transactionName,
                                                      String actionBeanName,
                                                      TccAction<T> tccAction,
                                                      BiConsumer<TccContext, Throwable> callback) {
        String xid = beginXid(transactionName);
        String branchId = generateBranchId();
        TccContext context = new TccContext(xid, branchId);
        cacheAction(xid, tccAction);

        TccTransactionLog txLog = new TccTransactionLog(xid, branchId, transactionName);
        txLog.setActionBeanName(actionBeanName);
        if (logStore != null) {
            logStore.save(txLog);
        }

        log.info("TCC Async Try phase: name={}, xid={}, branch={}", transactionName, xid, branchId);

        // 同步执行 Try 阶段
        T result;
        try {
            if (logStore != null && isSuspended(xid, branchId)) {
                log.warn("TCC Async Try skipped (suspension): already cancelled: xid={}, branch={}", xid, branchId);
                endXid();
                return CompletableFuture.completedFuture(null);
            }
            updateStatus(txLog, TccBranchStatus.TRYING);
            txLog.setTryStartedAt(LocalDateTime.now());

            result = tccAction.tryAction(context);

            txLog.setTryCompletedAt(LocalDateTime.now());
            updateStatus(txLog, TccBranchStatus.TRIED);
        } catch (Exception e) {
            log.error("TCC Async Try failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            executeCancelWithGuard(transactionName, xid, branchId, txLog, tccAction, context);
            endXid();
            notifyCallback(callback, context, e);
            return CompletableFuture.failedFuture(e);
        }

        // 异步执行 Confirm 阶段
        final T finalResult = result;
        Executor executor = getAsyncExecutor();
        log.info("TCC Async Confirm phase scheduled: name={}, xid={}, branch={}, executor={}",
                transactionName, xid, branchId, executor.getClass().getSimpleName());

        CompletableFuture<T> future = CompletableFuture.supplyAsync(() ->
                doAsyncConfirm(transactionName, xid, branchId, txLog, tccAction, context, finalResult),
                executor);

        // Try 完成后立即返回，不等待 Confirm
        endXid();
        return future.whenComplete((r, ex) -> notifyCallback(callback, context, ex));
    }

    /**
     * 异步执行 TCC 事务（向后兼容，无 Bean 名称版本）
     */
    public <T> CompletableFuture<T> executeTccAsync(String transactionName,
                                                      TccAction<T> tccAction,
                                                      BiConsumer<TccContext, Throwable> callback) {
        return executeTccAsync(transactionName, null, tccAction, callback);
    }

    /**
     * 异步 Confirm 执行逻辑（提取为独立方法降低嵌套层级）
     */
    private <T> T doAsyncConfirm(String transactionName, String xid, String branchId,
                                  TccTransactionLog txLog, TccAction<T> tccAction,
                                  TccContext context, T result) {
        try {
            executeConfirmWithRetry(transactionName, xid, branchId, txLog, tccAction, context);
            log.info("TCC Async Confirm completed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("TCC Async Confirm failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            try {
                executeCancelWithRetry(transactionName, xid, branchId, txLog, tccAction, context);
            } catch (Exception cancelEx) {
                log.error("TCC Async Cancel also failed: name={}, xid={}", transactionName, xid, cancelEx);
                throw new RuntimeException("TCC Async Confirm failed: " + transactionName, cancelEx);
            }
            throw new RuntimeException("TCC Async Confirm failed: " + transactionName, e);
        } finally {
            localActionCache.remove(xid);
            endXid();
        }
    }

    /**
     * 通知回调（安全包装，避免回调异常影响主流程）
     */
    private <T> void notifyCallback(BiConsumer<TccContext, Throwable> callback, TccContext context, Throwable error) {
        if (callback == null) {
            return;
        }
        try {
            callback.accept(context, error);
        } catch (Exception ce) {
            log.warn("TCC callback execution failed", ce);
        }
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

    // ============= 恢复回调 =============

    /**
     * 恢复时执行 Cancel（由恢复扫描器回调）
     *
     * <p>优先从本地缓存查找 Action，未命中时通过注册表查找。
     * 注册表查找支持跨实例恢复。
     *
     * @param txLog 超时事务日志
     * @throws Exception Cancel 执行异常
     */
    @Override
    public void recoverCancel(TccTransactionLog txLog) throws Exception {
        log.info("TCC recovery Cancel: xid={}, branch={}", txLog.getXid(), txLog.getBranchId());

        TccAction<?> action = resolveAction(txLog);
        if (action != null) {
            try {
                action.cancelAction(new TccContext(txLog.getXid(), txLog.getBranchId()));
                log.info("TCC recovery Cancel completed: xid={}", txLog.getXid());
            } catch (Exception e) {
                log.error("TCC recovery Cancel failed: xid={}", txLog.getXid(), e);
            }
        } else {
            log.warn("TCC recovery Cancel skipped: no TccAction found for xid={}, beanName={}",
                    txLog.getXid(), txLog.getActionBeanName());
        }
        if (logStore != null) {
            logStore.updateStatus(txLog.getXid(), txLog.getBranchId(), TccBranchStatus.CANCELLED);
        }
    }

    /**
     * 解析 TCC Action：优先本地缓存，其次注册表
     */
    @SuppressWarnings("unchecked")
    private <T> TccAction<T> resolveAction(TccTransactionLog txLog) {
        // 1. 本地缓存（同实例快速路径）
        TccAction<T> action = (TccAction<T>) localActionCache.get(txLog.getXid());
        if (action != null) {
            return action;
        }
        // 2. 注册表（跨实例恢复）
        if (actionRegistryProvider != null && txLog.getActionBeanName() != null) {
            TccActionRegistry registry = actionRegistryProvider.getIfAvailable();
            if (registry != null) {
                return registry.findByName(txLog.getActionBeanName());
            }
        }
        return null;
    }

    // ============= 辅助方法 =============

    private void cacheAction(String xid, TccAction<?> action) {
        if (action != null) {
            localActionCache.put(xid, action);
        }
    }

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

    /**
     * 获取当前事务类型
     *
     * @return TCC 事务类型
     */
    @Override
    public TransactionType getCurrentType() {
        return TransactionType.TCC;
    }
}
