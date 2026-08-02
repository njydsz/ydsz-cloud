package com.njydsz.agent.domain.agent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 执行计划值对象
 *
 * <p>Plan-and-Execute 模式中由 Planner 生成的步骤序列。
 *
 * <p><b>线程安全</b>：非线程安全。status 字段与 steps 列表可变，且 replaceRemainingSteps 会修改列表，
 * 须由单个执行线程独占访问或由外部加锁保护。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ExecutionPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 计划唯一标识 */
    private final String id;
    /** 执行目标 */
    private final String goal;
    /** 执行步骤列表 */
    private final List<PlanStep> steps;
    /** 计划状态 */
    private PlanStatus status;

    public ExecutionPlan(String id, String goal, List<PlanStep> steps) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.goal = Objects.requireNonNull(goal, "goal 不能为 null");
        this.steps = new ArrayList<>(Objects.requireNonNull(steps, "steps 不能为 null"));
        this.status = PlanStatus.PENDING;
    }

    public String getId() { return id; }
    public String getGoal() { return goal; }
    public List<PlanStep> getSteps() { return steps; }
    public PlanStatus getStatus() { return status; }

    /**
     * 将计划流转为执行中状态。
     *
     * <p>状态机：{@code PENDING -> EXECUTING}。方法不校验前置状态，重复调用等价于幂等赋值，
     * 由调度方保证只在开始执行时调用一次。
     */
    public void markExecuting() { this.status = PlanStatus.EXECUTING; }

    /**
     * 将计划流转为已完成状态。
     *
     * <p>状态机：{@code EXECUTING -> COMPLETED}。仅标记计划整体结果，
     * 不会级联修改 {@link PlanStep} 的状态；调用前应通过 {@link #isCompleted()} 确认所有步骤均已完成。
     */
    public void markCompleted() { this.status = PlanStatus.COMPLETED; }

    /**
     * 将计划流转为失败状态。
     *
     * <p>状态机：{@code EXECUTING -> FAILED}。终态，不支持回退；
     * 若需要重试应由上层重新生成 {@link ExecutionPlan} 实例，而非复用已失败的计划。
     */
    public void markFailed() { this.status = PlanStatus.FAILED; }

    public boolean isCompleted() {
        return steps.stream().allMatch(s -> s.getStatus() == PlanStep.StepStatus.COMPLETED);
    }

    public PlanStep getCurrentStep() {
        // 无 PENDING 步骤时返回 null，调用方据此判定计划已执行完毕并结束循环
        return steps.stream()
                .filter(s -> s.getStatus() == PlanStep.StepStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    /**
     * 替换从指定索引开始的剩余步骤（用于动态重规划）
     *
     * @param fromIndex 起始索引（包含）
     * @param newSteps  新的步骤列表
     */
    public void replaceRemainingSteps(int fromIndex, List<PlanStep> newSteps) {
        // 自 fromIndex 起逐个移除旧步骤，保留其前已完成的步骤不变
        while (steps.size() > fromIndex) {
            steps.remove(fromIndex);
        }
        // 用重规划产出的步骤按原索引接续追加，保证 steps 索引连续、序号不漂移
        for (int i = 0; i < newSteps.size(); i++) {
            steps.add(new PlanStep(fromIndex + i,
                    newSteps.get(i).getDescription(),
                    newSteps.get(i).getAction()));
        }
    }

    public enum PlanStatus {
        /** 待执行 */
        PENDING, 
        /** 执行中 */
        EXECUTING, 
        /** 已完成 */
        COMPLETED, 
        /** 已失败 */
        FAILED
    }

    public static final class PlanStep implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 步骤索引 */
        private final int index;
        /** 步骤描述 */
        private final String description;
        /** 执行动作 */
        private final String action;
        /** 步骤状态 */
        private StepStatus status;

        public PlanStep(int index, String description, String action) {
            this.index = index;
            this.description = Objects.requireNonNull(description, "description 不能为 null");
            this.action = action != null ? action : description;
            this.status = StepStatus.PENDING;
        }

        public int getIndex() { return index; }
        public String getDescription() { return description; }
        public String getAction() { return action; }
        public StepStatus getStatus() { return status; }

        /**
         * 将步骤流转为已完成状态。
         *
         * <p>状态机：{@code EXECUTING -> COMPLETED}。完成后该步骤不再被 {@link #getCurrentStep()} 选中，
         * 执行游标自动前移到下一个 {@code PENDING} 步骤。
         */
        public void markCompleted() { this.status = StepStatus.COMPLETED; }

        /**
         * 将步骤流转为失败状态。
         *
         * <p>状态机：{@code EXECUTING -> FAILED}。失败步骤同样脱离 {@code PENDING} 队列，
         * 因此不会被重复挑选；重试需通过 {@link #replaceRemainingSteps(int, List)} 重规划。
         */
        public void markFailed() { this.status = StepStatus.FAILED; }

        /**
         * 将步骤流转为执行中状态。
         *
         * <p>状态机：{@code PENDING -> EXECUTING}。用于标识当前占用执行线程的步骤，
         * 便于链路追踪定位卡住的步骤。
         */
        public void markExecuting() { this.status = StepStatus.EXECUTING; }

        public enum StepStatus {
            /** 待执行 */
            PENDING, 
            /** 执行中 */
            EXECUTING, 
            /** 已完成 */
            COMPLETED, 
            /** 已失败 */
            FAILED
        }
    }
}
