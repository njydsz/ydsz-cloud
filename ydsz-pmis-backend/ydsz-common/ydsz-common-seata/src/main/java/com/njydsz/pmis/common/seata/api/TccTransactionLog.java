package com.njydsz.common.seata.api;

import java.time.LocalDateTime;

/**
 * TCC 事务日志记录
 *
 * <p>持久化 TCC 分支事务状态，用于解决空回滚、悬挂、幂等三大经典问题。
 *
 * <p>对应数据库表 {@code tcc_transaction_log}（见 DDL）。
 *
 * @author ydsz-team
 * @since 3.5.0
 */
public class TccTransactionLog {

    /** 全局事务 ID */
    private final String xid;

    /** 分支事务 ID */
    private final String branchId;

    /** 事务名称 */
    private final String transactionName;

    /** 分支状态 */
    private TccBranchStatus status;

    /** 业务上下文快照（JSON，用于 Confirm/Cancel 恢复） */
    private String contextSnapshot;

    /** Try 开始时间 */
    private LocalDateTime tryStartedAt;

    /** Try 完成时间 */
    private LocalDateTime tryCompletedAt;

    /** Confirm/Cancel 完成时间 */
    private LocalDateTime finishedAt;

    /** 重试次数 */
    private int retryCount;

    /** 最近一次错误信息 */
    private String lastError;

    public TccTransactionLog(String xid, String branchId, String transactionName) {
        this.xid = xid;
        this.branchId = branchId;
        this.transactionName = transactionName;
        this.status = TccBranchStatus.INIT;
        this.retryCount = 0;
    }

    public String getXid() {
        return xid;
    }

    public String getBranchId() {
        return branchId;
    }

    public String getTransactionName() {
        return transactionName;
    }

    public TccBranchStatus getStatus() {
        return status;
    }

    public void setStatus(TccBranchStatus status) {
        this.status = status;
    }

    public String getContextSnapshot() {
        return contextSnapshot;
    }

    public void setContextSnapshot(String contextSnapshot) {
        this.contextSnapshot = contextSnapshot;
    }

    public LocalDateTime getTryStartedAt() {
        return tryStartedAt;
    }

    public void setTryStartedAt(LocalDateTime tryStartedAt) {
        this.tryStartedAt = tryStartedAt;
    }

    public LocalDateTime getTryCompletedAt() {
        return tryCompletedAt;
    }

    public void setTryCompletedAt(LocalDateTime tryCompletedAt) {
        this.tryCompletedAt = tryCompletedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
