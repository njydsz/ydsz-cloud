package com.njydsz.common.seata.impl;

import java.util.concurrent.Callable;
import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.metrics.SeataMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.seata.api.TransactionType;

/**
 * Seata AT 模式事务管理器
 *
 * <p>通过 Seata 的 {@code @GlobalTransactional} 语义实现自动补偿型分布式事务。
 * 当 Seata 在类路径时由 {@link com.njydsz.common.seata.config.SeataAutoConfiguration.SeataAtConfiguration}
 * 条件注册。
 *
 * <p><b>P0-1 修复</b>：此前 {@code SeataAutoConfiguration.SeataConfiguration} 是空壳，
 * 现在提供真实的 Seata AT 集成入口。
 *
 * <p>实现策略：由于 Seata 是 optional 依赖，此处不直接引用 Seata 的类
 * （避免 {@code NoClassDefFoundError}），而是通过 {@link GlobalTransactionTemplate}
 * 使用反射调用 Seata API。当 Seata 不在类路径时，{@code GlobalTransactionTemplate}
 * 不会被加载（由 {@code @ConditionalOnClass} 保护）。
 *
 * <p><b>注意</b>：当前版本为 Seata AT 模式的框架骨架。实际使用时，业务代码
 * 应直接使用 Seata 原生 {@code @GlobalTransactional} 注解，或通过
 * {@link #execute} 方法传入 {@link Callable}。此管理器负责：
 * <ul>
 *   <li>启动/提交/回滚全局事务</li>
 *   <li>管理 XID 上下文</li>
 *   <li>与 {@link com.njydsz.common.seata.api.XidPropagator} 协同传递 XID</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataTransactionManager extends AbstractTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(SeataTransactionManager.class);

    private final SeataGlobalTransactionExecutor globalExecutor;

    public SeataTransactionManager(SeataGlobalTransactionExecutor globalExecutor) {
        this.globalExecutor = globalExecutor;
    }

    public SeataTransactionManager(SeataGlobalTransactionExecutor globalExecutor,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        super(metricsProvider, auditProvider);
        this.globalExecutor = globalExecutor;
    }

    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        String xid = beginXid(transactionName);
        log.info("Seata AT transaction started: name={}, xid={}", transactionName, xid);
        try {
            T result = globalExecutor.executeInGlobalTransaction(action);
            log.info("Seata AT transaction committed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("Seata AT transaction rolled back: name={}, xid={}", transactionName, xid, e);
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
        log.info("Seata SAGA transaction started: name={}, xid={}", transactionName, xid);
        try {
            T result = globalExecutor.executeInGlobalTransaction(action);
            log.info("Seata SAGA transaction completed: name={}, xid={}", transactionName, xid);
            return result;
        } catch (Exception e) {
            log.error("Seata SAGA transaction failed, executing compensation: name={}, xid={}", transactionName, xid, e);
            try {
                compensation.run();
                log.info("Compensation completed: name={}, xid={}", transactionName, xid);
            } catch (Exception ce) {
                log.error("Compensation failed: name={}, xid={}", transactionName, xid, ce);
            }
            throw e;
        } finally {
            endXid();
        }
    }

    @Override
    public TransactionType getCurrentType() {
        return TransactionType.SEATA_AT;
    }
}
