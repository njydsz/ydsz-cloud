package com.njydsz.pmis.common.seata.impl;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.seata.api.DistributedTransactionManager;
import com.njydsz.pmis.common.seata.api.TccAction;
import com.njydsz.pmis.common.seata.api.TccContext;
import com.njydsz.pmis.common.seata.api.TransactionType;

/**
 * TCC 事务管理器
 *
 * <p>实现 Try-Confirm-Cancel 模式：
 * <ol>
 *   <li>Try 阶段：执行 {@link TccAction#tryAction}，预留资源</li>
 *   <li>如果 Try 成功：执行 {@link TccAction#confirmAction}，确认提交</li>
 *   <li>如果 Try 失败：执行 {@link TccAction#cancelAction}，取消预留</li>
 * </ol>
 *
 * <p>注意：此实现为本地 TCC 协调器，适用于单服务内的多资源操作。
 * 跨服务的 TCC 需要配合 Seata TCC 模式使用。
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public class TccTransactionManager implements DistributedTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionManager.class);

    private static final ThreadLocal<String> XID_HOLDER = new ThreadLocal<>();

    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        String xid = UUID.randomUUID().toString();
        XID_HOLDER.set(xid);
        log.debug("TCC transaction started: name={}, xid={}", transactionName, xid);
        try {
            T result = action.call();
            log.debug("TCC transaction confirmed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("TCC transaction cancelled: name={}, xid={}", transactionName, xid, e);
            throw e;
        } finally {
            XID_HOLDER.remove();
        }
    }

    @Override
    public <T> T executeWithCompensation(String transactionName,
                                          Callable<T> action,
                                          Runnable compensation) throws Exception {
        return execute(transactionName, TransactionType.TCC, action);
    }

    /**
     * 执行 TCC 事务
     *
     * @param transactionName 事务名称
     * @param tccAction       TCC 动作
     * @param <T>             返回值类型
     * @return Try 阶段的返回值
     * @throws Exception 事务异常
     */
    public <T> T executeTcc(String transactionName, TccAction<T> tccAction) throws Exception {
        String xid = UUID.randomUUID().toString();
        String branchId = UUID.randomUUID().toString();
        TccContext context = new TccContext(xid, branchId);
        XID_HOLDER.set(xid);

        log.info("TCC Try phase: name={}, xid={}, branch={}", transactionName, xid, branchId);
        T result;
        try {
            result = tccAction.tryAction(context);
        } catch (Exception e) {
            log.error("TCC Try failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            try {
                tccAction.cancelAction(context);
            } catch (Exception ce) {
                log.error("TCC Cancel failed: name={}, xid={}", transactionName, xid, ce);
            }
            throw e;
        }

        log.info("TCC Confirm phase: name={}, xid={}, branch={}", transactionName, xid, branchId);
        try {
            tccAction.confirmAction(context);
            log.info("TCC transaction completed: name={}, xid={}", transactionName, xid);
        } catch (Exception e) {
            log.error("TCC Confirm failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            try {
                tccAction.cancelAction(context);
            } catch (Exception ce) {
                log.error("TCC Cancel failed: name={}, xid={}", transactionName, xid, ce);
            }
            throw e;
        } finally {
            XID_HOLDER.remove();
        }

        return result;
    }

    @Override
    public TransactionType getCurrentType() {
        return TransactionType.TCC;
    }

    @Override
    public String getCurrentXid() {
        return XID_HOLDER.get();
    }
}
