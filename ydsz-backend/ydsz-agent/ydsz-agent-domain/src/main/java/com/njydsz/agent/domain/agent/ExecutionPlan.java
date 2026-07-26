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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ExecutionPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String goal;
    private final List<PlanStep> steps;
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

    public void markExecuting() { this.status = PlanStatus.EXECUTING; }
    public void markCompleted() { this.status = PlanStatus.COMPLETED; }
    public void markFailed() { this.status = PlanStatus.FAILED; }

    public boolean isCompleted() {
        return steps.stream().allMatch(s -> s.getStatus() == PlanStep.StepStatus.COMPLETED);
    }

    public PlanStep getCurrentStep() {
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
        while (steps.size() > fromIndex) {
            steps.remove(fromIndex);
        }
        for (int i = 0; i < newSteps.size(); i++) {
            steps.add(new PlanStep(fromIndex + i,
                    newSteps.get(i).getDescription(),
                    newSteps.get(i).getAction()));
        }
    }

    public enum PlanStatus {
        PENDING, EXECUTING, COMPLETED, FAILED
    }

    public static final class PlanStep implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int index;
        private final String description;
        private final String action;
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

        public void markCompleted() { this.status = StepStatus.COMPLETED; }
        public void markFailed() { this.status = StepStatus.FAILED; }
        public void markExecuting() { this.status = StepStatus.EXECUTING; }

        public enum StepStatus {
            PENDING, EXECUTING, COMPLETED, FAILED
        }
    }
}
