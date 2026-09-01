package com.njydsz.agent.domain.trigger;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 触发器聚合根。
 *
 * <p>定义一个触发规则，当满足条件时自动启动指定的 Agent 执行。
 * 支持多种触发类型：定时、Webhook、事件驱动等。</p>
 *
 * <p>借鉴 MateClaw 的 Triggers 系统设计，包含事件治理机制：
 * 去重、限速、递归守卫、失败关闭未知模式。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class AgentTrigger {

    private final String triggerId;
    private final String tenantId;
    private final String name;
    private final String description;
    private final TriggerType triggerType;
    private final String targetAgentCode;
    private final String targetAgentType;
    private final String cronExpression;
    private final String matchPattern;
    private final Map<String, Object> config;
    private final boolean enabled;
    private final int maxExecutionsPerHour;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastTriggeredAt;
    private final int totalTriggerCount;

    private AgentTrigger(Builder builder) {
        this.triggerId = builder.triggerId;
        this.tenantId = builder.tenantId;
        this.name = builder.name;
        this.description = builder.description;
        this.triggerType = builder.triggerType;
        this.targetAgentCode = builder.targetAgentCode;
        this.targetAgentType = builder.targetAgentType;
        this.cronExpression = builder.cronExpression;
        this.matchPattern = builder.matchPattern;
        this.config = builder.config != null ? Map.copyOf(builder.config) : Map.of();
        this.enabled = builder.enabled;
        this.maxExecutionsPerHour = builder.maxExecutionsPerHour;
        this.createdAt = builder.createdAt;
        this.lastTriggeredAt = builder.lastTriggeredAt;
        this.totalTriggerCount = builder.totalTriggerCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTriggerId() {
        return triggerId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public String getTargetAgentCode() {
        return targetAgentCode;
    }

    public String getTargetAgentType() {
        return targetAgentType;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getMatchPattern() {
        return matchPattern;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxExecutionsPerHour() {
        return maxExecutionsPerHour;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public int getTotalTriggerCount() {
        return totalTriggerCount;
    }

    /**
     * 创建更新 lastTriggeredAt 和 totalTriggerCount 后的不可变副本。
     *
     * @param newLastTriggeredAt 新的最后触发时间
     * @param newCount           新的累计触发次数
     * @return 更新后的新 AgentTrigger 实例
     */
    public AgentTrigger withTriggerStats(LocalDateTime newLastTriggeredAt, int newCount) {
        return new Builder()
                .triggerId(this.triggerId)
                .tenantId(this.tenantId)
                .name(this.name)
                .description(this.description)
                .triggerType(this.triggerType)
                .targetAgentCode(this.targetAgentCode)
                .targetAgentType(this.targetAgentType)
                .cronExpression(this.cronExpression)
                .matchPattern(this.matchPattern)
                .config(this.config)
                .enabled(this.enabled)
                .maxExecutionsPerHour(this.maxExecutionsPerHour)
                .createdAt(this.createdAt)
                .lastTriggeredAt(newLastTriggeredAt)
                .totalTriggerCount(newCount)
                .build();
    }

    public static final class Builder {

      /** 默认每小时最大执行次数 */
      private static final int DEFAULT_MAX_EXECUTIONS_PER_HOUR = 60;
        private String triggerId;
        private String tenantId;
        private String name;
        private String description;
        private TriggerType triggerType;
        private String targetAgentCode;
        private String targetAgentType;
        private String cronExpression;
        private String matchPattern;
        private Map<String, Object> config;
        private boolean enabled = true;
        private int maxExecutionsPerHour = DEFAULT_MAX_EXECUTIONS_PER_HOUR;
        private LocalDateTime createdAt;
        private LocalDateTime lastTriggeredAt;
        private int totalTriggerCount;

        public Builder triggerId(String triggerId) {
            this.triggerId = triggerId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder triggerType(TriggerType triggerType) {
            this.triggerType = triggerType;
            return this;
        }

        public Builder targetAgentCode(String targetAgentCode) {
            this.targetAgentCode = targetAgentCode;
            return this;
        }

        public Builder targetAgentType(String targetAgentType) {
            this.targetAgentType = targetAgentType;
            return this;
        }

        public Builder cronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
            return this;
        }

        public Builder matchPattern(String matchPattern) {
            this.matchPattern = matchPattern;
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxExecutionsPerHour(int maxExecutionsPerHour) {
            this.maxExecutionsPerHour = maxExecutionsPerHour;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastTriggeredAt(LocalDateTime lastTriggeredAt) {
            this.lastTriggeredAt = lastTriggeredAt;
            return this;
        }

        public Builder totalTriggerCount(int totalTriggerCount) {
            this.totalTriggerCount = totalTriggerCount;
            return this;
        }

        public AgentTrigger build() {
            return new AgentTrigger(this);
        }
    }
}
