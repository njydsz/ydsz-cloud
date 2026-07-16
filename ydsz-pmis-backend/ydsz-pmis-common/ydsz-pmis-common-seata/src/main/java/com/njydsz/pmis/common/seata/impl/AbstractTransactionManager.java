package com.njydsz.pmis.common.seata.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.seata.api.DistributedTransactionManager;
import com.njydsz.pmis.common.seata.audit.TransactionAuditLogger;
import com.njydsz.pmis.common.seata.metrics.SeataMetrics;

import org.springframework.beans.factory.ObjectProvider;

public abstract class AbstractTransactionManager implements DistributedTransactionManager {
    private static final Logger log = LoggerFactory.getLogger(AbstractTransactionManager.class);
    private static final ThreadLocal<String> XID_HOLDER = new ThreadLocal<>();
    static String getXidFromHolder() { return XID_HOLDER.get(); }
    static void setXidToHolder(String xid) { XID_HOLDER.set(xid); }
    static void removeXidFromHolder() { XID_HOLDER.remove(); }
    private final ObjectProvider<SeataMetrics> metricsProvider;
    private final ObjectProvider<TransactionAuditLogger> auditProvider;
    protected AbstractTransactionManager() { this.metricsProvider = null; this.auditProvider = null; }
    protected AbstractTransactionManager(ObjectProvider<SeataMetrics> metricsProvider, ObjectProvider<TransactionAuditLogger> auditProvider) { this.metricsProvider = metricsProvider; this.auditProvider = auditProvider; }
    protected SeataMetrics getMetrics() { return metricsProvider != null ? metricsProvider.getIfAvailable() : null; }
    protected TransactionAuditLogger getAuditLogger() { return auditProvider != null ? auditProvider.getIfAvailable() : null; }
    protected void recordStart(String transactionName, String xid) { SeataMetrics metrics = getMetrics(); if (metrics != null) { metrics.recordTxStart(getCurrentType()); } TransactionAuditLogger audit = getAuditLogger(); if (audit != null) { audit.auditStart(transactionName, getCurrentType(), xid); } }
    protected void recordComplete(String transactionName, String xid, String branchId, String result, long durationMs, String error) { SeataMetrics metrics = getMetrics(); if (metrics != null) { metrics.recordTxComplete(getCurrentType(), result, durationMs); } TransactionAuditLogger audit = getAuditLogger(); if (audit != null) { if (error != null) { audit.auditFailure(transactionName, getCurrentType(), xid, branchId, durationMs, error); } else { audit.auditSuccess(transactionName, getCurrentType(), xid, branchId, durationMs); } } }
    protected String generateXid() { return UUID.randomUUID().toString(); }
    protected String generateBranchId() { return UUID.randomUUID().toString(); }
    protected String beginXid(String transactionName) { String xid = generateXid(); XID_HOLDER.set(xid); log.debug("Transaction started: name={}, xid={}, type={}", transactionName, xid, getCurrentType()); recordStart(transactionName, xid); return xid; }
    protected void endXid() { XID_HOLDER.remove(); }
    @Override
    public String getCurrentXid() { return XID_HOLDER.get(); }
}
