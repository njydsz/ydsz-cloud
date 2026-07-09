package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleDocumentation;
import com.njydsz.pmis.literule.api.RuleEffectivenessMetrics;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleDocumentationService} 单元测试。
 *
 * <p>覆盖文档生成、Markdown/HTML 输出、文档目录、效果指标嵌入、关联规则识别等能力。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则文档生成服务测试")
@ExtendWith(MockitoExtension.class)
class RuleDocumentationServiceTest {

    @Mock
    private RuleConfigProvider configProvider;

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private RuleVersionRepository versionRepository;

    @Mock
    private RuleEffectivenessService effectivenessService;

    @InjectMocks
    private RuleDocumentationService documentationService;

    private RuleDefinition buildFullRule(String code) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .description("测试规则描述")
                .category("finance")
                .categoryPath("finance/credit")
                .owner("admin")
                .scope("全局")
                .status("PUBLISHED")
                .version(2)
                .conditionExpression("amount > 1000 && risk > 0.5")
                .severityExpression("amount > 5000 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(10)
                .mutexGroup("G1")
                .enabled(true)
                .tenantId("T001")
                .environment("prod")
                .effectiveFrom("2026-01-01 00:00:00")
                .effectiveTo("2026-12-31 23:59:59")
                .reviewedBy("reviewer")
                .reviewedAt("2026-01-02 10:00:00")
                .reviewComment("通过")
                .build();
    }

    private RuleEngineStats.RuleStat buildStat(long exec, long triggered, long errors) {
        return RuleEngineStats.RuleStat.builder()
                .executions(exec)
                .triggered(triggered)
                .errors(errors)
                .totalElapsedMs(exec * 2L)
                .build();
    }

    private RuleEngineStats buildStats(Map<String, RuleEngineStats.RuleStat> perRule) {
        return RuleEngineStats.builder()
                .totalEvaluations(perRule.values().stream().mapToLong(RuleEngineStats.RuleStat::getExecutions).sum())
                .totalTriggered(perRule.values().stream().mapToLong(RuleEngineStats.RuleStat::getTriggered).sum())
                .totalErrors(perRule.values().stream().mapToLong(RuleEngineStats.RuleStat::getErrors).sum())
                .perRuleStats(perRule)
                .build();
    }

    private RuleVersion buildVersion(String ruleCode, int version, String desc) {
        return RuleVersion.builder()
                .ruleCode(ruleCode)
                .version(version)
                .definitionJson("{\"name\":\"v" + version + "\"}")
                .operator("admin")
                .changeDesc(desc)
                .createdAt(LocalDateTime.now().minusDays(version))
                .build();
    }

    @BeforeEach
    void setUp() {
        documentationService.setEffectivenessService(effectivenessService);
    }

    @Nested
    @DisplayName("结构化文档生成：generateDocumentation")
    class GenerateDocumentationTest {

        @Test
        @DisplayName("边界场景：规则不存在返回 null")
        void shouldReturnNullWhenRuleNotFound() {
            when(configProvider.findByCode("R_NOT_EXIST")).thenReturn(null);

            RuleDocumentation doc = documentationService.generateDocumentation("R_NOT_EXIST", "system");

            assertThat(doc).isNull();
            verify(configProvider).findByCode("R_NOT_EXIST");
        }

