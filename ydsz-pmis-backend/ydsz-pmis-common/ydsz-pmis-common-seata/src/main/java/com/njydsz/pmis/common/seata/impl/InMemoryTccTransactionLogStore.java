package com.njydsz.pmis.common.seata.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.njydsz.pmis.common.seata.api.TccBranchStatus;
import com.njydsz.pmis.common.seata.api.TccTransactionLog;
import com.njydsz.pmis.common.seata.api.TccTransactionLogStore;

/**
 * 内存版 TCC 事务日志存储
 *
 * <p>使用 {@link ConcurrentHashMap} 存储事务日志，适用于单机、开发/测试环境。
 * 生产环境应使用 {@code JdbcTccTransactionLogStore} 配合数据库持久化。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class InMemoryTccTransactionLogStore implements TccTransactionLogStore {

    private final ConcurrentHashMap<String, TccTransactionLog> store = new ConcurrentHashMap<>();

    private static String key(String xid, String branchId) {
        return xid + ":" + branchId;
    }

    @Override
    public void save(TccTransactionLog txLog) {
        store.put(key(txLog.getXid(), txLog.getBranchId()), txLog);
    }

    @Override
    public void updateStatus(String xid, String branchId, TccBranchStatus status) {
        TccTransactionLog logEntry = store.get(key(xid, branchId));
        if (logEntry != null) {
            logEntry.setStatus(status);
            if (status.isFinal()) {
                logEntry.setFinishedAt(LocalDateTime.now());
            }
        }
    }

    @Override
    public Optional<TccTransactionLog> findByXidAndBranchId(String xid, String branchId) {
        return Optional.ofNullable(store.get(key(xid, branchId)));
    }

    @Override
    public List<TccTransactionLog> findTimeoutPending(LocalDateTime threshold) {
        return store.values().stream()
                .filter(log -> log.getStatus() == TccBranchStatus.TRIED)
                .filter(log -> log.getTryCompletedAt() != null && log.getTryCompletedAt().isBefore(threshold))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String xid, String branchId) {
        store.remove(key(xid, branchId));
    }
}
