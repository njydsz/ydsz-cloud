package com.njydsz.pmis.common.seata.impl;

import java.util.concurrent.Callable;

/**
 * Seata 全局事务执行器接口
 *
 * <p>抽象 Seata AT 模式的全局事务开启/提交/回滚逻辑，使 {@link SeataTransactionManager}
 * 不直接依赖 Seata 类（Seata 是 optional 依赖），避免 {@code NoClassDefFoundError}。
 *
 * <p>实现类 {@link SeataGlobalTransactionExecutor} 通过反射调用 Seata API，
 * 仅当 Seata 在类路径时由 {@code @ConditionalOnClass} 条件注册。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public interface GlobalTransactionExecutor {

    /**
     * 在 Seata 全局事务上下文中执行业务操作
     *
     * @param action 业务操作
     * @param <T>    返回值类型
     * @return 业务操作返回值
     * @throws Exception 全局事务异常
     */
    <T> T executeInGlobalTransaction(Callable<T> action) throws Exception;

    /**
     * 获取当前全局事务 XID
     *
     * @return 当前 XID，无全局事务上下文时返回 null
     */
    String getCurrentGlobalXid();
}
