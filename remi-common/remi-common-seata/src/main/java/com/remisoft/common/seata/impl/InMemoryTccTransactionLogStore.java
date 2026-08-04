package com.remisoft.common.seata.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.remisoft.common.seata.api.TccBranchStatus;
import com.remisoft.common.seata.api.TccTransactionLog;
import com.remisoft.common.seata.api.TccTransactionLogStore;

/**
 * 内存版 TCC 事务日志存储
 *
 * <p>使用 {@link ConcurrentHashMap} 存储事务日志，适用于单机、开发/测试环境。
 * 生产环境应使用 {@code JdbcTccTransactionLogStore} 配合数据库持久化。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class InMemoryTccTransactionLogStore implements TccTransactionLogStore {

    private static final int MAX_ENTRIES = 10000;
    private static final long RETENTION_HOURS = 1;

    private final ConcurrentHashMap<String, TccTransactionLog> store = new ConcurrentHashMap<>();

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
        if (store.size() >= MAX_ENTRIES) {
            cleanupFinalStateLogs();
        }
        store.put(key(txLog.getXid(), txLog.getBranchId()), txLog);
    }

    /**
     * 更新分支事务状态
     *
     * @param xid      全局事务 ID
     * @param branchId 分支事务 ID
     * @param status   新状态
     */
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

    /**
     * 根据 XID 和分支 ID 查询事务日志
     *
     * @param xid      全局事务 ID
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
                .filter(log -> log.getTryCompletedAt() != null && log.getTryCompletedAt().isBefore(threshold))
                .collect(Collectors.toList());
    }

    /**
     * 删除事务日志
     *
     * @param xid      全局事务 ID
     * @param branchId 分支事务 ID
     */
    @Override
    public void delete(String xid, String branchId) {
        store.remove(key(xid, branchId));
    }

    /**
     * 清理终态事务日志（超过保留时间的日志自动删除）
     */
    public void cleanupFinalStateLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
        store.entrySet().removeIf(entry -> {
            TccTransactionLog log = entry.getValue();
            return log.getStatus().isFinal()
                    && log.getFinishedAt() != null
                    && log.getFinishedAt().isBefore(cutoff);
        });
    }
}