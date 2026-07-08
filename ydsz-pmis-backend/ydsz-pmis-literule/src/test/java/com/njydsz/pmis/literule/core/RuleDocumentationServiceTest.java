package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleDocumentation;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RuleDocumentationService 单元测试（P3-2 规则文档自动生成）
 *
 * <p>测试目标：验证文档生成服务的核心能力，包括：
 * <ul>
 *   <li>结构化文档生成（基础信息、配置、统计、版本历史、关联规则）</li>
 *   <li>Markdown 格式输出</li>
 *   <li>HTML 格式输出</li>
 *   <li>文档目录生成</li>
 *   <li>条件表达式人类可读说明</li>
 *   <li>规则不存在时返回 null</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleDocumentationService 单元测试")
class RuleDocumentationServiceTest {

    private RuleConfigProvider configProvider;
    private RuleEngine ruleEngine;
    private RuleVersionRepository versionRepository;
    private RuleDocumentationService service;

    @BeforeEach
    void setUp() {
        configProvider = mock(RuleConfigProvider.class);
        ruleEngine = mock(RuleEngine.class);
        versionRepository = mock(RuleVersionRepository.class);
        service = new RuleDocumentationService(configProvider, ruleEngine, versionRepository);
    }

    // ==================== 结构化文档 ====================

    @Nested
    @DisplayName("结构化文档生成")
    class StructuredDocTest {

        @Test
        @DisplayName("完整文档 — 包含所有字段")
        void shouldGenerateFullDocumentation() {
            setupRule("R001", "毛利率预警规则", "finance");
            setupStats("R001", 1000, 200, 5);
            setupVersionHistory("R001");
            setupRelatedRules("R001", "finance");

            RuleDocumentation doc = service.generateDocumentation("R001", "admin");

            assertThat(doc).isNotNull();
            assertThat(doc.getRuleCode()).isEqualTo("R001");
            assertThat(doc.getRuleName()).isEqualTo("毛利率预警规则");
            assertThat(doc.getCategory()).isEqualTo("finance");
            assertThat(doc.getConditionExpression()).isEqualTo("grossMargin < 0.05");
            assertThat(doc.getConditionExplanation()).isNotEmpty();
            assertThat(doc.isHasStats()).isTrue();
            assertThat(doc.getTotalEvaluations()).isEqualTo(1000);
            assertThat(doc.getTotalTriggered()).isEqualTo(200);
            assertThat(doc.getVersionHistory()).isNotEmpty();
            assertThat(doc.getRelatedRules()).isNotEmpty();
            assertThat(doc.getGeneratedAt()).isNotNull();
            assertThat(doc.getGeneratedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("规则不存在 — 返回 null")
        void shouldReturnNullWhenRuleNotFound() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            RuleDocumentation doc = service.generateDocumentation("R999", "admin");

            assertThat(doc).isNull();
        }

        @Test
        @DisplayName("无执行统计 — hasStats 为 false")
        void shouldHandleNoStats() {
            setupRule("R002", "规则B", "risk");
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            RuleDocumentation doc = service.generateDocumentation("R002", "admin");

            assertThat(doc).isNotNull();
            assertThat(doc.isHasStats()).isFalse();
        }

        @Test
        @DisplayName("无版本仓库 — versionHistory 为空")
        void shouldHandleNoVersionRepository() {
            RuleDocumentationService serviceNoRepo =
                    new RuleDocumentationService(configProvider, ruleEngine, null);
            setupRule("R003", "规则C", "risk");

            RuleDocumentation doc = serviceNoRepo.generateDocumentation("R003", "admin");

            assertThat(doc).isNotNull();
            assertThat(doc.getVersionHistory()).isEmpty();
        }
    }

    // ==================== Markdown 输出 ====================

    @Nested
    @DisplayName("Markdown 输出")
    class MarkdownTest {

        @Test
        @DisplayName("Markdown 文档 — 包含标题和表格")
        void shouldGenerateMarkdown() {
            setupRule("R001", "测试规则", "finance");
            setupStats("R001", 1000, 200, 5);

            String markdown = service.generateMarkdown("R001", "admin");

            assertThat(markdown).isNotNull();
            assertThat(markdown).contains("# 规则文档：测试规则");
            assertThat(markdown).contains("## 基础信息");
            assertThat(markdown).contains("## 规则配置");
            assertThat(markdown).contains("R001");
            assertThat(markdown).contains("| 规则编码 |");
        }

        @Test
        @DisplayName("Markdown 文档 — 包含执行统计")
        void shouldIncludeStatsInMarkdown() {
            setupRule("R001", "测试规则", "finance");
            setupStats("R001", 1000, 200, 5);

            String markdown = service.generateMarkdown("R001", "admin");

            assertThat(markdown).contains("## 执行统计");
            assertThat(markdown).contains("1000");
            assertThat(markdown).contains("200");
        }

