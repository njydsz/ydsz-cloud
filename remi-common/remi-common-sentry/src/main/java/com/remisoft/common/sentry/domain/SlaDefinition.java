package com.remisoft.common.sentry.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * SLA 定义
 *
 * <p>描述业务关键路径的 SLA 指标，包括阈值、目标和分解步骤。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class SlaDefinition {

    /** SLA 名称（如 project_creation） */
    private String name;

    /** SLA 描述 */
    private String description;

    /** P99 阈值（毫秒） */
    private long thresholdMillis = 500;

    /** SLA 目标（0.0~1.0，如 0.99 表示 99%） */
    private double target = 0.99;

    /** 评估窗口（秒） */
    private long evaluationWindowSeconds = 300;

    /** SLA 步骤分解 */
    private List<SlaStep> steps = new ArrayList<>();

    /**
     * SLA 步骤
     */
    @Data
    public static class SlaStep {

        /** 步骤名 */
        private String name;

        /** 超时阈值（毫秒） */
        private long timeoutMillis;

        /** 是否关键步骤（失败则整体 SLA 违反） */
        private boolean critical = true;

        public SlaStep() {
        }

        public SlaStep(String name, long timeoutMillis) {
            this.name = name;
            this.timeoutMillis = timeoutMillis;
        }

        public SlaStep(String name, long timeoutMillis, boolean critical) {
            this.name = name;
            this.timeoutMillis = timeoutMillis;
            this.critical = critical;
        }
    }

    /**
     * 添加步骤
     */
    public SlaDefinition addStep(String name, long timeoutMillis) {
        steps.add(new SlaStep(name, timeoutMillis));
        return this;
    }

    /**
     * 添加步骤
     */
    public SlaDefinition addStep(String name, long timeoutMillis, boolean critical) {
        steps.add(new SlaStep(name, timeoutMillis, critical));
        return this;
    }

    /**
     * 获取总超时
     */
    public Duration getTotalTimeout() {
        return Duration.ofMillis(steps.stream().mapToLong(SlaStep::getTimeoutMillis).sum());
    }
}
