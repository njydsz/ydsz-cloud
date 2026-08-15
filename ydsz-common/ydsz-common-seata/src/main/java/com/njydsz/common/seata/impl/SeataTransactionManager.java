package com.njydsz.common.seata.impl;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.metrics.SeataMetrics;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionContext;

/**
 * Seata AT 模式事务管理器（适配器实现）
 *
 * <p>基于 Seata 2.x 原生 API 实现自动补偿型分布式事务。
 * 当 Seata 在类路径时由 {@link com.njydsz.common.seata.config.SeataAutoConfiguration.SeataAtConfiguration}
 * 条件注册。
 *
 * <p>设计说明：本管理器作为 ydsz-common-seata 框架与 Seata 原生 API 之间的适配器，
 * 提供统一的 {@link com.njydsz.common.seata.api.DistributedTransactionManager} 接口。
 * 业务代码推荐直接使用 Seata 原生 {@code @GlobalTransactional} 注解以获得最佳体验。
 *
 * <p>职责：
 * <ul>
 *   <li>启动/提交/回滚全局事务（委托 Seata 原生 API）</li>
 *   <li>管理 XID 上下文（与 Seata RootContext 协同）</li>
 *   <li>提供可观测性支持（指标、审计日志）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataTransactionManager extends AbstractTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(SeataTransactionManager.class);

    /**
     * 构造 Seata AT 模式事务管理器（基础模式）
     */
    public SeataTransactionManager() {
        super();
    }

    /**
     * 构造 Seata AT 模式事务管理器（带指标和审计）
     *
     * @param metricsProvider 指标采集提供者（可选）
     * @param auditProvider   审计日志提供者（可选）
     */
    public SeataTransactionManager(ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        super(metricsProvider, auditProvider);
    }

    /**
     * 执行 Seata AT 分布式事务
     *
     * <p>通过 Seata {@link GlobalTransactionContext} 操作全局事务生命周期。
     * 业务操作成功时提交全局事务，失败时回滚。
     *
     * @param transactionName 事务名称（用于日志和监控）
     * @param type            事务类型
     * @param action          业务操作
     * @param <T>             返回值
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        String xid = beginXid(transactionName);
        GlobalTransaction globalTx = null;
        log.info("Seata AT transaction started: name={}, xid={}", transactionName, xid);

        try {
            globalTx = GlobalTransactionContext.getCurrentOrCreate();
            globalTx.begin();

            String seataXid = RootContext.getXID();
            log.debug("Seata global transaction begun: name={}, xid={}, seataXid={}",
                    transactionName, xid, seataXid);

            T result = action.call();

            globalTx.commit();
            log.info("Seata AT transaction committed: name={}, xid={}, seataXid={}", transactionName, xid, seataXid);
            endXid();
            RootContext.unbind();
            return result;
        } catch (Exception e) {
            log.error("Seata AT transaction failed: name={}, xid={}, rolling back", transactionName, xid, e);
            if (globalTx != null) {
                try {
                    globalTx.rollback();
                    log.info("Seata AT transaction rolled back: name={}, xid={}", transactionName, xid);
                } catch (Exception re) {
                    log.error("Seata AT transaction rollback failed: name={}, xid={}", transactionName, xid, re);
                }
            }
            endXid(e);
            RootContext.unbind();
            throw e;
        }
    }

    /**
     * 执行 Seata AT 分布式事务（带补偿动作，SAGA 模式兼容）
     *
     * <p>当 Seata AT 全局事务回滚后，执行业务层提供的补偿动作。
     * 注意：Seata AT 模式下补偿通常不需要（undo_log 已自动回滚），此方法主要
     * 用于需要额外业务补偿的场景（如释放分布式锁、发送通知等）。
     *
     * @param transactionName 事务名称
     * @param action          正向操作
     * @param compensation    补偿操作
     * @param <T>             返回值
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    @Override
    public <T> T executeWithCompensation(String transactionName,
                                          Callable<T> action,
                                          Runnable compensation) throws Exception {
        String xid = beginXid(transactionName);
        GlobalTransaction globalTx = null;
        log.info("Seata AT+SAGA transaction started: name={}, xid={}", transactionName, xid);

        try {
            globalTx = GlobalTransactionContext.getCurrentOrCreate();
            globalTx.begin();

            T result = action.call();

            globalTx.commit();
            log.info("Seata AT+SAGA transaction completed: name={}, xid={}", transactionName, xid);
            endXid();
            RootContext.unbind();
            return result;
        } catch (Exception e) {
            log.error("Seata AT+SAGA transaction failed: name={}, xid={}, executing compensation",
                    transactionName, xid, e);
            if (globalTx != null) {
                try {
                    globalTx.rollback();
                } catch (Exception re) {
                    log.error("Seata rollback failed before compensation: name={}, xid={}", transactionName, xid, re);
                }
            }
            if (compensation != null) {
                try {
                    compensation.run();
                    log.info("Business compensation completed: name={}, xid={}", transactionName, xid);
                } catch (Exception ce) {
                    log.error("Business compensation failed: name={}, xid={}", transactionName, xid, ce);
                }
            }
            endXid(e);
            RootContext.unbind();
            throw e;
        }
    }

    /**
     * 获取当前 Seata AT 事务类型
     *
     * @return Seata AT 事务类型
     */
    @Override
    public TransactionType getCurrentType() {
        return TransactionType.SEATA_AT;
    }

    /**
     * 获取当前 Seata 全局事务的 XID
     *
     * <p>此方法用于将 ydsz 框架的 XID 上下文与 Seata RootContext 同步。
     *
     * @return Seata 全局事务 XID，无活跃事务时返回 null
     */
    public String getCurrentGlobalXid() {
        try {
            return RootContext.getXID();
        } catch (Exception e) {
            log.debug("Failed to get current Seata XID from RootContext", e);
            return null;
        }
    }
}
