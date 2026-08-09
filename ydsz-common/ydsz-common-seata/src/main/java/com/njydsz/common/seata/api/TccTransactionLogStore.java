package com.njydsz.common.seata.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TCC 事务日志存储接口
 *
 * <p>用于持久化 TCC 分支事务状态，解决三大经典问题：
 * <ul>
 *   <li><b>空回滚</b>：Cancel 前检查分支状态，若 Try 未执行则跳过</li>
 *   <li><b>悬挂</b>：Try 前检查分支状态，若已有 Cancel 则跳过</li>
 *   <li><b>幂等</b>：Confirm/Cancel 前检查是否已为终态</li>
 * </ul>
 *
 * <p>提供两种实现：
 * <ul>
 *   <li>{@code InMemoryTccTransactionLogStore} - 内存实现，适用于单机/测试</li>
 *   <li>{@code JdbcTccTransactionLogStore} - 数据库实现，适用于生产环境（需配套 DDL）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TccTransactionLogStore {

    /**
     * 记录分支事务开始（Try 前）
     *
     * @param log 事务日志
     */
    void save(TccTransactionLog log);

    /**
     * 更新分支事务状态
     *
     * @param xid     全局事务 ID
     * @param branchId 分支事务 ID
     * @param status  新状态
     */
    void updateStatus(String xid, String branchId, TccBranchStatus status);

    /**
     * 查询分支事务日志
     *
     * @param xid     全局事务 ID
     * @param branchId 分支事务 ID
     * @return 事务日志（Optional）
     */
    Optional<TccTransactionLog> findByXidAndBranchId(String xid, String branchId);

    /**
     * 查询超时未完成的分支事务（用于恢复扫描）
     *
     * @param threshold 超时阈值，早于此时间的 TRIED 状态分支需要恢复
     * @return 超时分支列表
     */
    List<TccTransactionLog> findTimeoutPending(LocalDateTime threshold);

    /**
     * 删除已完成的分支事务日志（终态清理）
     *
     * @param xid     全局事务 ID
     * @param branchId 分支事务 ID
     */
    void delete(String xid, String branchId);
}