        @Test
        @DisplayName("正常场景：生成包含基础信息和配置的文档")
        void shouldGenerateDocWithBasicInfo() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc).isNotNull();
            assertThat(doc.getRuleCode()).isEqualTo("R001");
            assertThat(doc.getRuleName()).isEqualTo("规则-R001");
            assertThat(doc.getDescription()).isEqualTo("测试规则描述");
            assertThat(doc.getCategory()).isEqualTo("finance");
            assertThat(doc.getOwner()).isEqualTo("admin");
            assertThat(doc.getStatus()).isEqualTo("PUBLISHED");
            assertThat(doc.getVersion()).isEqualTo(2);
            assertThat(doc.getConditionExpression()).contains("amount > 1000");
            assertThat(doc.getConditionExplanation()).isNotBlank();
            assertThat(doc.getDefaultSeverity()).isEqualTo("YELLOW");
            assertThat(doc.getPriority()).isEqualTo(10);
            assertThat(doc.getMutexGroup()).isEqualTo("G1");
            assertThat(doc.isEnabled()).isTrue();
            assertThat(doc.getGeneratedBy()).isEqualTo("system");
            assertThat(doc.getGeneratedAt()).isNotNull();
        }

        @Test
        @DisplayName("正常场景：嵌入执行统计数据")
        void shouldFillStats() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R001", buildStat(1000, 200, 10));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.isHasStats()).isTrue();
            assertThat(doc.getTotalEvaluations()).isEqualTo(1000);
            assertThat(doc.getTotalTriggered()).isEqualTo(200);
            assertThat(doc.getTotalErrors()).isEqualTo(10);
            assertThat(doc.getTriggerRate()).isEqualTo(0.2);
            assertThat(doc.getErrorRate()).isCloseTo(0.01, within(0.0001));
            assertThat(doc.getAvgElapsedMs()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("边界场景：stats 为 null 时 hasStats 为 false")
        void shouldHandleNullStats() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(null);
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.isHasStats()).isFalse();
        }

        @Test
        @DisplayName("边界场景：规则无执行记录时 hasStats 为 false")
        void shouldHandleZeroExecutions() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R001", buildStat(0, 0, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.isHasStats()).isFalse();
        }

        @Test
        @DisplayName("正常场景：嵌入效果指标数据")
        void shouldFillEffectivenessMetrics() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            RuleEffectivenessMetrics metrics = RuleEffectivenessMetrics.builder()
                    .ruleCode("R001")
                    .truePositives(80)
                    .falsePositives(20)
                    .falseNegatives(10)
                    .trueNegatives(90)
                    .totalSamples(200)
                    .build();
            when(effectivenessService.getMetrics("R001")).thenReturn(metrics);

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.isHasEffectivenessMetrics()).isTrue();
            assertThat(doc.getPrecision()).isCloseTo(0.8, within(0.0001));
            assertThat(doc.getRecall()).isCloseTo(0.888, within(0.01));
            assertThat(doc.getF1Score()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("边界场景：效果指标样本为 0 时 hasEffectivenessMetrics 为 false")
        void shouldHandleZeroSamples() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.isHasEffectivenessMetrics()).isFalse();
        }

        @Test
        @DisplayName("正常场景：填充变更历史")
        void shouldFillVersionHistory() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));
            when(versionRepository.listVersions("R001")).thenReturn(List.of(
                    buildVersion("R001", 1, "初始版本"),
                    buildVersion("R001", 2, "调整阈值")
            ));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.getVersionHistory()).hasSize(2);
            assertThat(doc.getVersionHistory().get(0).getVersion()).isEqualTo(1);
            assertThat(doc.getVersionHistory().get(0).getChangeDesc()).isEqualTo("初始版本");
            assertThat(doc.getVersionHistory().get(1).getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("边界场景：versionRepository 为 null 时不填充版本历史")
        void shouldHandleNullVersionRepo() {
            RuleDocumentationService service = new RuleDocumentationService(
                    configProvider, ruleEngine, null);
            service.setEffectivenessService(effectivenessService);
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = service.generateDocumentation("R001", "system");

            assertThat(doc.getVersionHistory()).isEmpty();
        }

        @Test
        @DisplayName("正常场景：识别同分类关联规则")
        void shouldIdentifyRelatedRulesByCategory() {
            RuleDefinition rule = buildFullRule("R001");
            RuleDefinition related = RuleDefinition.builder()
                    .code("R002").name("规则-R002").category("finance").build();
            RuleDefinition unrelated = RuleDefinition.builder()
                    .code("R003").name("规则-R003").category("hr").build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule, related, unrelated));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.getRelatedRules()).hasSize(1);
            assertThat(doc.getRelatedRules().get(0).getRuleCode()).isEqualTo("R002");
            assertThat(doc.getRelatedRules().get(0).getRelationType()).contains("同分类");
        }

        @Test
        @DisplayName("正常场景：识别同互斥组关联规则")
        void shouldIdentifyRelatedRulesByMutexGroup() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001").name("规则-R001").category("finance").mutexGroup("G1").build();
            RuleDefinition related = RuleDefinition.builder()
                    .code("R002").name("规则-R002").category("hr").mutexGroup("G1").build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule, related));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.getRelatedRules()).hasSize(1);
            assertThat(doc.getRelatedRules().get(0).getRelationType()).contains("同互斥组");
        }

        @Test
        @DisplayName("正常场景：同分类且同互斥组时关联类型合并显示")
        void shouldCombineRelationType() {
            RuleDefinition rule = buildFullRule("R001");
            RuleDefinition related = RuleDefinition.builder()
                    .code("R002").name("规则-R002").category("finance").mutexGroup("G1").build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule, related));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.getRelatedRules()).hasSize(1);
            assertThat(doc.getRelatedRules().get(0).getRelationType()).contains("同分类");
            assertThat(doc.getRelatedRules().get(0).getRelationType()).contains("同互斥组");
        }

        @Test
        @DisplayName("边界场景：effectivenessService 未设置时 hasEffectivenessMetrics 为 false")
        void shouldHandleNoEffectivenessService() {
            RuleDocumentationService service = new RuleDocumentationService(
                    configProvider, ruleEngine, versionRepository);
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            RuleDocumentation doc = service.generateDocumentation("R001", "system");

            assertThat(doc.isHasEffectivenessMetrics()).isFalse();
        }

        @Test
        @DisplayName("正常场景：条件表达式为空时说明为空字符串")
        void shouldHandleBlankCondition() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001").name("规则-R001").conditionExpression("").build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            assertThat(doc.getConditionExplanation()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Markdown 输出：generateMarkdown")
    class GenerateMarkdownTest {

        @Test
        @DisplayName("边界场景：规则不存在返回 null")
        void shouldReturnNullWhenRuleNotFound() {
            when(configProvider.findByCode(anyString())).thenReturn(null);

            String markdown = documentationService.generateMarkdown("R_NOT_EXIST", "system");

            assertThat(markdown).isNull();
        }

        @Test
        @DisplayName("正常场景：生成包含标题和基础信息的 Markdown")
        void shouldGenerateMarkdown() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            String markdown = documentationService.generateMarkdown("R001", "system");

            assertThat(markdown).isNotNull();
            assertThat(markdown).startsWith("# 规则文档：");
            assertThat(markdown).contains("## 基础信息");
            assertThat(markdown).contains("## 规则配置");
            assertThat(markdown).contains("## 生命周期");
            assertThat(markdown).contains("R001");
            assertThat(markdown).contains("规则-R001");
        }

        @Test
        @DisplayName("正常场景：包含执行统计的 Markdown")
        void shouldGenerateMarkdownWithStats() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R001", buildStat(1000, 200, 10));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            String markdown = documentationService.generateMarkdown("R001", "system");

            assertThat(markdown).contains("## 执行统计");
            assertThat(markdown).contains("总评估次数");
        }
    }

    @Nested
    @DisplayName("HTML 输出：generateHtml")
    class GenerateHtmlTest {

        @Test
        @DisplayName("边界场景：规则不存在返回 null")
        void shouldReturnNullWhenRuleNotFound() {
            when(configProvider.findByCode(anyString())).thenReturn(null);

            String html = documentationService.generateHtml("R_NOT_EXIST", "system");

            assertThat(html).isNull();
        }

        @Test
        @DisplayName("正常场景：生成包含完整结构的 HTML")
        void shouldGenerateHtml() {
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            String html = documentationService.generateHtml("R001", "system");

            assertThat(html).isNotNull();
            assertThat(html).startsWith("<!DOCTYPE html>");
            assertThat(html).contains("<html");
            assertThat(html).contains("<title>规则文档：");
            assertThat(html).contains("<h1>规则文档：");
            assertThat(html).contains("<h2>基础信息</h2>");
            assertThat(html).contains("<h2>规则配置</h2>");
            assertThat(html).contains("</html>");
        }

        @Test
        @DisplayName("正常场景：HTML 特殊字符被转义")
        void shouldEscapeHtml() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .name("规则<script>alert(1)</script>")
                    .conditionExpression("a > b && c < d")
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());
            when(effectivenessService.getMetrics("R001"))
                    .thenReturn(RuleEffectivenessMetrics.empty("R001"));

            String html = documentationService.generateHtml("R001", "system");

            assertThat(html).contains("&lt;script&gt;");
            assertThat(html).doesNotContain("<script>alert(1)</script>");
        }
    }

    @Nested
    @DisplayName("文档目录：generateIndex")
    class GenerateIndexTest {

        @Test
        @DisplayName("边界场景：规则列表为空时返回提示")
        void shouldReturnEmptyIndexWhenNoRules() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            String index = documentationService.generateIndex("system");

            assertThat(index).contains("暂无规则");
            assertThat(index).startsWith("# 规则文档目录");
        }

        @Test
        @DisplayName("边界场景：规则列表为 null 时返回提示")
        void shouldReturnEmptyIndexWhenNull() {
            when(configProvider.loadAllRules()).thenReturn(null);

            String index = documentationService.generateIndex("system");

            assertThat(index).contains("暂无规则");
        }

        @Test
        @DisplayName("正常场景：生成包含规则列表的目录")
        void shouldGenerateIndex() {
            RuleDefinition r1 = buildFullRule("R001");
            RuleDefinition r2 = RuleDefinition.builder()
                    .code("R002").name("规则-R002").category("hr")
                    .status("DRAFT").version(1).enabled(false).build();
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));

            String index = documentationService.generateIndex("system");

            assertThat(index).startsWith("# 规则文档目录");
            assertThat(index).contains("共 2 条规则");
            assertThat(index).contains("R001");
            assertThat(index).contains("R002");
            assertThat(index).contains("规则-R001");
            assertThat(index).contains("system");
        }
    }

    @Nested
    @DisplayName("效果服务设置：setEffectivenessService")
    class SetEffectivenessServiceTest {

        @Test
        @DisplayName("正常场景：可设置并替换效果评估服务")
        void shouldSetEffectivenessService() {
            RuleEffectivenessService newService = new RuleEffectivenessService();
            documentationService.setEffectivenessService(newService);

            // 通过行为验证已设置：规则存在但无效果数据
            RuleDefinition rule = buildFullRule("R001");
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            RuleDocumentation doc = documentationService.generateDocumentation("R001", "system");

            // 新服务无数据，hasEffectivenessMetrics 应为 false
            assertThat(doc.isHasEffectivenessMetrics()).isFalse();
        }
    }
}
