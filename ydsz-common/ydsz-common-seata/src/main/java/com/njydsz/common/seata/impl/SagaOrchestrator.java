package com.njydsz.common.seata.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.seata.api.SagaStateMachineLog;
import com.njydsz.common.seata.api.SagaStateMachineLogStore;
import com.njydsz.common.seata.api.SagaStep;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.config.SeataProperties;
import com.njydsz.common.seata.metrics.SeataMetrics;

import com.njydsz.common.seata.api.TransactionType;
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
 * <p><b>P1-4 修复</b>：集成 {@link SagaStateMachineLogStore} 持久化状态机日志，
 * 支持服务崩溃后恢复未完成的 SAGA 事务。
 *
 * <p><b>P0-5 预留</b>：当前为同步编排，可扩展为异步状态机驱动。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SagaOrchestrator extends AbstractTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SeataProperties properties;
    private final ObjectProvider<SagaStateMachineLogStore> stateMachineLogStoreProvider;

    /**
     * 构造 SAGA 事务编排器
     *
     * @param properties              Seata 配置属性
     * @param metricsProvider         指标采集提供者（可选）
     * @param auditProvider           审计日志提供者（可选）
     */
    public SagaOrchestrator(SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        this(properties, metricsProvider, auditProvider, null);
    }

    /**
     * 构造 SAGA 事务编排器（带状态机日志存储）
     *
     * @param properties              Seata 配置属性
     * @param metricsProvider         指标采集提供者（可选）
     * @param auditProvider           审计日志提供者（可选）
     * @param stateMachineLogStoreProvider SAGA 状态机日志存储提供者（可选）
     */
    public SagaOrchestrator(SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider,
            ObjectProvider<SagaStateMachineLogStore> stateMachineLogStoreProvider) {
        super(metricsProvider, auditProvider);
        this.properties = properties;
        this.stateMachineLogStoreProvider = stateMachineLogStoreProvider;
    }

    /**
     * 执行 SAGA 事务
     *
     * <p>按顺序执行步骤链，失败时逆序补偿已完成步骤。
     * 支持状态机持久化（当配置 SagaStateMachineLogStore 时）。
     *
     * @param transactionName 事务名称
     * @param steps           SAGA 步骤链
     * @return 最后一步的返回值
     * @throws Exception 事务异常
     */
    public Object execute(String transactionName, List<? extends SagaStep<?>> steps) throws Exception {
        String xid = beginXid(transactionName);
        log.info("SAGA transaction started: name={}, xid={}, steps={}", transactionName, xid, steps.size());

        // 创建状态机日志（如存储可用）
        SagaStateMachineLog stateLog = createStateMachineLog(transactionName, xid, steps);

        List<SagaStep<?>> completedSteps = new ArrayList<>();
        Object lastResult = null;

        try {
            for (int i = 0; i < steps.size(); i++) {
                SagaStep<?> step = steps.get(i);
                log.info("SAGA step {}/{} forward: name={}, xid={}", i + 1, steps.size(), step.getName(), xid);

                // 更新当前步骤状态
                if (stateLog != null) {
                    stateLog.setCurrentStepIndex(i);
                    stateLog.setCurrentStepName(step.getName());
                    stateLog.setState(SagaStateMachineLog.SagaState.EXECUTING);
                    persistStateMachineLog(stateLog);
                }

                lastResult = step.getForwardAction().call();
                completedSteps.add(step);
                log.info("SAGA step {}/{} completed: name={}, xid={}", i + 1, steps.size(), step.getName(), xid);
            }

            // 全部步骤执行成功
            if (stateLog != null) {
                stateLog.setState(SagaStateMachineLog.SagaState.SUCCEEDED);
                persistStateMachineLog(stateLog);
            }

            log.info("SAGA transaction completed: name={}, xid={}", transactionName, xid);
            return lastResult;

        } catch (Exception e) {
            log.error("SAGA transaction failed at step {}/{}, executing compensation: name={}, xid={}",
                    completedSteps.size() + 1, steps.size(), transactionName, xid, e);

            // 更新状态为失败
            if (stateLog != null) {
                stateLog.setState(SagaStateMachineLog.SagaState.FAILED);
                stateLog.setLastError(e.getMessage());
                persistStateMachineLog(stateLog);
            }

            // 执行补偿
            boolean compensationSuccess = executeCompensation(completedSteps, xid, stateLog);

            if (stateLog != null && compensationSuccess) {
                stateLog.setState(SagaStateMachineLog.SagaState.COMPENSATED);
                persistStateMachineLog(stateLog);
            } else if (stateLog != null) {
                stateLog.setState(SagaStateMachineLog.SagaState.COMPENSATION_FAILED);
                persistStateMachineLog(stateLog);
            }

            throw e;
        }
    }

    /**
     * 执行补偿操作（逆序）
     *
     * @param completedSteps 已完成的步骤
     * @param xid            全局事务 ID
     * @param stateLog       状态机日志
     * @return 补偿是否全部成功
     */
    private boolean executeCompensation(List<SagaStep<?>> completedSteps, String xid, SagaStateMachineLog stateLog) {
        List<SagaStep<?>> reverseSteps = new ArrayList<>(completedSteps);
        Collections.reverse(reverseSteps);

        if (stateLog != null) {
            stateLog.setState(SagaStateMachineLog.SagaState.COMPENSATING);
            persistStateMachineLog(stateLog);
        }

        boolean allSuccess = true;
        for (SagaStep<?> step : reverseSteps) {
            if (!step.hasCompensation()) {
                log.warn("SAGA step has no compensation, skipping: name={}, xid={}", step.getName(), xid);
                continue;
            }
            boolean success = compensateStepWithRetry(step, xid);
            if (!success) {
                allSuccess = false;
                log.error("SAGA compensation failed after retries: step={}, xid={}", step.getName(), xid);
            }
        }
        return allSuccess;
    }

    /**
     * 执行 SAGA 事务（带类型安全的结果转换）
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

    /**
     * 创建状态机日志
     */
    private SagaStateMachineLog createStateMachineLog(String transactionName, String xid,
            List<? extends SagaStep<?>> steps) {
        SagaStateMachineLogStore store = getSagaStateMachineLogStore();
        if (store == null) {
            return null;
        }
        try {
            SagaStateMachineLog log = new SagaStateMachineLog(xid, transactionName);
            // 记录步骤快照（简化实现，实际可序列化完整步骤信息）
            log.setStepsSnapshot("steps:" + steps.size());
            store.save(log);
            return log;
        } catch (Exception e) {
            log.warn("Failed to persist SAGA state machine log, continuing without persistence", e);
            return null;
        }
    }

    /**
     * 持久化状态机日志
     */
    private void persistStateMachineLog(SagaStateMachineLog log) {
        SagaStateMachineLogStore store = getSagaStateMachineLogStore();
        if (store != null && log != null) {
            try {
                store.save(log);
            } catch (Exception e) {
                log.warn("Failed to persist SAGA state: xid={}", log.getXid(), e);
            }
        }
    }

    /**
     * 获取 SAGA 状态机日志存储（如有）
     */
    private SagaStateMachineLogStore getSagaStateMachineLogStore() {
        return stateMachineLogStoreProvider != null ? stateMachineLogStoreProvider.getIfAvailable() : null;
    }

    private boolean compensateStepWithRetry(SagaStep<?> step, String xid) {
        int maxRetries = properties != null ? properties.getSagaMaxRetries() : 0;
        long intervalMs = properties != null ? properties.getSagaRetryIntervalMs() : 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                step.getCompensation().run();
                log.info("SAGA compensation completed: step={}, xid={}", step.getName(), xid);
                return true;
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
        return false;
    }

    @Override
    public TransactionType getCurrentType() {
        return TransactionType.SAGA;
    }

    @Override
    public <T> T execute(String transactionName, TransactionType type, Callable<T> action) throws Exception {
        return action.call();
    }

    @Override
    public <T> T executeWithCompensation(String transactionName, Callable<T> action, Runnable compensation) throws Exception {
        try { return action.call(); }
        catch (Exception e) { if (compensation != null) { compensation.run(); } throw e; }
    }
}
