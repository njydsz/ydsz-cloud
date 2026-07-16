package com.njydsz.pmis.common.seata.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.seata.api.DistributedTransactionManager;

/**
 * 分布式事务管理器抽象基类
 *
 * <p>提取公共的 XID 上下文管理逻辑，消除 {@link LocalTransactionManager} 和
 * {@link TccTransactionManager} 中重复的 ThreadLocal + UUID 生成代码。
 *
 * <p>子类通过 {@link #beginXid(String)} / {@link #endXid()} 管理当前线程的 XID，
 * 通过 {@link #generateXid()} / {@link #generateBranchId()} 生成唯一标识。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public abstract class AbstractTransactionManager implements DistributedTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(AbstractTransactionManager.class);

    private static final ThreadLocal<String> XID_HOLDER = new ThreadLocal<>();

    /**
     * 生成全局事务 XID
     *
     * @return 全局唯一事务 ID
     */
    protected String generateXid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成分支事务 ID
     *
     * @return 全局唯一分支 ID
     */
    protected String generateBranchId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 在当前线程绑定 XID，并记录调试日志
     *
     * @param transactionName 事务名称
     * @return 生成的 XID
     */
    protected String beginXid(String transactionName) {
        String xid = generateXid();
        XID_HOLDER.set(xid);
        log.debug("Transaction started: name={}, xid={}, type={}", transactionName, xid, getCurrentType());
        return xid;
    }

    /**
     * 清除当前线程的 XID
     */
    protected void endXid() {
        XID_HOLDER.remove();
    }

    @Override
    public String getCurrentXid() {
        return XID_HOLDER.get();
    }
}
