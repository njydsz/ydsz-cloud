package com.njydsz.agent.domain.runtime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Agent 运行时会话值对象。
 *
 * <p>记录一次 Agent 执行会话的完整生命周期状态，包括执行元数据、进度信息、资源消耗。
 * 不可变对象，所有字段均为 final。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class RuntimeSession {

    private final String executionId;
    private final String conversationId;
    private final String agentCode;
    private final String agentType;
    private final String tenantId;
    private final String userId;
    private final String model;
    private final String status;
    private final String currentStep;
    private final int currentIteration;
    private final int maxIterations;
    private final int totalTokens;
    private final double costUsd;
    private final LocalDateTime startTime;
    private final LocalDateTime lastActiveTime;
    private final String source;
    private final String errorMessage;

    private RuntimeSession(Builder builder) {
        this.executionId = builder.executionId;
        this.conversationId = builder.conversationId;
        this.agentCode = builder.agentCode;
        this.agentType = builder.agentType;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.model = builder.model;
        this.status = builder.status;
        this.currentStep = builder.currentStep;
        this.currentIteration = builder.currentIteration;
        this.maxIterations = builder.maxIterations;
        this.totalTokens = builder.totalTokens;
        this.costUsd = builder.costUsd;
        this.startTime = builder.startTime;
        this.lastActiveTime = builder.lastActiveTime;
        this.source = builder.source;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getAgentType() {
        return agentType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getModel() {
        return model;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public String getSource() {
        return source;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 判断会话是否处于活跃状态（运行中或等待中）。
     *
     * @return true 如果状态为 RUNNING 或 WAITING
     */
    public boolean isActive() {
        return RuntimeSessionStatus.RUNNING.getCode().equals(status)
                || RuntimeSessionStatus.WAITING.getCode().equals(status);
    }

    /**
     * 计算会话已运行时长（毫秒）。
     *
     * @return 从 startTime 到 lastActiveTime 的毫秒数
     */
    public long getElapsedMillis() {
        if (startTime == null || lastActiveTime == null) {
            return 0L;
        }
        return Duration.between(startTime, lastActiveTime).toMillis();
    }

    public static final class Builder {
        private String executionId;
        private String conversationId;
        private String agentCode;
        private String agentType;
        private String tenantId;
        private String userId;
        private String model;
        private String status;
        private String currentStep;
        private int currentIteration;
        private int maxIterations;
        private int totalTokens;
        private double costUsd;
        private LocalDateTime startTime;
        private LocalDateTime lastActiveTime;
        private String source;
        private String errorMessage;

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder agentCode(String agentCode) {
            this.agentCode = agentCode;
            return this;
        }

        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder currentStep(String currentStep) {
            this.currentStep = currentStep;
            return this;
        }

        public Builder currentIteration(int currentIteration) {
            this.currentIteration = currentIteration;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder costUsd(double costUsd) {
            this.costUsd = costUsd;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder lastActiveTime(LocalDateTime lastActiveTime) {
            this.lastActiveTime = lastActiveTime;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public RuntimeSession build() {
            return new RuntimeSession(this);
        }
    }
}
