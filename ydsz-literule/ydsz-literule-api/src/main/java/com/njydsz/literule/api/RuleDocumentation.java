package com.njydsz.literule.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则文档（P3-2 规则文档自动生成）
 *
 * <p>从规则元数据、版本历史、执行统计自动生成结构化文档，
 * 支持 Markdown / HTML / 纯文本三种输出格式。
 *
 * <p>对标 Drools KIE Workbench 的规则文档化能力，
 * 解决规则"难理解、难交接、难审计"的问题。
 *
 * <h3>文档结构</h3>
 * <ul>
 *   <li>基础信息：编码、名称、描述、分类、责任人、状态</li>
 *   <li>规则配置：条件表达式、严重度配置、优先级、互斥组</li>
 *   <li>生命周期：版本号、生效/失效时间、审核信息</li>
 *   <li>执行统计：评估次数、触发次数、错误次数、触发率、平均耗时</li>
 *   <li>变更历史：版本列表（版本号、操作人、变更描述、时间）</li>
 *   <li>关联规则：同分类或同互斥组的规则列表</li>
 *   <li>效果指标：Precision/Recall/F1（如有效果评估数据）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDocumentation implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 基础信息 ====================

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则描述 */
    private String description;

    /** 分类 */
    private String category;

    /** 分类路径 */
    private String categoryPath;

    /** 责任人 */
    private String owner;

    /** 影响范围 */
    private String scope;

    /** 当前状态 */
    private String status;

    /** 当前版本号 */
    private int version;

    // ==================== 规则配置 ====================

    /** 条件表达式 */
    private String conditionExpression;

    /** 条件表达式说明（自动生成的人类可读描述） */
    private String conditionExplanation;

    /** 严重度表达式 */
    private String severityExpression;

    /** 默认严重度 */
    private String defaultSeverity;

    /** 优先级 */
    private int priority;

    /** 互斥组 */
    private String mutexGroup;

    /** 是否启用 */
    private boolean enabled;

    /** 租户 ID */
    private String tenantId;

    /** 环境标识 */
    private String environment;

    // ==================== 生命周期 ====================

    /** 生效时间 */
    private String effectiveFrom;

    /** 失效时间 */
    private String effectiveTo;

    /** 审核人 */
    private String reviewedBy;

    /** 审核时间 */
    private String reviewedAt;

    /** 审核意见 */
    private String reviewComment;

    // ==================== 执行统计 ====================

    /** 总评估次数 */
    private long totalEvaluations;

    /** 总触发次数 */
    private long totalTriggered;

    /** 总异常次数 */
    private long totalErrors;

    /** 触发率 */
    private double triggerRate;

    /** 错误率 */
    private double errorRate;

    /** 平均评估耗时（毫秒） */
    private double avgElapsedMs;

    /** 是否有执行统计数据 */
    private boolean hasStats;

    // ==================== 效果指标 ====================

    /** 精确率（Precision） */
    private double precision;

    /** 召回率（Recall） */
    private double recall;

    /** F1-Score */
    private double f1Score;

    /** 是否有效果指标数据 */
    private boolean hasEffectivenessMetrics;

    // ==================== 变更历史 ====================

    /** 变更历史摘要 */
    @Builder.Default
    private List<VersionSummary> versionHistory = new ArrayList<>();

    // ==================== 关联规则 ====================

    /** 关联规则列表（同分类或同互斥组） */
    @Builder.Default
    private List<RelatedRule> relatedRules = new ArrayList<>();

    // ==================== 元信息 ====================

    /** 文档生成时间 */
    private LocalDateTime generatedAt;

    /** 文档生成人 */
    private String generatedBy;

    /**
     * 版本摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 版本号 */
        private int version;
        /** 操作人 */
        private String operator;
        /** 变更描述 */
        private String changeDesc;
        /** 变更时间 */
        private LocalDateTime createdAt;
    }

    /**
     * 关联规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedRule implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 规则编码 */
        private String ruleCode;
        /** 规则名称 */
        private String ruleName;
        /** 关联类型 */
        private String relationType;
        /** 是否启用 */
        private boolean enabled;
    }
}
