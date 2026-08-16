package com.njydsz.literule.server.core;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleDocumentation;
import com.njydsz.literule.api.RuleEffectivenessMetrics;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.RuleEngineStats;
import com.njydsz.literule.server.spi.RuleConfigProvider;
import com.njydsz.literule.server.spi.RuleVersion;
import com.njydsz.literule.server.spi.RuleVersionRepository;

/**
 * 规则文档自动生成服务（P3-2）
 *
 * <p>从规则元数据、版本历史、执行统计、效果指标自动生成结构化文档，
 * 支持 Markdown / HTML / 纯文本三种输出格式。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>单规则文档</b>：为指定规则生成完整文档（含变更历史、关联规则）</li>
 *   <li><b>批量文档</b>：为全部规则生成文档目录</li>
 *   <li><b>多格式输出</b>：Markdown（默认）、HTML、纯文本</li>
 *   <li><b>条件表达式说明</b>：自动将表达式转换为人类可读描述</li>
 *   <li><b>关联规则识别</b>：自动发现同分类、同互斥组的关联规则</li>
 *   <li><b>效果指标嵌入</b>：如有效果评估数据，嵌入 Precision/Recall/F1</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 生成单规则 Markdown 文档
 * String markdown = docService.generateMarkdown("R001", "system");
 *
 * // 生成单规则 HTML 文档
 * String html = docService.generateHtml("R001", "system");
 *
 * // 生成全部规则文档目录
 * String index = docService.generateIndex("system");
 * </pre>
 *
 * @author ydsz-team
 *
 * @since 1.0.0
 */
@Slf4j
public class RuleDocumentationService {

    private final RuleConfigProvider configProvider;
    private final RuleEngine ruleEngine;
    private final RuleVersionRepository versionRepository;
    private RuleEffectivenessService effectivenessService;

    /**
     * 构造文档生成服务
     *
     * @param configProvider   规则配置提供者
     * @param ruleEngine       规则引擎
     * @param versionRepository 版本仓库（可为 null）
     */
    public RuleDocumentationService(RuleConfigProvider configProvider,
                                    RuleEngine ruleEngine,
                                    RuleVersionRepository versionRepository) {
        this.configProvider = configProvider;
        this.ruleEngine = ruleEngine;
        this.versionRepository = versionRepository;
    }

    /**
     * 设置效果评估服务（可选）
     *
     * @param effectivenessService 效果评估服务
     */
    public void setEffectivenessService(RuleEffectivenessService effectivenessService) {
        this.effectivenessService = effectivenessService;
    }

    // ==================== 文档生成（结构化） ====================

    /**
     * 生成单条规则的结构化文档
     *
     * @param ruleCode   规则编码
     * @param generatedBy 文档生成人
     * @return 规则文档；规则不存在返回 null
     */
    public RuleDocumentation generateDocumentation(String ruleCode, String generatedBy) {
        RuleDefinition rule = configProvider.findByCode(ruleCode);
        if (rule == null) {
            log.warn("[DocGen] 规则不存在: {}", ruleCode);
            return null;
        }

        RuleDocumentation.RuleDocumentationBuilder builder = RuleDocumentation.builder()
                .ruleCode(rule.getCode())
                .ruleName(rule.getName())
                .description(rule.getDescription())
                .category(rule.getCategory())
                .categoryPath(rule.getCategoryPath())
                .owner(rule.getOwner())
                .scope(rule.getScope())
                .status(rule.getStatus())
                .version(rule.getVersion())
                .conditionExpression(rule.getConditionExpression())
                .conditionExplanation(explainCondition(rule.getConditionExpression()))
                .severityExpression(rule.getSeverityExpression())
                .defaultSeverity(rule.getDefaultSeverity() != null ? rule.getDefaultSeverity().name() : null)
                .priority(rule.getPriority())
                .mutexGroup(rule.getMutexGroup())
                .enabled(rule.isEnabled())
                .tenantId(rule.getTenantId())
                .environment(rule.getEnvironment())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .reviewedBy(rule.getReviewedBy())
                .reviewedAt(rule.getReviewedAt())
                .reviewComment(rule.getReviewComment())
                .generatedAt(LocalDateTime.now())
                .generatedBy(generatedBy);

        // 填充执行统计
        fillStats(builder, ruleCode);

        // 填充效果指标
        fillEffectivenessMetrics(builder, ruleCode);

        // 填充变更历史
        fillVersionHistory(builder, ruleCode);

        // 填充关联规则
        fillRelatedRules(builder, rule);

        return builder.build();
    }

