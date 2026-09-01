package com.njydsz.agent.domain.memory;

import java.time.LocalDateTime;

/**
 * 从对话中提取的记忆事实值对象。
 *
 * <p>表示从一次或多次对话中提炼出的结构化知识，可用于：</p>
 * <ul>
 *   <li>长期记忆存储 — 跨会话持久化用户偏好、关键决策</li>
 *   <li>知识库补充 — 将对话洞察沉淀为可检索的知识文档</li>
 *   <li>个性化上下文 — 在后续对话中自动注入相关记忆</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的 Dreaming 机制——"你睡了它在工作"，
 * 对话结束后后台自动提取有价值的信息。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class MemoryExtractedFact {

    private final String factId;
    private final String tenantId;
    private final String userId;
    private final String conversationId;
    private final String category;
    private final String content;
    private final String sourceSummary;
    private final double importance;
    private final LocalDateTime extractedAt;

    private MemoryExtractedFact(Builder builder) {
        this.factId = builder.factId;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.conversationId = builder.conversationId;
        this.category = builder.category;
        this.content = builder.content;
        this.sourceSummary = builder.sourceSummary;
        this.importance = builder.importance;
        this.extractedAt = builder.extractedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFactId() {
        return factId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public String getSourceSummary() {
        return sourceSummary;
    }

    public double getImportance() {
        return importance;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public static final class Builder {
        private String factId;
        private String tenantId;
        private String userId;
        private String conversationId;
        private String category;
        private String content;
        private String sourceSummary;
        private double importance;
        private LocalDateTime extractedAt;

        public Builder factId(String factId) {
            this.factId = factId;
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

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder sourceSummary(String sourceSummary) {
            this.sourceSummary = sourceSummary;
            return this;
        }

        public Builder importance(double importance) {
            this.importance = importance;
            return this;
        }

        public Builder extractedAt(LocalDateTime extractedAt) {
            this.extractedAt = extractedAt;
            return this;
        }

        public MemoryExtractedFact build() {
            return new MemoryExtractedFact(this);
        }
    }
}
