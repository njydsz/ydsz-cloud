package com.remisoft.common.seata.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.seata.api.SagaStep;
import com.remisoft.common.seata.config.SeataProperties;
import org.springframework.beans.factory.ObjectProvider;
import com.remisoft.common.seata.audit.TransactionAuditLogger;
import com.remisoft.common.seata.metrics.SeataMetrics;

import com.remisoft.common.seata.api.TransactionType;
import java.util.concurrent.Callable;
/**
 * SAGA 事务编排器
 *
 * <p>实现多步骤 SAGA 长事务编排：
 * <ol>
 *   <li>按顺序执行所有正向操作</li>
 *   <li>若某步骤失败，逆序执行已完成步骤的补偿操作</li>
 *   <li>补偿失败时按配置重试</li>
 * </ol>
 *
 * <p><b>P0-3 修复</b>：此前 {@code executeWithCompensation} 只是单步 try-catch，
 * 无法处理多步编排。现在通过 {@link SagaStep} 链 + 逆序补偿实现真正的 SAGA 语义。
 *
 * <p><b>P0-5 预留</b>：当前为同步编排，未来可扩展为异步状态机驱动
 * （通过 {@code SagaStateMachine} + 定时恢复）。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class SagaOrchestrator extends AbstractTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SeataProperties properties;

    /**
     * 构造 SAGA 事务编排器
     *
     * @param properties     Seata 配置属性
     * @param metricsProvider 指标采集提供者（可选）
     * @param auditProvider   审计日志提供者（可选）
     */
    public SagaOrchestrator(SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        super(metricsProvider, auditProvider);
        this.properties = properties;
    }

    /**
     * 执行 SAGA 事务
     *
     * <p>按顺序执行步骤链，失败时逆序补偿已完成步骤。
     *
     * <p><b>返回类型说明：</b>返回 {@link Object} 而非泛型 {@code <T>}，
     * 因为 {@code (T) lastResult} 在类型擦除后无法做运行时检查，
     * 必然导致未经检查的强制类型转换。调用方如需特定类型，请使用
     * {@link #execute(String, List, Class)} 显式传入结果类型。
     *
     * @param transactionName 事务名称
     * @param steps           SAGA 步骤链
     * @return 最后一步的返回值（类型为 {@link Object}）
     * @throws Exception 事务异常
     */
    public Object execute(String transactionName, List<? extends SagaStep<?>> steps) throws Exception {
        String xid = beginXid(transactionName);
        log.info("SAGA transaction started: name={}, xid={}, steps={}", transactionName, xid, steps.size());

        List<SagaStep<?>> completedSteps = new ArrayList<>();
        Object lastResult = null;

        try {
            for (int i = 0; i < steps.size(); i++) {
                SagaStep<?> step = steps.get(i);
                log.info("SAGA step {}/{} forward: name={}, xid={}", i + 1, steps.size(), step.getName(), xid);

                lastResult = step.getForwardAction().call();
                completedSteps.add(step);
                log.info("SAGA step {}/{} completed: name={}, xid={}", i + 1, steps.size(), step.getName(), xid);
            }

            log.info("SAGA transaction completed: name={}, xid={}", transactionName, xid);
            return lastResult;

        } catch (Exception e) {
            log.error("SAGA transaction failed at step {}/{}, executing compensation: name={}, xid={}",
                    completedSteps.size() + 1, steps.size(), transactionName, xid, e);

            List<SagaStep<?>> reverseSteps = new ArrayList<>(completedSteps);
            Collections.reverse(reverseSteps);

            for (SagaStep<?> step : reverseSteps) {
                if (!step.hasCompensation()) {
                    log.warn("SAGA step has no compensation, skipping: name={}, xid={}", step.getName(), xid);
                    continue;
                }
                compensateStepWithRetry(step, xid);
            }
            throw e;
        }
    }

    /**
     * 执行 SAGA 事务（带类型安全的结果转换）。
     *
     * <p>相比 {@link #execute(String, List)}，本方法通过 {@link Class#cast(Object)}
     * 在运行时验证最后一步返回值的类型，避免调用方进行未经检查的强制类型转换。
     *
     * @param transactionName 事务名称
     * @param steps           SAGA 步骤链
     * @param resultType      最后一步返回值的预期类型
     * @param <T>             最后一步的返回值类型
     * @return 最后一步的返回值
     * @throws Exception 事务异常或类型不匹配
     */
    public <T> T execute(String transactionName, List<? extends SagaStep<?>> steps, Class<T> resultType) throws Exception {
        Object result = execute(transactionName, steps);
        return resultType.cast(result);
    }

    private void compensateStepWithRetry(SagaStep<?> step, String xid) {
        int maxRetries = properties != null ? properties.getSagaMaxRetries() : 0;
        long intervalMs = properties != null ? properties.getSagaRetryIntervalMs() : 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                step.getCompensation().run();
                log.info("SAGA compensation completed: step={}, xid={}", step.getName(), xid);
                return;
            } catch (Exception e) {
                log.warn("SAGA compensation attempt {} failed: step={}, xid={}", attempt + 1, step.getName(), xid, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(intervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("SAGA compensation exhausted retries: step={}, xid={}", step.getName(), xid);
    }

    /**
     * 获取当前事务类型
     *
     * @return SAGA 事务类型
     */
    @Override
    public TransactionType getCurrentType() {
        return TransactionType.SAGA;
    }

    /**
     * 执行 SAGA 事务（单步模式，委托给 action）
     *
     * @param transactionName 事务名称
     * @param type            事务类型
     * @param action          业务操作
     * @param <T>             返回值类型
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        return action.call();
    }

    /**
     * 执行 SAGA 事务（带补偿动作的单步模式）
     *
     * @param transactionName 事务名称
     * @param action          正向操作
     * @param compensation    补偿操作
     * @param <T>             返回值类型
     * @return 业务操作返回值
     * @throws Exception 事务执行异常
     */
    @Override
    public <T> T executeWithCompensation(String transactionName, Callable<T> action, Runnable compensation) throws Exception {
        try { return action.call(); }
        catch (Exception e) { if (compensation != null) { compensation.run(); } throw e; }
    }
}