package com.njydsz.pmis.common.seata.impl;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.common.seata.api.DistributedTransactionManager;
import com.njydsz.pmis.common.seata.api.TransactionType;

/**
 * 本地事务管理器（降级实现）
 *
 * <p>当 Seata 不可用时使用，仅提供本地 {@code @Transactional} 语义。
 * 不提供跨服务事务保证，适用于单机模式或开发环境。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class LocalTransactionManager implements DistributedTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(LocalTransactionManager.class);

    private static final ThreadLocal<String> XID_HOLDER = new ThreadLocal<>();

    @Override
    @Transactional
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        String xid = UUID.randomUUID().toString();
        XID_HOLDER.set(xid);
        log.debug("Local transaction started: name={}, xid={}", transactionName, xid);
        try {
            T result = action.call();
            log.debug("Local transaction committed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("Local transaction rolled back: name={}, xid={}", transactionName, xid, e);
            throw e;
        } finally {
            XID_HOLDER.remove();
        }
    }

    @Override
    @Transactional
    public <T> T executeWithCompensation(String transactionName,
                                          Callable<T> action,
                                          Runnable compensation) throws Exception {
        String xid = UUID.randomUUID().toString();
        XID_HOLDER.set(xid);
        log.debug("Saga transaction started: name={}, xid={}", transactionName, xid);
        try {
            T result = action.call();
            log.debug("Saga transaction completed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("Saga transaction failed, executing compensation: name={}, xid={}", transactionName, xid, e);
            try {
                compensation.run();
                log.info("Compensation completed: name={}, xid={}", transactionName, xid);
            } catch (Exception ce) {
                log.error("Compensation failed: name={}, xid={}", transactionName, xid, ce);
            }
            throw e;
        } finally {
            XID_HOLDER.remove();
        }
    }

    @Override
    public TransactionType getCurrentType() {
        return TransactionType.LOCAL;
    }

    @Override
    public String getCurrentXid() {
        return XID_HOLDER.get();
    }
}