    /**
     * 填充执行统计
     */
    private void fillStats(RuleDocumentation.RuleDocumentationBuilder builder, String ruleCode) {
        try {
            RuleEngineStats stats = ruleEngine.getStats();
            if (stats == null || stats.getPerRuleStats() == null) {
                builder.hasStats(false);
                return;
            }
            RuleEngineStats.RuleStat stat = stats.getPerRuleStats().get(ruleCode);
            if (stat == null || stat.getExecutions() == 0) {
                builder.hasStats(false);
                return;
            }
            long executions = stat.getExecutions();
            long triggered = stat.getTriggered();
            long errors = stat.getErrors();
            builder.totalEvaluations(executions)
                    .totalTriggered(triggered)
                    .totalErrors(errors)
                    .triggerRate(executions > 0 ? (double) triggered / executions : 0.0)
                    .errorRate(executions > 0 ? (double) errors / executions : 0.0)
                    .avgElapsedMs(executions > 0 ? (double) stat.getTotalElapsedMs() / executions : 0.0)
                    .hasStats(true);
        } catch (Exception e) {
            log.debug("[DocGen] 获取执行统计失败: {}", e.getMessage());
            builder.hasStats(false);
        }
    }

    /**
     * 填充效果指标
     */
    private void fillEffectivenessMetrics(RuleDocumentation.RuleDocumentationBuilder builder, String ruleCode) {
        if (effectivenessService == null) {
            builder.hasEffectivenessMetrics(false);
            return;
        }
        try {
            RuleEffectivenessMetrics metrics = effectivenessService.getMetrics(ruleCode);
            if (metrics == null || metrics.getTotalSamples() == 0) {
                builder.hasEffectivenessMetrics(false);
                return;
            }
            builder.precision(metrics.getPrecision())
                    .recall(metrics.getRecall())
                    .f1Score(metrics.getF1Score())
                    .hasEffectivenessMetrics(true);
        } catch (Exception e) {
            log.debug("[DocGen] 获取效果指标失败: {}", e.getMessage());
            builder.hasEffectivenessMetrics(false);
        }
    }

    /**
     * 填充变更历史
     */
    private void fillVersionHistory(RuleDocumentation.RuleDocumentationBuilder builder, String ruleCode) {
        if (versionRepository == null) {
            return;
        }
        try {
            List<RuleVersion> versions = versionRepository.listVersions(ruleCode);
            if (versions == null || versions.isEmpty()) {
                return;
            }
            List<RuleDocumentation.VersionSummary> history = new ArrayList<>();
            for (RuleVersion v : versions) {
                history.add(RuleDocumentation.VersionSummary.builder()
                        .version(v.getVersion())
                        .operator(v.getOperator())
                        .changeDesc(v.getChangeDesc())
                        .createdAt(v.getCreatedAt())
                        .build());
            }
            builder.versionHistory(history);
        } catch (Exception e) {
            log.debug("[DocGen] 获取版本历史失败: {}", e.getMessage());
        }
    }

    /**
     * 填充关联规则
     */
    private void fillRelatedRules(RuleDocumentation.RuleDocumentationBuilder builder, RuleDefinition rule) {
        try {
            List<RuleDefinition> allRules = configProvider.loadAllRules();
            if (allRules == null || allRules.isEmpty()) {
                return;
            }
            List<RuleDocumentation.RelatedRule> related = new ArrayList<>();
            for (RuleDefinition other : allRules) {
                if (other.getCode().equals(rule.getCode())) {
                    continue;
                }
                String relationType = null;
                // 同分类
                if (rule.getCategory() != null && rule.getCategory().equals(other.getCategory())) {
                    relationType = "同分类";
                }
                // 同互斥组
                if (rule.getMutexGroup() != null && rule.getMutexGroup().equals(other.getMutexGroup())) {
                    relationType = relationType != null ? relationType + "/同互斥组" : "同互斥组";
                }
                if (relationType != null) {
                    related.add(RuleDocumentation.RelatedRule.builder()
                            .ruleCode(other.getCode())
                            .ruleName(other.getName())
                            .relationType(relationType)
                            .enabled(other.isEnabled())
                            .build());
                }
            }
            builder.relatedRules(related);
        } catch (Exception e) {
            log.debug("[DocGen] 获取关联规则失败: {}", e.getMessage());
        }
    }

