package com.njydsz.agent.domain.teamrun;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Team Run 成员值对象。
 *
 * <p>记录单个 Agent 在 Team Run 中的执行信息和状态。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public final class TeamRunMember {

    private final String memberId;
    private final String agentCode;
    private final String agentName;
    private final String role;
    private final int executionOrder;
    private final String inputContext;
    private final String outputResult;
    private final String executionId;
    private final TeamRunMemberStatus status;
    private final String errorMessage;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final Map<String, Object> metadata;

    private TeamRunMember(Builder builder) {
        this.memberId = builder.memberId;
        this.agentCode = builder.agentCode;
        this.agentName = builder.agentName;
        this.role = builder.role;
        this.executionOrder = builder.executionOrder;
        this.inputContext = builder.inputContext;
        this.outputResult = builder.outputResult;
        this.executionId = builder.executionId;
        this.status = builder.status;
        this.errorMessage = builder.errorMessage;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getMemberId() {
        return memberId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getRole() {
        return role;
    }

    public int getExecutionOrder() {
        return executionOrder;
    }

    public String getInputContext() {
        return inputContext;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public String getExecutionId() {
        return executionId;
    }

    public TeamRunMemberStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 创建更新状态后的不可变副本。
     *
     * @param newStatus 新状态
     * @return 更新后的 TeamRunMember 实例
     */
    public TeamRunMember withStatus(TeamRunMemberStatus newStatus) {
        return new Builder()
                .memberId(this.memberId)
                .agentCode(this.agentCode)
                .agentName(this.agentName)
                .role(this.role)
                .executionOrder(this.executionOrder)
                .inputContext(this.inputContext)
                .outputResult(this.outputResult)
                .executionId(this.executionId)
                .status(newStatus)
                .errorMessage(this.errorMessage)
                .startedAt(this.startedAt)
                .completedAt(this.completedAt)
                .metadata(this.metadata)
                .build();
    }

    /**
     * 创建更新执行结果后的不可变副本。
     *
     * @param outputResult 执行输出结果
     * @param executionId  执行 ID
     * @return 更新后的 TeamRunMember 实例
     */
    public TeamRunMember withResult(String outputResult, String executionId) {
        return new Builder()
                .memberId(this.memberId)
                .agentCode(this.agentCode)
                .agentName(this.agentName)
                .role(this.role)
                .executionOrder(this.executionOrder)
                .inputContext(this.inputContext)
                .outputResult(outputResult)
                .executionId(executionId)
                .status(TeamRunMemberStatus.COMPLETED)
                .errorMessage(null)
                .startedAt(this.startedAt)
                .completedAt(LocalDateTime.now())
                .metadata(this.metadata)
                .build();
    }

    /**
     * 创建更新错误信息后的不可变副本。
     *
     * @param errorMessage 错误信息
     * @return 更新后的 TeamRunMember 实例
     */
    public TeamRunMember withError(String errorMessage) {
        return new Builder()
                .memberId(this.memberId)
                .agentCode(this.agentCode)
                .agentName(this.agentName)
                .role(this.role)
                .executionOrder(this.executionOrder)
                .inputContext(this.inputContext)
                .outputResult(this.outputResult)
                .executionId(this.executionId)
                .status(TeamRunMemberStatus.FAILED)
                .errorMessage(errorMessage)
                .startedAt(this.startedAt)
                .completedAt(LocalDateTime.now())
                .metadata(this.metadata)
                .build();
    }

    public static final class Builder {
        private String memberId;
        private String agentCode;
        private String agentName;
        private String role;
        private int executionOrder;
        private String inputContext;
        private String outputResult;
        private String executionId;
        private TeamRunMemberStatus status;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Map<String, Object> metadata;

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder agentCode(String agentCode) {
            this.agentCode = agentCode;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder executionOrder(int executionOrder) {
            this.executionOrder = executionOrder;
            return this;
        }

        public Builder inputContext(String inputContext) {
            this.inputContext = inputContext;
            return this;
        }

        public Builder outputResult(String outputResult) {
            this.outputResult = outputResult;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder status(TeamRunMemberStatus status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder startedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TeamRunMember build() {
            return new TeamRunMember(this);
        }
    }
}
