package com.njydsz.pmis.common.seata.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.seata.api.SagaStep;
import com.njydsz.pmis.common.seata.config.SeataProperties;

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
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SeataProperties properties;

    public SagaOrchestrator(SeataProperties properties) {
        this.properties = properties;
    }

    /**
     * 执行 SAGA 事务
     *
     * <p>按顺序执行步骤链，失败时逆序补偿已完成步骤。
     *
     * @param transactionName 事务名称
     * @param steps           SAGA 步骤链
     * @param <T>             最后一步的返回值类型
     * @return 最后一步的返回值
     * @throws Exception 事务异常
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String transactionName, List<? extends SagaStep<?>> steps) throws Exception {
        String xid = java.util.UUID.randomUUID().toString();
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
            return (T) lastResult;

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
}