    // ==================== Markdown 输出 ====================

    /**
     * 生成 Markdown 格式的规则文档
     *
     * @param ruleCode   规则编码
     * @param generatedBy 生成人
     * @return Markdown 文本；规则不存在返回 null
     */
    public String generateMarkdown(String ruleCode, String generatedBy) {
        RuleDocumentation doc = generateDocumentation(ruleCode, generatedBy);
        if (doc == null) {
            return null;
        }
        return toMarkdown(doc);
    }

    /**
     * 将结构化文档转换为 Markdown
     */
    private String toMarkdown(RuleDocumentation doc) {
        StringBuilder sb = new StringBuilder(2048);

        // 标题
        sb.append("# 规则文档：").append(safe(doc.getRuleName())).append("\n\n");
        sb.append("> 规则编码：`").append(safe(doc.getRuleCode())).append("`\n\n");

        // 基础信息
        sb.append("## 基础信息\n\n");
        sb.append("| 属性 | 值 |\n|---|---|\n");
        sb.append("| 规则编码 | ").append(safe(doc.getRuleCode())).append(" |\n");
        sb.append("| 规则名称 | ").append(safe(doc.getRuleName())).append(" |\n");
        sb.append("| 描述 | ").append(safe(doc.getDescription())).append(" |\n");
        sb.append("| 分类 | ").append(safe(doc.getCategory())).append(" |\n");
        sb.append("| 分类路径 | ").append(safe(doc.getCategoryPath())).append(" |\n");
        sb.append("| 责任人 | ").append(safe(doc.getOwner())).append(" |\n");
        sb.append("| 影响范围 | ").append(safe(doc.getScope())).append(" |\n");
        sb.append("| 状态 | ").append(safe(doc.getStatus())).append(" |\n");
        sb.append("| 版本 | v").append(doc.getVersion()).append(" |\n");
        sb.append("| 是否启用 | ").append(doc.isEnabled() ? "✅ 是" : "❌ 否").append(" |\n");
        sb.append("| 租户 | ").append(safe(doc.getTenantId())).append(" |\n");
        sb.append("| 环境 | ").append(safe(doc.getEnvironment())).append(" |\n\n");

        // 规则配置
        sb.append("## 规则配置\n\n");
        sb.append("### 条件表达式\n\n");
        sb.append("```liteexpr\n");
        sb.append(safe(doc.getConditionExpression())).append("\n");
        sb.append("```\n\n");
        if (doc.getConditionExplanation() != null && !doc.getConditionExplanation().isBlank()) {
            sb.append("**说明**：").append(doc.getConditionExplanation()).append("\n\n");
        }

        sb.append("### 严重度配置\n\n");
        sb.append("| 属性 | 值 |\n|---|---|\n");
        sb.append("| 默认严重度 | ").append(safe(doc.getDefaultSeverity())).append(" |\n");
        sb.append("| 严重度表达式 | ").append(safe(doc.getSeverityExpression())).append(" |\n");
        sb.append("| 优先级 | ").append(doc.getPriority()).append(" |\n");
        sb.append("| 互斥组 | ").append(safe(doc.getMutexGroup())).append(" |\n\n");

        // 生命周期
        sb.append("## 生命周期\n\n");
        sb.append("| 属性 | 值 |\n|---|---|\n");
        sb.append("| 生效时间 | ").append(safe(doc.getEffectiveFrom())).append(" |\n");
        sb.append("| 失效时间 | ").append(safe(doc.getEffectiveTo())).append(" |\n");
        sb.append("| 审核人 | ").append(safe(doc.getReviewedBy())).append(" |\n");
        sb.append("| 审核时间 | ").append(safe(doc.getReviewedAt())).append(" |\n");
        sb.append("| 审核意见 | ").append(safe(doc.getReviewComment())).append(" |\n\n");

        // 执行统计
        if (doc.isHasStats()) {
            sb.append("## 执行统计\n\n");
            sb.append("| 指标 | 值 |\n|---|---|\n");
            sb.append("| 总评估次数 | ").append(doc.getTotalEvaluations()).append(" |\n");
            sb.append("| 总触发次数 | ").append(doc.getTotalTriggered()).append(" |\n");
            sb.append("| 总异常次数 | ").append(doc.getTotalErrors()).append(" |\n");
            sb.append("| 触发率 | ").append(String.format("%.2f%%", doc.getTriggerRate() * 100)).append(" |\n");
            sb.append("| 错误率 | ").append(String.format("%.2f%%", doc.getErrorRate() * 100)).append(" |\n");
            sb.append("| 平均耗时 | ").append(String.format("%.2f ms", doc.getAvgElapsedMs())).append(" |\n\n");
        }

        // 效果指标
        if (doc.isHasEffectivenessMetrics()) {
            sb.append("## 效果指标\n\n");
            sb.append("| 指标 | 值 |\n|---|---|\n");
            sb.append("| Precision（精确率） | ").append(String.format("%.4f", doc.getPrecision())).append(" |\n");
            sb.append("| Recall（召回率） | ").append(String.format("%.4f", doc.getRecall())).append(" |\n");
            sb.append("| F1-Score | ").append(String.format("%.4f", doc.getF1Score())).append(" |\n\n");
        }

        // 变更历史
        if (doc.getVersionHistory() != null && !doc.getVersionHistory().isEmpty()) {
            sb.append("## 变更历史\n\n");
            sb.append("| 版本 | 操作人 | 变更描述 | 时间 |\n|---|---|---|---|\n");
            for (RuleDocumentation.VersionSummary v : doc.getVersionHistory()) {
                sb.append("| v").append(v.getVersion())
                        .append(" | ").append(safe(v.getOperator()))
                        .append(" | ").append(safe(v.getChangeDesc()))
                        .append(" | ").append(v.getCreatedAt() != null ? v.getCreatedAt().toString() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // 关联规则
        if (doc.getRelatedRules() != null && !doc.getRelatedRules().isEmpty()) {
            sb.append("## 关联规则\n\n");
            sb.append("| 规则编码 | 规则名称 | 关联类型 | 启用 |\n|---|---|---|---|\n");
            for (RuleDocumentation.RelatedRule r : doc.getRelatedRules()) {
                sb.append("| `").append(safe(r.getRuleCode())).append("`")
                        .append(" | ").append(safe(r.getRuleName()))
                        .append(" | ").append(safe(r.getRelationType()))
                        .append(" | ").append(r.isEnabled() ? "✅" : "❌")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // 文档元信息
        sb.append("---\n\n");
        sb.append("*文档生成时间：").append(doc.getGeneratedAt())
                .append("，生成人：").append(safe(doc.getGeneratedBy())).append("*\n");

        return sb.toString();
    }

    // ==================== HTML 输出 ====================

    /**
     * 生成 HTML 格式的规则文档
     *
     * @param ruleCode   规则编码
     * @param generatedBy 生成人
     * @return HTML 文本；规则不存在返回 null
     */
    public String generateHtml(String ruleCode, String generatedBy) {
        RuleDocumentation doc = generateDocumentation(ruleCode, generatedBy);
        if (doc == null) {
            return null;
        }
        return toHtml(doc);
    }

    /**
     * 将结构化文档转换为 HTML
     */
    private String toHtml(RuleDocumentation doc) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>规则文档：").append(escapeHtml(doc.getRuleName())).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body{font-family:'Microsoft YaHei',sans-serif;margin:40px;max-width:960px;}\n");
        sb.append("h1{color:#1a1a1a;border-bottom:2px solid #1890ff;padding-bottom:10px;}\n");
        sb.append("h2{color:#1890ff;margin-top:30px;}\n");
        sb.append("table{border-collapse:collapse;width:100%;margin:10px 0;}\n");
        sb.append("th,td{border:1px solid #ddd;padding:8px 12px;text-align:left;}\n");
        sb.append("th{background:#f5f5f5;font-weight:600;}\n");
        sb.append("code{background:#f5f5f5;padding:2px 6px;border-radius:3px;}\n");
        sb.append("pre{background:#f5f5f5;padding:12px;border-radius:4px;overflow-x:auto;}\n");
        sb.append(".meta{color:#999;font-size:12px;margin-top:40px;border-top:1px solid #eee;padding-top:10px;}\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>规则文档：").append(escapeHtml(doc.getRuleName())).append("</h1>\n");
        sb.append("<p><strong>规则编码：</strong><code>").append(escapeHtml(doc.getRuleCode())).append("</code></p>\n");

        // 基础信息
        sb.append("<h2>基础信息</h2>\n<table>\n");
        appendHtmlRow(sb, "规则编码", doc.getRuleCode());
        appendHtmlRow(sb, "规则名称", doc.getRuleName());
        appendHtmlRow(sb, "描述", doc.getDescription());
        appendHtmlRow(sb, "分类", doc.getCategory());
        appendHtmlRow(sb, "分类路径", doc.getCategoryPath());
        appendHtmlRow(sb, "责任人", doc.getOwner());
        appendHtmlRow(sb, "影响范围", doc.getScope());
        appendHtmlRow(sb, "状态", doc.getStatus());
        appendHtmlRow(sb, "版本", "v" + doc.getVersion());
        appendHtmlRow(sb, "是否启用", doc.isEnabled() ? "✅ 是" : "❌ 否");
        sb.append("</table>\n");

        // 规则配置
        sb.append("<h2>规则配置</h2>\n");
        sb.append("<h3>条件表达式</h3>\n<pre><code>").append(escapeHtml(doc.getConditionExpression())).append("</code></pre>\n");
        if (doc.getConditionExplanation() != null && !doc.getConditionExplanation().isBlank()) {
            sb.append("<p><strong>说明：</strong>").append(escapeHtml(doc.getConditionExplanation())).append("</p>\n");
        }

        sb.append("<h3>严重度配置</h3>\n<table>\n");
        appendHtmlRow(sb, "默认严重度", doc.getDefaultSeverity());
        appendHtmlRow(sb, "严重度表达式", doc.getSeverityExpression());
        appendHtmlRow(sb, "优先级", String.valueOf(doc.getPriority()));
        appendHtmlRow(sb, "互斥组", doc.getMutexGroup());
        sb.append("</table>\n");

        // 执行统计
        if (doc.isHasStats()) {
            sb.append("<h2>执行统计</h2>\n<table>\n");
            appendHtmlRow(sb, "总评估次数", String.valueOf(doc.getTotalEvaluations()));
            appendHtmlRow(sb, "总触发次数", String.valueOf(doc.getTotalTriggered()));
            appendHtmlRow(sb, "总异常次数", String.valueOf(doc.getTotalErrors()));
            appendHtmlRow(sb, "触发率", String.format("%.2f%%", doc.getTriggerRate() * 100));
            appendHtmlRow(sb, "错误率", String.format("%.2f%%", doc.getErrorRate() * 100));
            appendHtmlRow(sb, "平均耗时", String.format("%.2f ms", doc.getAvgElapsedMs()));
            sb.append("</table>\n");
        }

        // 效果指标
        if (doc.isHasEffectivenessMetrics()) {
            sb.append("<h2>效果指标</h2>\n<table>\n");
            appendHtmlRow(sb, "Precision（精确率）", String.format("%.4f", doc.getPrecision()));
            appendHtmlRow(sb, "Recall（召回率）", String.format("%.4f", doc.getRecall()));
            appendHtmlRow(sb, "F1-Score", String.format("%.4f", doc.getF1Score()));
            sb.append("</table>\n");
        }

        // 变更历史
        if (doc.getVersionHistory() != null && !doc.getVersionHistory().isEmpty()) {
            sb.append("<h2>变更历史</h2>\n<table>\n<tr><th>版本</th><th>操作人</th><th>变更描述</th><th>时间</th></tr>\n");
            for (RuleDocumentation.VersionSummary v : doc.getVersionHistory()) {
                sb.append("<tr><td>v").append(v.getVersion()).append("</td><td>")
                        .append(escapeHtml(safe(v.getOperator()))).append("</td><td>")
                        .append(escapeHtml(safe(v.getChangeDesc()))).append("</td><td>")
                        .append(v.getCreatedAt() != null ? v.getCreatedAt().toString() : "")
                        .append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }

        // 关联规则
        if (doc.getRelatedRules() != null && !doc.getRelatedRules().isEmpty()) {
            sb.append("<h2>关联规则</h2>\n<table>\n<tr><th>规则编码</th><th>规则名称</th><th>关联类型</th><th>启用</th></tr>\n");
            for (RuleDocumentation.RelatedRule r : doc.getRelatedRules()) {
                sb.append("<tr><td><code>").append(escapeHtml(r.getRuleCode())).append("</code></td><td>")
                        .append(escapeHtml(safe(r.getRuleName()))).append("</td><td>")
                        .append(escapeHtml(safe(r.getRelationType()))).append("</td><td>")
                        .append(r.isEnabled() ? "✅" : "❌")
                        .append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }

        sb.append("<div class=\"meta\">文档生成时间：").append(doc.getGeneratedAt())
                .append("，生成人：").append(escapeHtml(safe(doc.getGeneratedBy()))).append("</div>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    // ==================== 文档目录 ====================

    /**
     * 生成全部规则的文档目录（Markdown）
     *
     * @param generatedBy 生成人
     * @return Markdown 格式的文档目录
     */
    public String generateIndex(String generatedBy) {
        List<RuleDefinition> allRules = configProvider.loadAllRules();
        if (allRules == null || allRules.isEmpty()) {
            return "# 规则文档目录\n\n暂无规则。\n";
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append("# 规则文档目录\n\n");
        sb.append("> 共 ").append(allRules.size()).append(" 条规则\n\n");
        sb.append("| # | 规则编码 | 规则名称 | 分类 | 状态 | 版本 | 启用 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        int idx = 1;
        for (RuleDefinition rule : allRules) {
            sb.append("| ").append(idx++)
                    .append(" | `").append(safe(rule.getCode())).append("`")
                    .append(" | ").append(safe(rule.getName()))
                    .append(" | ").append(safe(rule.getCategory()))
                    .append(" | ").append(safe(rule.getStatus()))
                    .append(" | v").append(rule.getVersion())
                    .append(" | ").append(rule.isEnabled() ? "✅" : "❌")
                    .append(" |\n");
        }
        sb.append("\n---\n\n*文档生成时间：").append(LocalDateTime.now())
                .append("，生成人：").append(safe(generatedBy)).append("*\n");
        return sb.toString();
    }

    // ==================== 条件表达式说明 ====================

    /**
     * 将条件表达式转换为人类可读的说明
     *
     * <p>简化实现：识别常见的比较运算符和逻辑运算符，
     * 将其转换为中文描述。对于复杂表达式，返回原始表达式。
     *
     * @param expression 条件表达式
     * @return 人类可读说明
     */
    private String explainCondition(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        String expr = expression.trim();

        // 简化说明：替换运算符为中文描述
        String explained = expr
                .replace("&&", " 且 ")
                .replace("||", " 或 ")
                .replace(">=", " 大于等于 ")
                .replace("<=", " 小于等于 ")
                .replace("!=", " 不等于 ")
                .replace("==", " 等于 ")
                .replace(">", " 大于 ")
                .replace("<", " 小于 ")
                .replace("!", " 非 ");

        // 清理多余空格
        explained = explained.replaceAll("\\s+", " ").trim();

        // 如果说明与原始表达式差异不大，返回提示
        if (explained.equals(expr)) {
            return "自定义条件表达式，请参考 LiteExpr 语法文档。";
        }
        return "当满足以下条件时触发：" + explained;
    }

    // ==================== 工具方法 ====================

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void appendHtmlRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(escapeHtml(label)).append("</th><td>")
                .append(escapeHtml(safe(value))).append("</td></tr>\n");
    }
}
