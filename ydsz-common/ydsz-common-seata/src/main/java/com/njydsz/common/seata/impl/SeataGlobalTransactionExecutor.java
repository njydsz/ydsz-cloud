package com.njydsz.common.seata.impl;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seata 全局事务执行器实现（反射调用 Seata API）
 *
 * <p>通过反射调用 Seata 2.x 的 {@code GlobalTransactionContext} API，
 * 避免编译期对 Seata 类的硬依赖（Seata 是 optional 依赖）。
 *
 * <p>仅当类路径存在 {@code org.apache.seata.tm.api.GlobalTransactionContext} 时
 * 由 {@link com.njydsz.common.seata.config.SeataAutoConfiguration.SeataAtConfiguration}
 * 条件注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataGlobalTransactionExecutor  {

    private static final Logger log = LoggerFactory.getLogger(SeataGlobalTransactionExecutor.class);

    private static final String SEATA_GLOBAL_TX_CONTEXT = "org.apache.seata.tm.api.GlobalTransactionContext";
    private static final String SEATA_GLOBAL_TRANSACTION = "org.apache.seata.tm.api.GlobalTransaction";

    private final Method beginMethod;
    private final Method commitMethod;
    private final Method rollbackMethod;
    private final Method getXidMethod;
    private final Method getCurrentMethod;

    /**
     * 构造 Seata 全局事务执行器，通过反射加载 Seata 2.x API
     *
     * @throws ClassNotFoundException Seata 类不在类路径时抛出
     * @throws NoSuchMethodException   Seata API 方法不存在时抛出
     */
    public SeataGlobalTransactionExecutor() throws ClassNotFoundException, NoSuchMethodException {
        Class<?> globalTxContextClass = Class.forName(SEATA_GLOBAL_TX_CONTEXT);
        Class<?> globalTxClass = Class.forName(SEATA_GLOBAL_TRANSACTION);

        this.getCurrentMethod = globalTxContextClass.getMethod("getCurrent");
        this.beginMethod = globalTxClass.getMethod("begin", int.class);
        this.commitMethod = globalTxClass.getMethod("commit");
        this.rollbackMethod = globalTxClass.getMethod("rollback");
        this.getXidMethod = globalTxClass.getMethod("getXid");

        log.info("Seata GlobalTransactionExecutor initialized (Seata 2.x detected)");
    }

    /**
     * 在 Seata 全局事务中执行业务操作
     *
     * <p>自动处理全局事务的 begin/commit/rollback 生命周期。
     *
     * @param action 业务操作
     * @param <T>    返回值类型
     * @return 业务操作返回值
     * @throws Exception 业务异常或全局事务异常
     */
    public <T> T executeInGlobalTransaction(Callable<T> action) throws Exception {
        Object globalTx = getCurrentMethod.invoke(null);
        if (globalTx == null) {
            throw new IllegalStateException("No active Seata GlobalTransaction. "
                    + "Ensure @GlobalTransactional is on the caller method or "
                    + "ydsz.seata.seata-tx-service-group is configured correctly.");
        }

        beginMethod.invoke(globalTx, 60000);
        log.debug("Seata global transaction begun");
        try {
            T result = action.call();
            commitMethod.invoke(globalTx);
            log.debug("Seata global transaction committed");
            return result;
        } catch (Exception e) {
            rollbackMethod.invoke(globalTx);
            log.debug("Seata global transaction rolled back");
            throw e;
        }
    }

    /**
     * 获取当前全局事务的 XID
     *
     * @return 当前全局事务 ID，无活跃事务时返回 null
     */
    public String getCurrentGlobalXid() {
        try {
            Object globalTx = getCurrentMethod.invoke(null);
            if (globalTx == null) {
                return null;
            }
            Object xid = getXidMethod.invoke(globalTx);
            return xid != null ? xid.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to get current Seata XID", e);
            return null;
        }
    }
}
