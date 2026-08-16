package com.njydsz.common.seata.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.seata.api.DistributedTransactionManager;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.context.XidContextHolder;
import com.njydsz.common.seata.metrics.SeataMetrics;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 分布式事务管理器抽象基类。
 *
 * <p>提供 XID 生成与 ThreadLocal 传播、事务开始/完成的指标记录和审计日志等公共能力。
 * 子类（{@code SeataTransactionManager}、{@code TccTransactionManager}、{@code SagaOrchestrator}）
 * 只需实现 {@link #getCurrentType()} 和具体的 begin/commit/rollback 逻辑。
 *
 * <p>通过 {@link ObjectProvider} 可选注入 {@link SeataMetrics} 和 {@link TransactionAuditLogger}，
 * 未配置时降级跳过指标和审计。
 *
 * <p><b>P2-3 修复</b>：XID 持有者从本类迁移至独立的 {@link XidContextHolder}，
 * 解决与 {@code DefaultXidPropagator} 的循环依赖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractTransactionManager implements DistributedTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(AbstractTransactionManager.class);

    private final ObjectProvider<SeataMetrics> metricsProvider;
    private final ObjectProvider<TransactionAuditLogger> auditProvider;

    /**
     * 无参构造（降级模式，不记录指标和审计）
     */
    protected AbstractTransactionManager() {
        this.metricsProvider = null;
        this.auditProvider = null;
    }

    /**
     * 带指标和审计的构造
     *
     * @param metricsProvider 指标采集提供者（可选）
     * @param auditProvider   审计日志提供者（可选）
     */
    protected AbstractTransactionManager(ObjectProvider<SeataMetrics> metricsProvider,
                                          ObjectProvider<TransactionAuditLogger> auditProvider) {
        this.metricsProvider = metricsProvider;
        this.auditProvider = auditProvider;
    }

    /**
     * 获取指标采集器实例
     *
     * @return SeataMetrics 实例，未配置时返回 null
     */
    protected SeataMetrics getMetrics() {
        return metricsProvider != null ? metricsProvider.getIfAvailable() : null;
    }

    /**
     * 获取审计日志实例
     *
     * @return TransactionAuditLogger 实例，未配置时返回 null
     */
    protected TransactionAuditLogger getAuditLogger() {
        return auditProvider != null ? auditProvider.getIfAvailable() : null;
    }

    /**
     * 记录事务开始（指标 + 审计）
     *
     * @param transactionName 事务名称
     * @param xid             全局事务 ID
     */
    protected void recordStart(String transactionName, String xid) {
        SeataMetrics metrics = getMetrics();
        if (metrics != null) {
            metrics.recordTxStart(getCurrentType());
        }
        TransactionAuditLogger audit = getAuditLogger();
        if (audit != null) {
            audit.auditStart(transactionName, getCurrentType(), xid);
        }
    }

    /**
     * 记录事务完成（指标 + 审计）
     *
     * @param transactionName 事务名称
     * @param xid             全局事务 ID
     * @param branchId        分支事务 ID（可为 null）
     * @param result          执行结果（"success" 或 "fail"）
     * @param durationMs      执行耗时（毫秒）
     * @param error           异常信息（成功时为 null）
     */
    protected void recordComplete(String transactionName, String xid, String branchId,
                                   String result, long durationMs, String error) {
        SeataMetrics metrics = getMetrics();
        if (metrics != null) {
            metrics.recordTxComplete(getCurrentType(), result, durationMs);
        }
        TransactionAuditLogger audit = getAuditLogger();
        if (audit != null) {
            if (error != null) {
                audit.auditFailure(transactionName, getCurrentType(), xid, branchId, durationMs, error);
            } else {
                audit.auditSuccess(transactionName, getCurrentType(), xid, branchId, durationMs);
            }
        }
    }

    /**
     * 生成全局事务 XID
     *
     * @return UUID 格式的 XID
     */
    protected String generateXid() {
        return IdGenerator.nextIdStr();
    }

    /**
     * 生成分支事务 ID
     *
     * @return UUID 格式的分支 ID
     */
    protected String generateBranchId() {
        return IdGenerator.nextIdStr();
    }

    /**
     * 开始事务，生成 XID 并绑定到当前线程
     *
     * @param transactionName 事务名称
     * @return 生成的全局事务 ID
     */
    protected String beginXid(String transactionName) {
        String xid = generateXid();
        XidContextHolder.setXid(xid);
        XidContextHolder.current().setStartTime(System.currentTimeMillis());
        log.debug("Transaction started: name={}, xid={}, type={}", transactionName, xid, getCurrentType());
        recordStart(transactionName, xid);
        return xid;
    }

    /**
     * 结束事务（成功路径），记录指标和审计
     */
    protected void endXid() {
        endXid(null);
    }

    /**
     * 结束事务（带异常信息），记录指标和审计
     *
     * @param error 异常信息，成功时为 null
     */
    protected void endXid(Throwable error) {
        XidContextHolder.XidContext ctx = XidContextHolder.current();
        if (ctx != null) {
            String transactionName = ctx.getName();
            String xid = ctx.getXid();
            long durationMs = System.currentTimeMillis() - ctx.getStartTime();
            String result = error == null ? "success" : "fail";
            recordComplete(transactionName, xid, null, result, durationMs,
                    error != null ? error.getMessage() : null);
        }
        XidContextHolder.remove();
    }

    /**
     * 获取全局事务 XID（如有）
     *
     * @return 全局事务 ID，无事务上下文时返回 null
     */
    @Override
    public String getCurrentXid() {
        return XidContextHolder.getXid();
    }
}
