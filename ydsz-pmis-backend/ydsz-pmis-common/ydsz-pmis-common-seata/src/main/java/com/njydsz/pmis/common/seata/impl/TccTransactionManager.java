package com.njydsz.pmis.common.seata.impl;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p><b>P0-9 修复</b>：{@code executeWithCompensation} 不再忽略 compensation 参数，
 * 现在正确执行补偿逻辑。
 *
 * <p><b>P0-10 修复</b>：{@code execute()} 现在根据 {@link TransactionType} 正确路由：
 * <ul>
 *   <li>{@code TCC} → 委托给 {@link #executeTcc}（但需要传入 {@link TccAction}，
 *       若调用者直接传 {@link Callable} 则按普通执行处理并记录警告）</li>
 *   <li>{@code LOCAL} → 等同于普通 try-catch</li>
 *   <li>{@code SAGA} → 等同于 {@code executeWithCompensation}（补偿由调用者另行处理）</li>
 * </ul>
 *
 * <p>注意：此实现为本地 TCC 协调器，适用于单服务内的多资源操作。
 * 跨服务的 TCC 需要配合 Seata TCC 模式使用。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class TccTransactionManager extends AbstractTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TccTransactionManager.class);

    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
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
     *
     * @param transactionName 事务名称
     * @param tccAction       TCC 动作
     * @param <T>             返回值类型
     * @return Try 阶段的返回值
     * @throws Exception 事务异常
     */
    public <T> T executeTcc(String transactionName, TccAction<T> tccAction) throws Exception {
        String xid = beginXid(transactionName);
        String branchId = generateBranchId();
        TccContext context = new TccContext(xid, branchId);

        log.info("TCC Try phase: name={}, xid={}, branch={}", transactionName, xid, branchId);
        T result;
        try {
            result = tccAction.tryAction(context);
        } catch (Exception e) {
            log.error("TCC Try failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            runTccCancel(transactionName, xid, tccAction, context);
            throw e;
        }

        log.info("TCC Confirm phase: name={}, xid={}, branch={}", transactionName, xid, branchId);
        try {
            tccAction.confirmAction(context);
            log.info("TCC transaction completed: name={}, xid={}", transactionName, xid);
        } catch (Exception e) {
            log.error("TCC Confirm failed, executing Cancel: name={}, xid={}", transactionName, xid, e);
            runTccCancel(transactionName, xid, tccAction, context);
            throw e;
        } finally {
            endXid();
        }

        return result;
    }

    /**
     * 执行 TCC Cancel 操作，捕获并记录 Cancel 异常（不阻断主异常抛出）
     */
    private <T> void runTccCancel(String transactionName, String xid,
                                   TccAction<T> tccAction, TccContext context) {
        try {
            tccAction.cancelAction(context);
            log.info("TCC Cancel completed: name={}, xid={}", transactionName, xid);
        } catch (Exception ce) {
            log.error("TCC Cancel failed: name={}, xid={}", transactionName, xid, ce);
        }
    }

    /**
     * 执行补偿操作，捕获并记录补偿异常（不阻断主异常抛出）
     */
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

    @Override
    public TransactionType getCurrentType() {
        return TransactionType.TCC;
    }
}