        @Test
        @DisplayName("Markdown 文档 — 规则不存在返回 null")
        void shouldReturnNullMarkdown() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            String markdown = service.generateMarkdown("R999", "admin");

            assertThat(markdown).isNull();
        }
    }

    // ==================== HTML 输出 ====================

    @Nested
    @DisplayName("HTML 输出")
    class HtmlTest {

        @Test
        @DisplayName("HTML 文档 — 包含完整结构")
        void shouldGenerateHtml() {
            setupRule("R001", "测试规则", "finance");

            String html = service.generateHtml("R001", "admin");

            assertThat(html).isNotNull();
            assertThat(html).contains("<!DOCTYPE html>");
            assertThat(html).contains("<html");
            assertThat(html).contains("<title>规则文档：测试规则</title>");
            assertThat(html).contains("<h1>规则文档：测试规则</h1>");
            assertThat(html).contains("<h2>基础信息</h2>");
            assertThat(html).contains("</html>");
        }

        @Test
        @DisplayName("HTML 文档 — 包含条件表达式代码块")
        void shouldIncludeConditionInHtml() {
            setupRule("R001", "测试规则", "finance");

            String html = service.generateHtml("R001", "admin");

            assertThat(html).contains("<pre><code>grossMargin &lt; 0.05</code></pre>");
        }

        @Test
        @DisplayName("HTML 文档 — HTML 转义正确")
        void shouldEscapeHtmlInFields() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .name("规则<script>alert('xss')</script>")
                    .conditionExpression("a > 10")
                    .status("PUBLISHED")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            String html = service.generateHtml("R001", "admin");

