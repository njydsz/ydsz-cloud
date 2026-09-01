package com.njydsz.agent.domain.skill;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Skill 经验值对象。
 *
 * <p>记录 Skill 执行过程中积累的经验教训，用于指导未来执行。
 * 借鉴 MateClaw 的 LESSONS 设计，实现知识的持续积累和复用。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class SkillLesson {

    private final String lessonId;
    private final String tenantId;
    private final String skillCode;
    private final String skillName;
    private final LessonType lessonType;
    private final String title;
    private final String content;
    private final String scenario;
    private final String action;
    private final String result;
    private final int confidence;
    private final int usageCount;
    private final String sourceExecutionId;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastUsedAt;
    private final Map<String, Object> tags;

    private SkillLesson(Builder builder) {
        this.lessonId = builder.lessonId;
        this.tenantId = builder.tenantId;
        this.skillCode = builder.skillCode;
        this.skillName = builder.skillName;
        this.lessonType = builder.lessonType;
        this.title = builder.title;
        this.content = builder.content;
        this.scenario = builder.scenario;
        this.action = builder.action;
        this.result = builder.result;
        this.confidence = builder.confidence;
        this.usageCount = builder.usageCount;
        this.sourceExecutionId = builder.sourceExecutionId;
        this.createdAt = builder.createdAt;
        this.lastUsedAt = builder.lastUsedAt;
        this.tags = builder.tags != null ? Map.copyOf(builder.tags) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLessonId() {
        return lessonId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public String getSkillName() {
        return skillName;
    }

    public LessonType getLessonType() {
        return lessonType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getScenario() {
        return scenario;
    }

    public String getAction() {
        return action;
    }

    public String getResult() {
        return result;
    }

    public int getConfidence() {
        return confidence;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public String getSourceExecutionId() {
        return sourceExecutionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    /**
     * 创建增加使用次数后的不可变副本。
     *
     * @return 更新后的 SkillLesson 实例
     */
    public SkillLesson withUsed() {
        return new Builder()
                .lessonId(this.lessonId)
                .tenantId(this.tenantId)
                .skillCode(this.skillCode)
                .skillName(this.skillName)
                .lessonType(this.lessonType)
                .title(this.title)
                .content(this.content)
                .scenario(this.scenario)
                .action(this.action)
                .result(this.result)
                .confidence(this.confidence)
                .usageCount(this.usageCount + 1)
                .sourceExecutionId(this.sourceExecutionId)
                .createdAt(this.createdAt)
                .lastUsedAt(LocalDateTime.now())
                .tags(this.tags)
                .build();
    }

    /**
     * 创建更新置信度后的不可变副本。
     *
     * @param newConfidence 新的置信度
     * @return 更新后的 SkillLesson 实例
     */
    public SkillLesson withConfidence(int newConfidence) {
        return new Builder()
                .lessonId(this.lessonId)
                .tenantId(this.tenantId)
                .skillCode(this.skillCode)
                .skillName(this.skillName)
                .lessonType(this.lessonType)
                .title(this.title)
                .content(this.content)
                .scenario(this.scenario)
                .action(this.action)
                .result(this.result)
                .confidence(newConfidence)
                .usageCount(this.usageCount)
                .sourceExecutionId(this.sourceExecutionId)
                .createdAt(this.createdAt)
                .lastUsedAt(this.lastUsedAt)
                .tags(this.tags)
                .build();
    }

    public static final class Builder {

      /** 默认置信度（0-100） */
      private static final int DEFAULT_CONFIDENCE = 50;
        private String lessonId;
        private String tenantId;
        private String skillCode;
        private String skillName;
        private LessonType lessonType;
        private String title;
        private String content;
        private String scenario;
        private String action;
        private String result;
        private int confidence = DEFAULT_CONFIDENCE;
        private int usageCount = 0;
        private String sourceExecutionId;
        private LocalDateTime createdAt;
        private LocalDateTime lastUsedAt;
        private Map<String, Object> tags;

        public Builder lessonId(String lessonId) {
            this.lessonId = lessonId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder skillCode(String skillCode) {
            this.skillCode = skillCode;
            return this;
        }

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public Builder lessonType(LessonType lessonType) {
            this.lessonType = lessonType;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder confidence(int confidence) {
            this.confidence = Math.max(0, Math.min(100, confidence));
            return this;
        }

        public Builder usageCount(int usageCount) {
            this.usageCount = usageCount;
            return this;
        }

        public Builder sourceExecutionId(String sourceExecutionId) {
            this.sourceExecutionId = sourceExecutionId;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastUsedAt(LocalDateTime lastUsedAt) {
            this.lastUsedAt = lastUsedAt;
            return this;
        }

        public Builder tags(Map<String, Object> tags) {
            this.tags = tags;
            return this;
        }

        public SkillLesson build() {
            return new SkillLesson(this);
        }
    }
}
