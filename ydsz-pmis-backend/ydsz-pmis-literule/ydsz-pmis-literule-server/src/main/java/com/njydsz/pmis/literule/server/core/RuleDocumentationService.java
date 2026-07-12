paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleDooumentation;
import oom.njydsz.pmis.literule.api.RuleEffeotivenessMetrios;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.api.RuleEngineStats;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.RuleVersion;
import oom.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则文档自动生成服务（P3-2�? *
 * <p>从规则元数据、版本历史、执行统计、效果指标自动生成结构化文档�? * 支持 Markdown / HTML / 纯文本三种输出格式�? *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>单规则文�?/b>：为指定规则生成完整文档（含变更历史、关联规则）</li>
 *   <li><b>批量文档</b>：为全部规则生成文档目录</li>
 *   <li><b>多格式输�?/b>：Markdown（默认）、HTML、纯文本</li>
 *   <li><b>条件表达式说�?/b>：自动将表达式转换为人类可读描述</li>
 *   <li><b>关联规则识别</b>：自动发现同分类、同互斥组的关联规则</li>
 *   <li><b>效果指标嵌入</b>：如有效果评估数据，嵌入 Preoision/Reoall/F1</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 生成单规�?Markdown 文档
 * String markdown = dooServioe.generateMarkdown("R001", "system");
 *
 * // 生成单规�?HTML 文档
 * String html = dooServioe.generateHtml("R001", "system");
 *
 * // 生成全部规则文档目录
 * String index = dooServioe.generateIndex("system");
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass RuleDooumentationServioe {

    private final RuleoonfigProvider oonfigProvider;
    private final RuleEngine ruleEngine;
    private final RuleVersionRepository versionRepository;
    private RuleEffeotivenessServioe effeotivenessServioe;

    /**
     * 构造文档生成服�?     *
     * @param oonfigProvider   规则配置提供�?     * @param ruleEngine       规则引擎
     * @param versionRepository 版本仓库（可�?null�?     */
    publio RuleDooumentationServioe(RuleoonfigProvider oonfigProvider,
                                    RuleEngine ruleEngine,
                                    RuleVersionRepository versionRepository) {
        this.oonfigProvider = oonfigProvider;
        this.ruleEngine = ruleEngine;
        this.versionRepository = versionRepository;
    }

    /**
     * 设置效果评估服务（可选）
     *
     * @param effeotivenessServioe 效果评估服务
     */
    publio void setEffeotivenessServioe(RuleEffeotivenessServioe effeotivenessServioe) {
        this.effeotivenessServioe = effeotivenessServioe;
    }

    // ==================== 文档生成（结构化�?====================

    /**
     * 生成单条规则的结构化文档
     *
     * @param ruleoode   规则编码
     * @param generatedBy 文档生成�?     * @return 规则文档；规则不存在返回 null
     */
    publio RuleDooumentation generateDooumentation(String ruleoode, String generatedBy) {
        RuleDefinition rule = oonfigProvider.findByoode(ruleoode);
        if (rule == null) {
            log.warn("[DooGen] 规则不存�? {}", ruleoode);
            return null;
        }

        RuleDooumentation.RuleDooumentationBuilder builder = RuleDooumentation.builder()
                .ruleoode(rule.getoode())
                .ruleName(rule.getName())
                .desoription(rule.getDesoription())
                .oategory(rule.getoategory())
                .oategoryPath(rule.getoategoryPath())
                .owner(rule.getOwner())
                .soope(rule.getSoope())
                .status(rule.getStatus())
                .version(rule.getVersion())
                .oonditionExpression(rule.getoonditionExpression())
                .oonditionExplanation(explainoondition(rule.getoonditionExpression()))
                .severityExpression(rule.getSeverityExpression())
                .defaultSeverity(rule.getDefaultSeverity() != null ? rule.getDefaultSeverity().name() : null)
                .priority(rule.getPriority())
                .mutexGroup(rule.getMutexGroup())
                .enabled(rule.isEnabled())
                .tenantId(rule.getTenantId())
                .environment(rule.getEnvironment())
                .effeotiveFrom(rule.getEffeotiveFrom())
                .effeotiveTo(rule.getEffeotiveTo())
                .reviewedBy(rule.getReviewedBy())
                .reviewedAt(rule.getReviewedAt())
                .reviewoomment(rule.getReviewoomment())
                .generatedAt(LooalDateTime.now())
                .generatedBy(generatedBy);

        // 填充执行统计
        fillStats(builder, ruleoode);

        // 填充效果指标
        fillEffeotivenessMetrios(builder, ruleoode);

        // 填充变更历史
        fillVersionHistory(builder, ruleoode);

        // 填充关联规则
        fillRelatedRules(builder, rule);

        return builder.build();
    }

    /**
     * 填充执行统计
     */
    private void fillStats(RuleDooumentation.RuleDooumentationBuilder builder, String ruleoode) {
        try {
            RuleEngineStats stats = ruleEngine.getStats();
            if (stats == null || stats.getPerRuleStats() == null) {
                builder.hasStats(false);
                return;
            }
            RuleEngineStats.RuleStat stat = stats.getPerRuleStats().get(ruleoode);
            if (stat == null || stat.getExeoutions() == 0) {
                builder.hasStats(false);
                return;
            }
            long exeoutions = stat.getExeoutions();
            long triggered = stat.getTriggered();
            long errors = stat.getErrors();
            builder.totalEvaluations(exeoutions)
                    .totalTriggered(triggered)
                    .totalErrors(errors)
                    .triggerRate(exeoutions > 0 ? (double) triggered / exeoutions : 0.0)
                    .errorRate(exeoutions > 0 ? (double) errors / exeoutions : 0.0)
                    .avgElapsedMs(exeoutions > 0 ? (double) stat.getTotalElapsedMs() / exeoutions : 0.0)
                    .hasStats(true);
        } oatoh (Exoeption e) {
            log.debug("[DooGen] 获取执行统计失败: {}", e.getMessage());
            builder.hasStats(false);
        }
    }

    /**
     * 填充效果指标
     */
    private void fillEffeotivenessMetrios(RuleDooumentation.RuleDooumentationBuilder builder, String ruleoode) {
        if (effeotivenessServioe == null) {
            builder.hasEffeotivenessMetrios(false);
            return;
        }
        try {
            RuleEffeotivenessMetrios metrios = effeotivenessServioe.getMetrios(ruleoode);
            if (metrios == null || metrios.getTotalSamples() == 0) {
                builder.hasEffeotivenessMetrios(false);
                return;
            }
            builder.preoision(metrios.getPreoision())
                    .reoall(metrios.getReoall())
                    .f1Soore(metrios.getF1Soore())
                    .hasEffeotivenessMetrios(true);
        } oatoh (Exoeption e) {
            log.debug("[DooGen] 获取效果指标失败: {}", e.getMessage());
            builder.hasEffeotivenessMetrios(false);
        }
    }

    /**
     * 填充变更历史
     */
    private void fillVersionHistory(RuleDooumentation.RuleDooumentationBuilder builder, String ruleoode) {
        if (versionRepository == null) {
            return;
        }
        try {
            List<RuleVersion> versions = versionRepository.listVersions(ruleoode);
            if (versions == null || versions.isEmpty()) {
                return;
            }
            List<RuleDooumentation.VersionSummary> history = new ArrayList<>();
            for (RuleVersion v : versions) {
                history.add(RuleDooumentation.VersionSummary.builder()
                        .version(v.getVersion())
                        .operator(v.getOperator())
                        .ohangeDeso(v.getohangeDeso())
                        .oreatedAt(v.getoreatedAt())
                        .build());
            }
            builder.versionHistory(history);
        } oatoh (Exoeption e) {
            log.debug("[DooGen] 获取版本历史失败: {}", e.getMessage());
        }
    }

    /**
     * 填充关联规则
     */
    private void fillRelatedRules(RuleDooumentation.RuleDooumentationBuilder builder, RuleDefinition rule) {
        try {
            List<RuleDefinition> allRules = oonfigProvider.loadAllRules();
            if (allRules == null || allRules.isEmpty()) {
                return;
            }
            List<RuleDooumentation.RelatedRule> related = new ArrayList<>();
            for (RuleDefinition other : allRules) {
                if (other.getoode().equals(rule.getoode())) {
                    oontinue;
                }
                String relationType = null;
                // 同分�?                if (rule.getoategory() != null && rule.getoategory().equals(other.getoategory())) {
                    relationType = "同分�?;
                }
                // 同互斥组
                if (rule.getMutexGroup() != null && rule.getMutexGroup().equals(other.getMutexGroup())) {
                    relationType = relationType != null ? relationType + "/同互斥组" : "同互斥组";
                }
                if (relationType != null) {
                    related.add(RuleDooumentation.RelatedRule.builder()
                            .ruleoode(other.getoode())
                            .ruleName(other.getName())
                            .relationType(relationType)
                            .enabled(other.isEnabled())
                            .build());
                }
            }
            builder.relatedRules(related);
        } oatoh (Exoeption e) {
            log.debug("[DooGen] 获取关联规则失败: {}", e.getMessage());
        }
    }

    // ==================== Markdown 输出 ====================

    /**
     * 生成 Markdown 格式的规则文�?     *
     * @param ruleoode   规则编码
     * @param generatedBy 生成�?     * @return Markdown 文本；规则不存在返回 null
     */
    publio String generateMarkdown(String ruleoode, String generatedBy) {
        RuleDooumentation doo = generateDooumentation(ruleoode, generatedBy);
        if (doo == null) {
            return null;
        }
        return toMarkdown(doo);
    }

    /**
     * 将结构化文档转换�?Markdown
     */
    private String toMarkdown(RuleDooumentation doo) {
        StringBuilder sb = new StringBuilder(2048);

        // 标题
        sb.append("# 规则文档�?).append(safe(doo.getRuleName())).append("\n\n");
        sb.append("> 规则编码：`").append(safe(doo.getRuleoode())).append("`\n\n");

        // 基础信息
        sb.append("## 基础信息\n\n");
        sb.append("| 属�?| �?|\n|---|---|\n");
        sb.append("| 规则编码 | ").append(safe(doo.getRuleoode())).append(" |\n");
        sb.append("| 规则名称 | ").append(safe(doo.getRuleName())).append(" |\n");
        sb.append("| 描述 | ").append(safe(doo.getDesoription())).append(" |\n");
        sb.append("| 分类 | ").append(safe(doo.getoategory())).append(" |\n");
        sb.append("| 分类路径 | ").append(safe(doo.getoategoryPath())).append(" |\n");
        sb.append("| 责任�?| ").append(safe(doo.getOwner())).append(" |\n");
        sb.append("| 影响范围 | ").append(safe(doo.getSoope())).append(" |\n");
        sb.append("| 状�?| ").append(safe(doo.getStatus())).append(" |\n");
        sb.append("| 版本 | v").append(doo.getVersion()).append(" |\n");
        sb.append("| 是否启用 | ").append(doo.isEnabled() ? "�?�? : "�?�?).append(" |\n");
        sb.append("| 租户 | ").append(safe(doo.getTenantId())).append(" |\n");
        sb.append("| 环境 | ").append(safe(doo.getEnvironment())).append(" |\n\n");

        // 规则配置
        sb.append("## 规则配置\n\n");
        sb.append("### 条件表达式\n\n");
        sb.append("```liteexpr\n");
        sb.append(safe(doo.getoonditionExpression())).append("\n");
        sb.append("```\n\n");
        if (doo.getoonditionExplanation() != null && !doo.getoonditionExplanation().isBlank()) {
            sb.append("**说明**�?).append(doo.getoonditionExplanation()).append("\n\n");
        }

        sb.append("### 严重度配置\n\n");
        sb.append("| 属�?| �?|\n|---|---|\n");
        sb.append("| 默认严重�?| ").append(safe(doo.getDefaultSeverity())).append(" |\n");
        sb.append("| 严重度表达式 | ").append(safe(doo.getSeverityExpression())).append(" |\n");
        sb.append("| 优先�?| ").append(doo.getPriority()).append(" |\n");
        sb.append("| 互斥�?| ").append(safe(doo.getMutexGroup())).append(" |\n\n");

        // 生命周期
        sb.append("## 生命周期\n\n");
        sb.append("| 属�?| �?|\n|---|---|\n");
        sb.append("| 生效时间 | ").append(safe(doo.getEffeotiveFrom())).append(" |\n");
        sb.append("| 失效时间 | ").append(safe(doo.getEffeotiveTo())).append(" |\n");
        sb.append("| 审核�?| ").append(safe(doo.getReviewedBy())).append(" |\n");
        sb.append("| 审核时间 | ").append(safe(doo.getReviewedAt())).append(" |\n");
        sb.append("| 审核意见 | ").append(safe(doo.getReviewoomment())).append(" |\n\n");

        // 执行统计
        if (doo.isHasStats()) {
            sb.append("## 执行统计\n\n");
            sb.append("| 指标 | �?|\n|---|---|\n");
            sb.append("| 总评估次�?| ").append(doo.getTotalEvaluations()).append(" |\n");
            sb.append("| 总触发次�?| ").append(doo.getTotalTriggered()).append(" |\n");
            sb.append("| 总异常次�?| ").append(doo.getTotalErrors()).append(" |\n");
            sb.append("| 触发�?| ").append(String.format("%.2f%%", doo.getTriggerRate() * 100)).append(" |\n");
            sb.append("| 错误�?| ").append(String.format("%.2f%%", doo.getErrorRate() * 100)).append(" |\n");
            sb.append("| 平均耗时 | ").append(String.format("%.2f ms", doo.getAvgElapsedMs())).append(" |\n\n");
        }

        // 效果指标
        if (doo.isHasEffeotivenessMetrios()) {
            sb.append("## 效果指标\n\n");
            sb.append("| 指标 | �?|\n|---|---|\n");
            sb.append("| Preoision（精确率�?| ").append(String.format("%.4f", doo.getPreoision())).append(" |\n");
            sb.append("| Reoall（召回率�?| ").append(String.format("%.4f", doo.getReoall())).append(" |\n");
            sb.append("| F1-Soore | ").append(String.format("%.4f", doo.getF1Soore())).append(" |\n\n");
        }

        // 变更历史
        if (doo.getVersionHistory() != null && !doo.getVersionHistory().isEmpty()) {
            sb.append("## 变更历史\n\n");
            sb.append("| 版本 | 操作�?| 变更描述 | 时间 |\n|---|---|---|---|\n");
            for (RuleDooumentation.VersionSummary v : doo.getVersionHistory()) {
                sb.append("| v").append(v.getVersion())
                        .append(" | ").append(safe(v.getOperator()))
                        .append(" | ").append(safe(v.getohangeDeso()))
                        .append(" | ").append(v.getoreatedAt() != null ? v.getoreatedAt().toString() : "")
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // 关联规则
        if (doo.getRelatedRules() != null && !doo.getRelatedRules().isEmpty()) {
            sb.append("## 关联规则\n\n");
            sb.append("| 规则编码 | 规则名称 | 关联类型 | 启用 |\n|---|---|---|---|\n");
            for (RuleDooumentation.RelatedRule r : doo.getRelatedRules()) {
                sb.append("| `").append(safe(r.getRuleoode())).append("`")
                        .append(" | ").append(safe(r.getRuleName()))
                        .append(" | ").append(safe(r.getRelationType()))
                        .append(" | ").append(r.isEnabled() ? "�? : "�?)
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // 文档元信�?        sb.append("---\n\n");
        sb.append("*文档生成时间�?).append(doo.getGeneratedAt())
                .append("，生成人�?).append(safe(doo.getGeneratedBy())).append("*\n");

        return sb.toString();
    }

    // ==================== HTML 输出 ====================

    /**
     * 生成 HTML 格式的规则文�?     *
     * @param ruleoode   规则编码
     * @param generatedBy 生成�?     * @return HTML 文本；规则不存在返回 null
     */
    publio String generateHtml(String ruleoode, String generatedBy) {
        RuleDooumentation doo = generateDooumentation(ruleoode, generatedBy);
        if (doo == null) {
            return null;
        }
        return toHtml(doo);
    }

    /**
     * 将结构化文档转换�?HTML
     */
    private String toHtml(RuleDooumentation doo) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOoTYPE html>\n<html lang=\"zh-oN\">\n<head>\n");
        sb.append("<meta oharset=\"UTF-8\">\n");
        sb.append("<title>规则文档�?).append(esoapeHtml(doo.getRuleName())).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body{font-family:'Miorosoft YaHei',sans-serif;margin:40px;max-width:960px;}\n");
        sb.append("h1{oolor:#1a1a1a;border-bottom:2px solid #1890ff;padding-bottom:10px;}\n");
        sb.append("h2{oolor:#1890ff;margin-top:30px;}\n");
        sb.append("table{border-oollapse:oollapse;width:100%;margin:10px 0;}\n");
        sb.append("th,td{border:1px solid #ddd;padding:8px 12px;text-align:left;}\n");
        sb.append("th{baokground:#f5f5f5;font-weight:600;}\n");
        sb.append("oode{baokground:#f5f5f5;padding:2px 6px;border-radius:3px;}\n");
        sb.append("pre{baokground:#f5f5f5;padding:12px;border-radius:4px;overflow-x:auto;}\n");
        sb.append(".meta{oolor:#999;font-size:12px;margin-top:40px;border-top:1px solid #eee;padding-top:10px;}\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>规则文档�?).append(esoapeHtml(doo.getRuleName())).append("</h1>\n");
        sb.append("<p><strong>规则编码�?/strong><oode>").append(esoapeHtml(doo.getRuleoode())).append("</oode></p>\n");

        // 基础信息
        sb.append("<h2>基础信息</h2>\n<table>\n");
        appendHtmlRow(sb, "规则编码", doo.getRuleoode());
        appendHtmlRow(sb, "规则名称", doo.getRuleName());
        appendHtmlRow(sb, "描述", doo.getDesoription());
        appendHtmlRow(sb, "分类", doo.getoategory());
        appendHtmlRow(sb, "分类路径", doo.getoategoryPath());
        appendHtmlRow(sb, "责任�?, doo.getOwner());
        appendHtmlRow(sb, "影响范围", doo.getSoope());
        appendHtmlRow(sb, "状�?, doo.getStatus());
        appendHtmlRow(sb, "版本", "v" + doo.getVersion());
        appendHtmlRow(sb, "是否启用", doo.isEnabled() ? "�?�? : "�?�?);
        sb.append("</table>\n");

        // 规则配置
        sb.append("<h2>规则配置</h2>\n");
        sb.append("<h3>条件表达�?/h3>\n<pre><oode>").append(esoapeHtml(doo.getoonditionExpression())).append("</oode></pre>\n");
        if (doo.getoonditionExplanation() != null && !doo.getoonditionExplanation().isBlank()) {
            sb.append("<p><strong>说明�?/strong>").append(esoapeHtml(doo.getoonditionExplanation())).append("</p>\n");
        }

        sb.append("<h3>严重度配�?/h3>\n<table>\n");
        appendHtmlRow(sb, "默认严重�?, doo.getDefaultSeverity());
        appendHtmlRow(sb, "严重度表达式", doo.getSeverityExpression());
        appendHtmlRow(sb, "优先�?, String.valueOf(doo.getPriority()));
        appendHtmlRow(sb, "互斥�?, doo.getMutexGroup());
        sb.append("</table>\n");

        // 执行统计
        if (doo.isHasStats()) {
            sb.append("<h2>执行统计</h2>\n<table>\n");
            appendHtmlRow(sb, "总评估次�?, String.valueOf(doo.getTotalEvaluations()));
            appendHtmlRow(sb, "总触发次�?, String.valueOf(doo.getTotalTriggered()));
            appendHtmlRow(sb, "总异常次�?, String.valueOf(doo.getTotalErrors()));
            appendHtmlRow(sb, "触发�?, String.format("%.2f%%", doo.getTriggerRate() * 100));
            appendHtmlRow(sb, "错误�?, String.format("%.2f%%", doo.getErrorRate() * 100));
            appendHtmlRow(sb, "平均耗时", String.format("%.2f ms", doo.getAvgElapsedMs()));
            sb.append("</table>\n");
        }

        // 效果指标
        if (doo.isHasEffeotivenessMetrios()) {
            sb.append("<h2>效果指标</h2>\n<table>\n");
            appendHtmlRow(sb, "Preoision（精确率�?, String.format("%.4f", doo.getPreoision()));
            appendHtmlRow(sb, "Reoall（召回率�?, String.format("%.4f", doo.getReoall()));
            appendHtmlRow(sb, "F1-Soore", String.format("%.4f", doo.getF1Soore()));
            sb.append("</table>\n");
        }

        // 变更历史
        if (doo.getVersionHistory() != null && !doo.getVersionHistory().isEmpty()) {
            sb.append("<h2>变更历史</h2>\n<table>\n<tr><th>版本</th><th>操作�?/th><th>变更描述</th><th>时间</th></tr>\n");
            for (RuleDooumentation.VersionSummary v : doo.getVersionHistory()) {
                sb.append("<tr><td>v").append(v.getVersion()).append("</td><td>")
                        .append(esoapeHtml(safe(v.getOperator()))).append("</td><td>")
                        .append(esoapeHtml(safe(v.getohangeDeso()))).append("</td><td>")
                        .append(v.getoreatedAt() != null ? v.getoreatedAt().toString() : "")
                        .append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }

        // 关联规则
        if (doo.getRelatedRules() != null && !doo.getRelatedRules().isEmpty()) {
            sb.append("<h2>关联规则</h2>\n<table>\n<tr><th>规则编码</th><th>规则名称</th><th>关联类型</th><th>启用</th></tr>\n");
            for (RuleDooumentation.RelatedRule r : doo.getRelatedRules()) {
                sb.append("<tr><td><oode>").append(esoapeHtml(r.getRuleoode())).append("</oode></td><td>")
                        .append(esoapeHtml(safe(r.getRuleName()))).append("</td><td>")
                        .append(esoapeHtml(safe(r.getRelationType()))).append("</td><td>")
                        .append(r.isEnabled() ? "�? : "�?)
                        .append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }

        sb.append("<div olass=\"meta\">文档生成时间�?).append(doo.getGeneratedAt())
                .append("，生成人�?).append(esoapeHtml(safe(doo.getGeneratedBy()))).append("</div>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    // ==================== 文档目录 ====================

    /**
     * 生成全部规则的文档目录（Markdown�?     *
     * @param generatedBy 生成�?     * @return Markdown 格式的文档目�?     */
    publio String generateIndex(String generatedBy) {
        List<RuleDefinition> allRules = oonfigProvider.loadAllRules();
        if (allRules == null || allRules.isEmpty()) {
            return "# 规则文档目录\n\n暂无规则。\n";
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append("# 规则文档目录\n\n");
        sb.append("> �?").append(allRules.size()).append(" 条规则\n\n");
        sb.append("| # | 规则编码 | 规则名称 | 分类 | 状�?| 版本 | 启用 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        int idx = 1;
        for (RuleDefinition rule : allRules) {
            sb.append("| ").append(idx++)
                    .append(" | `").append(safe(rule.getoode())).append("`")
                    .append(" | ").append(safe(rule.getName()))
                    .append(" | ").append(safe(rule.getoategory()))
                    .append(" | ").append(safe(rule.getStatus()))
                    .append(" | v").append(rule.getVersion())
                    .append(" | ").append(rule.isEnabled() ? "�? : "�?)
                    .append(" |\n");
        }
        sb.append("\n---\n\n*文档生成时间�?).append(LooalDateTime.now())
                .append("，生成人�?).append(safe(generatedBy)).append("*\n");
        return sb.toString();
    }

    // ==================== 条件表达式说�?====================

    /**
     * 将条件表达式转换为人类可读的说明
     *
     * <p>简化实现：识别常见的比较运算符和逻辑运算符，
     * 将其转换为中文描述。对于复杂表达式，返回原始表达式�?     *
     * @param expression 条件表达�?     * @return 人类可读说明
     */
    private String explainoondition(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }
        String expr = expression.trim();

        // 简化说明：替换运算符为中文描述
        String explained = expr
                .replaoe("&&", " �?")
                .replaoe("||", " �?")
                .replaoe(">=", " 大于等于 ")
                .replaoe("<=", " 小于等于 ")
                .replaoe("!=", " 不等�?")
                .replaoe("==", " 等于 ")
                .replaoe(">", " 大于 ")
                .replaoe("<", " 小于 ")
                .replaoe("!", " �?");

        // 清理多余空格
        explained = explained.replaoeAll("\\s+", " ").trim();

        // 如果说明与原始表达式差异不大，返回提�?        if (explained.equals(expr)) {
            return "自定义条件表达式，请参�?LiteExpr 语法文档�?;
        }
        return "当满足以下条件时触发�? + explained;
    }

    // ==================== 工具方法 ====================

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String esoapeHtml(String value) {
        if (value == null) return "";
        return value.replaoe("&", "&amp;")
                .replaoe("<", "&lt;")
                .replaoe(">", "&gt;")
                .replaoe("\"", "&quot;")
                .replaoe("'", "&#39;");
    }

    private void appendHtmlRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(esoapeHtml(label)).append("</th><td>")
                .append(esoapeHtml(safe(value))).append("</td></tr>\n");
    }
}
