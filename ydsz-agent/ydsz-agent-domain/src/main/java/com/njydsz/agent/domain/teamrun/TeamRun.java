package com.njydsz.agent.domain.teamrun;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Team Run 聚合根。
 *
 * <p>代表一次多 Agent 协作执行，包含协作模式、成员列表、整体状态等。
 * 借鉴 MateClaw 的 Team Runs 设计，支持顺序、并行、层级、协商等多种协作模式。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class TeamRun {

    private final String teamRunId;
    private final String tenantId;
    private final String title;
    private final String description;
    private final TeamRunPattern pattern;
    private final List<TeamRunMember> members;
    private final TeamRunStatus status;
    private final String initiatedBy;
    private final String finalResult;
    private final LocalDateTime createdAt;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final Map<String, Object> context;

    private TeamRun(Builder builder) {
        this.teamRunId = builder.teamRunId;
        this.tenantId = builder.tenantId;
        this.title = builder.title;
        this.description = builder.description;
        this.pattern = builder.pattern;
        this.members = builder.members != null ? List.copyOf(builder.members) : List.of();
        this.status = builder.status;
        this.initiatedBy = builder.initiatedBy;
        this.finalResult = builder.finalResult;
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.context = builder.context != null ? Map.copyOf(builder.context) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTeamRunId() {
        return teamRunId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TeamRunPattern getPattern() {
        return pattern;
    }

    public List<TeamRunMember> getMembers() {
        return members;
    }

    public TeamRunStatus getStatus() {
        return status;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public String getFinalResult() {
        return finalResult;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * 创建添加成员后的不可变副本。
     *
     * @param member 待添加的成员
     * @return 新的 TeamRun 实例
     */
    public TeamRun withMember(TeamRunMember member) {
        List<TeamRunMember> newMembers = new ArrayList<>(this.members);
        newMembers.add(member);
        return new Builder()
                .teamRunId(this.teamRunId)
                .tenantId(this.tenantId)
                .title(this.title)
                .description(this.description)
                .pattern(this.pattern)
                .members(newMembers)
                .status(this.status)
                .initiatedBy(this.initiatedBy)
                .finalResult(this.finalResult)
                .createdAt(this.createdAt)
                .startedAt(this.startedAt)
                .completedAt(this.completedAt)
                .context(this.context)
                .build();
    }

    /**
     * 创建更新状态后的不可变副本。
     *
     * @param newStatus 新状态
     * @return 新的 TeamRun 实例
     */
    public TeamRun withStatus(TeamRunStatus newStatus) {
        LocalDateTime now = LocalDateTime.now();
        return new Builder()
                .teamRunId(this.teamRunId)
                .tenantId(this.tenantId)
                .title(this.title)
                .description(this.description)
                .pattern(this.pattern)
                .members(this.members)
                .status(newStatus)
                .initiatedBy(this.initiatedBy)
                .finalResult(this.finalResult)
                .createdAt(this.createdAt)
                .startedAt(this.status == TeamRunStatus.CREATED ? now : this.startedAt)
                .completedAt(newStatus.isTerminal() ? now : this.completedAt)
                .context(this.context)
                .build();
    }

    /**
     * 创建更新成员后的不可变副本。
     *
     * @param memberId  成员 ID
     * @param newMember 新的成员信息
     * @return 新的 TeamRun 实例
     */
    public TeamRun withUpdatedMember(String memberId, TeamRunMember newMember) {
        List<TeamRunMember> newMembers = new ArrayList<>();
        for (TeamRunMember m : this.members) {
            if (m.getMemberId().equals(memberId)) {
                newMembers.add(newMember);
            } else {
                newMembers.add(m);
            }
        }
        return new Builder()
                .teamRunId(this.teamRunId)
                .tenantId(this.tenantId)
                .title(this.title)
                .description(this.description)
                .pattern(this.pattern)
                .members(newMembers)
                .status(this.status)
                .initiatedBy(this.initiatedBy)
                .finalResult(this.finalResult)
                .createdAt(this.createdAt)
                .startedAt(this.startedAt)
                .completedAt(this.completedAt)
                .context(this.context)
                .build();
    }

    /**
     * 创建设置最终结果后的不可变副本。
     *
     * @param result 最终结果
     * @return 新的 TeamRun 实例
     */
    public TeamRun withFinalResult(String result) {
        return new Builder()
                .teamRunId(this.teamRunId)
                .tenantId(this.tenantId)
                .title(this.title)
                .description(this.description)
                .pattern(this.pattern)
                .members(this.members)
                .status(TeamRunStatus.COMPLETED)
                .initiatedBy(this.initiatedBy)
                .finalResult(result)
                .createdAt(this.createdAt)
                .startedAt(this.startedAt)
                .completedAt(LocalDateTime.now())
                .context(this.context)
                .build();
    }

    /**
     * 获取下一个待执行的成员。
     *
     * @return 下一个待执行的成员，如果没有则返回 null
     */
    public TeamRunMember getNextPendingMember() {
        return members.stream()
                .filter(m -> m.getStatus() == TeamRunMemberStatus.PENDING)
                .min((a, b) -> Integer.compare(a.getExecutionOrder(), b.getExecutionOrder()))
                .orElse(null);
    }

    /**
     * 检查是否所有成员都已执行完毕（成功或失败）。
     *
     * @return 是否全部完成
     */
    public boolean areAllMembersFinished() {
        return members.stream().allMatch(m -> m.getStatus().isTerminal());
    }

    /**
     * 检查是否有任何成员执行失败。
     *
     * @return 是否有失败
     */
    public boolean hasAnyMemberFailed() {
        return members.stream().anyMatch(m -> m.getStatus() == TeamRunMemberStatus.FAILED);
    }

    public static final class Builder {
        private String teamRunId;
        private String tenantId;
        private String title;
        private String description;
        private TeamRunPattern pattern;
        private List<TeamRunMember> members;
        private TeamRunStatus status;
        private String initiatedBy;
        private String finalResult;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Map<String, Object> context;

        public Builder teamRunId(String teamRunId) {
            this.teamRunId = teamRunId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder pattern(TeamRunPattern pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder members(List<TeamRunMember> members) {
            this.members = members;
            return this;
        }

        public Builder status(TeamRunStatus status) {
            this.status = status;
            return this;
        }

        public Builder initiatedBy(String initiatedBy) {
            this.initiatedBy = initiatedBy;
            return this;
        }

        public Builder finalResult(String finalResult) {
            this.finalResult = finalResult;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
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

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public TeamRun build() {
            return new TeamRun(this);
        }
    }
}
