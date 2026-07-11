package com.njydsz.pmis.common.tx;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Saga 分布式事务编排器（P0-4 分布式事务：Saga 补偿模式）
 *
 * <p>用于编排跨服务的多步骤事务，每个步骤都有对应的补偿动作。
 * 当某个步骤失败时，按逆序执行已完成步骤的补偿动作。
 *
 * <h3>使用示例</h3>
 * <pre>
 * SagaOrchestrator saga = SagaOrchestrator.create("项目立项流程")
 *     .step("创建项目")
 *         .action(() -&gt; projectService.create(dto))
 *         .compensate(projectId -&gt; projectService.delete(projectId))
 *     .step("初始化 WBS")
 *         .action(() -&gt; wbsService.init(projectId))
 *         .compensate(wbsId -&gt; wbsService.delete(wbsId))
 *     .step("发送通知")
 *         .action(() -&gt; messageClient.send(request));
 *
 * SagaResult result = saga.execute();
 * if (result.isSuccess()) {
 *     log.info("Saga 成功");
 * } else {
 *     log.error("Saga 失败: {}", result.getError());
 * }
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>同步执行，非事件驱动（适合简单场景）</li>
 *   <li>补偿动作必须幂等（可能被多次调用）</li>
 *   <li>补偿动作失败时记录日志，不阻断后续补偿</li>
 *   <li>每个步骤的 action 返回结果会传递给 compensate 作为参数</li>
 * </ul>
 *
 * @author ydsydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class SagaOrchestrator {

    /** Saga 名称 */
    private final String name;
    /** 步骤列表 */
    private final List<SagaStep<?>> steps = new ArrayList<>();

    private SagaOrchestrator(String name) {
        this.name = name;
    }

    /**
     * 创建 Saga 编排器
     *
     * @param name Saga 名称（用于日志和追踪）
     * @return Saga 编排器
     */
    public static SagaOrchestrator create(String name) {
        return new SagaOrchestrator(name);
    }

    /**
     * 添加一个步骤
     *
     * @param stepName 步骤名称
     * @return 步骤构建器
     */
    public <T> SagaStep<T> step(String stepName) {
        SagaStep<T> step = new SagaStep<>(stepName, this);
        steps.add(step);
        return step;
    }

    /**
     * 执行 Saga 事务
     *
     * @return 执行结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SagaResult execute() {
        log.info("[Saga] 开始执行: {} (共 {} 步)", name, steps.size());

        List<SagaStep<?>> completedSteps = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            try {
                log.debug("[Saga] 执行步骤 {}/{}: {}", i + 1, steps.size(), step.getName());
                Object result = step.execute();
                step.setResult(result);
                completedSteps.add(step);
            } catch (Exception e) {
                log.error("[Saga] 步骤 {} 失败: {} error={}", i + 1, step.getName(), e.getMessage(), e);
                // 执行补偿（逆序）
                compensate(completedSteps);
                return SagaResult.failure(step.getName(), e.getMessage());
            }
        }

        log.info("[Saga] 执行成功: {}", name);
        return SagaResult.success();
    }

    /**
     * 逆序执行补偿动作
     *
     * @param completedSteps 已完成的步骤列表
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void compensate(List<SagaStep<?>> completedSteps) {
        log.warn("[Saga] 开始补偿，共 {} 步需补偿", completedSteps.size());
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            SagaStep step = completedSteps.get(i);
            try {
                log.debug("[Saga] 补偿步骤 {}/{}: {}", completedSteps.size() - i, completedSteps.size(), step.getName());
                step.compensate();
            } catch (Exception e) {
                // 补偿失败记录日志，不阻断后续补偿
                log.error("[Saga] 补偿步骤失败: {} error={}", step.getName(), e.getMessage(), e);
            }
        }
        log.warn("[Saga] 补偿完成");
    }

    /**
     * Saga 步骤
     *
     * @param <T> action 返回类型
     */
    @Slf4j
    @Data
    public static class SagaStep<T> {
        private final String name;
        private final SagaOrchestrator orchestrator;
        private Supplier<T> action;
        private Consumer<T> compensateAction;
        private T result;

        public SagaStep(String name, SagaOrchestrator orchestrator) {
            this.name = name;
            this.orchestrator = orchestrator;
        }

        /**
         * 设置执行动作
         *
         * @param action 执行动作
         * @return 当前步骤（链式调用）
         */
        public SagaStep<T> action(Supplier<T> action) {
            this.action = action;
            return this;
        }

        /**
         * 设置补偿动作
         *
         * @param compensate 补偿动作，参数为 action 的返回值
         * @return 当前步骤（链式调用）
         */
        public SagaStep<T> compensate(Consumer<T> compensate) {
            this.compensateAction = compensate;
            return this;
        }

        /**
         * 结束当前步骤，返回编排器
         *
         * @return Saga 编排器
         */
        public SagaOrchestrator end() {
            return orchestrator;
        }

        public T execute() {
            if (action == null) {
                return null;
            }
            return action.get();
        }

        public void compensate() {
            if (compensateAction != null && result != null) {
                compensateAction.accept(result);
            }
        }
    }

    /**
     * Saga 执行结果
     */
    @Data
    public static class SagaResult {
        private final boolean success;
        private final String failedStep;
        private final String error;

        public static SagaResult success() {
            return new SagaResult(true, null, null);
        }

        public static SagaResult failure(String failedStep, String error) {
            return new SagaResult(false, failedStep, error);
        }
    }
}