            assertThat(html).doesNotContain("<script>alert('xss')</script>");
            assertThat(html).contains("&lt;script&gt;");
        }
    }

    // ==================== 文档目录 ====================

    @Nested
    @DisplayName("文档目录")
    class IndexTest {

        @Test
        @DisplayName("生成文档目录 — 包含所有规则")
        void shouldGenerateIndex() {
            List<RuleDefinition> rules = List.of(
                    RuleDefinition.builder().code("R001").name("规则A").category("finance")
                            .status("PUBLISHED").version(1).enabled(true).build(),
                    RuleDefinition.builder().code("R002").name("规则B").category("risk")
                            .status("DISABLED").version(2).enabled(false).build()
            );
            when(configProvider.loadAllRules()).thenReturn(rules);

            String index = service.generateIndex("admin");

            assertThat(index).isNotNull();
            assertThat(index).contains("# 规则文档目录");
            assertThat(index).contains("R001");
            assertThat(index).contains("R002");
            assertThat(index).contains("共 2 条规则");
        }

        @Test
        @DisplayName("空规则列表 — 返回提示信息")
        void shouldHandleEmptyIndex() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            String index = service.generateIndex("admin");

            assertThat(index).contains("暂无规则");
        }
    }

    // ==================== 条件表达式说明 ====================

    @Nested
    @DisplayName("条件表达式说明")
    class ConditionExplanationTest {

        @Test
        @DisplayName("比较运算符 — 转换为中文说明")
        void shouldExplainComparisonOperators() {
            setupRuleWithExpr("R001", "a >= 10 && b < 5");

            RuleDocumentation doc = service.generateDocumentation("R001", "admin");

            assertThat(doc.getConditionExplanation()).contains("大于等于");
            assertThat(doc.getConditionExplanation()).contains("小于");
            assertThat(doc.getConditionExplanation()).contains("且");
        }

        @Test
        @DisplayName("或运算符 — 转换为中文说明")
        void shouldExplainOrOperator() {
            setupRuleWithExpr("R001", "a > 10 || b < 5");

            RuleDocumentation doc = service.generateDocumentation("R001", "admin");

            assertThat(doc.getConditionExplanation()).contains("或");
        }

        @Test
        @DisplayName("空表达式 — 返回空说明")
        void shouldHandleEmptyExpression() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .name("规则")
                    .conditionExpression("")
                    .status("PUBLISHED")
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            RuleDocumentation doc = service.generateDocumentation("R001", "admin");

            assertThat(doc.getConditionExplanation()).isEmpty();
        }
    }

    // ==================== 关联规则 ====================

    @Nested
    @DisplayName("关联规则识别")
    class RelatedRulesTest {

        @Test
        @DisplayName("同分类规则 — 识别为关联")
        void shouldIdentifySameCategoryRules() {
            RuleDefinition r1 = RuleDefinition.builder()
                    .code("R001").name("规则A").category("finance")
                    .conditionExpression("a > 1").status("PUBLISHED")
                    .defaultSeverity(RuleSeverity.YELLOW).enabled(true).build();
            RuleDefinition r2 = RuleDefinition.builder()
                    .code("R002").name("规则B").category("finance")
                    .conditionExpression("b > 1").status("PUBLISHED")
                    .defaultSeverity(RuleSeverity.YELLOW).enabled(true).build();
            when(configProvider.findByCode("R001")).thenReturn(r1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            RuleDocumentation doc = service.generateDocumentation("R001", "admin");

            assertThat(doc.getRelatedRules()).hasSize(1);
            assertThat(doc.getRelatedRules().get(0).getRuleCode()).isEqualTo("R002");
            assertThat(doc.getRelatedRules().get(0).getRelationType()).contains("同分类");
        }

        @Test
        @DisplayName("同互斥组规则 — 识别为关联")
        void shouldIdentifySameMutexGroupRules() {
            RuleDefinition r1 = RuleDefinition.builder()
                    .code("R001").name("规则A").category("finance")
                    .mutexGroup("group1")
                    .conditionExpression("a > 1").status("PUBLISHED")
                    .defaultSeverity(RuleSeverity.YELLOW).enabled(true).build();
            RuleDefinition r2 = RuleDefinition.builder()
                    .code("R002").name("规则B").category("risk")
                    .mutexGroup("group1")
                    .conditionExpression("b > 1").status("PUBLISHED")
                    .defaultSeverity(RuleSeverity.YELLOW).enabled(true).build();
            when(configProvider.findByCode("R001")).thenReturn(r1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            RuleDocumentation doc = service.generateDocumentation("R001", "admin");

            assertThat(doc.getRelatedRules()).hasSize(1);
            assertThat(doc.getRelatedRules().get(0).getRelationType()).contains("同互斥组");
        }
    }

    // ==================== 辅助方法 ====================

    private void setupRule(String code, String name, String category) {
        RuleDefinition rule = RuleDefinition.builder()
                .code(code)
                .name(name)
                .description(name + "描述")
                .category(category)
                .categoryPath(category)
                .owner("owner1")
                .scope("全局")
                .status("PUBLISHED")
                .version(1)
                .conditionExpression("grossMargin < 0.05")
                .severityExpression("grossMargin < 0.02 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(10)
                .enabled(true)
                .tenantId("1")
                .environment("default")
                .effectiveFrom("2024-01-01 00:00:00")
                .reviewedBy("reviewer1")
                .reviewedAt("2024-01-01 10:00:00")
                .build();
        when(configProvider.findByCode(code)).thenReturn(rule);
        when(configProvider.loadAllRules()).thenReturn(List.of(rule));
    }

    private void setupRuleWithExpr(String code, String expr) {
        RuleDefinition rule = RuleDefinition.builder()
                .code(code)
                .name("规则")
                .conditionExpression(expr)
                .status("PUBLISHED")
                .defaultSeverity(RuleSeverity.YELLOW)
                .build();
        when(configProvider.findByCode(code)).thenReturn(rule);
        when(configProvider.loadAllRules()).thenReturn(List.of(rule));
    }

    private void setupStats(String ruleCode, long executions, long triggered, long errors) {
        RuleEngineStats.RuleStat stat = RuleEngineStats.RuleStat.builder()
                .executions(executions)
                .triggered(triggered)
                .errors(errors)
                .totalElapsedMs(executions * 3)
                .build();
        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new java.util.concurrent.ConcurrentHashMap<>(
                        java.util.Map.of(ruleCode, stat)))
                .build();
        when(ruleEngine.getStats()).thenReturn(stats);
    }

    private void setupVersionHistory(String ruleCode) {
        List<RuleVersion> versions = List.of(
                RuleVersion.builder()
                        .ruleCode(ruleCode)
                        .version(2)
                        .operator("admin")
                        .changeDesc("调整阈值")
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build(),
                RuleVersion.builder()
                        .ruleCode(ruleCode)
                        .version(1)
                        .operator("admin")
                        .changeDesc("初始版本")
                        .createdAt(LocalDateTime.now().minusDays(10))
                        .build()
        );
        when(versionRepository.listVersions(ruleCode)).thenReturn(versions);
    }

    private void setupRelatedRules(String ruleCode, String category) {
        RuleDefinition main = RuleDefinition.builder()
                .code(ruleCode).name("主规则").category(category)
                .conditionExpression("a > 1").status("PUBLISHED")
                .defaultSeverity(RuleSeverity.YELLOW).enabled(true).build();
        RuleDefinition related = RuleDefinition.builder()
                .code("R099").name("关联规则").category(category)
                .conditionExpression("b > 1").status("PUBLISHED")
                .defaultSeverity(RuleSeverity.YELLOW).enabled(true).build();
        when(configProvider.findByCode(ruleCode)).thenReturn(main);
        when(configProvider.loadAllRules()).thenReturn(List.of(main, related));
    }
}
